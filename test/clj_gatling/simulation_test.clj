(ns clj-gatling.simulation-test
  (:use clojure.test)
  (:require [clj-gatling.simulation :as simulation]
            [clj-gatling.httpkit :as httpkit]
            [clj-gatling.simulation-util :refer [choose-runner
                                                 weighted-scenarios]]
            [clj-containment-matchers.clojure-test :refer :all]
            [clj-async-test.core :refer :all]
            [clojure.core.async :refer [<!!]]
            [clj-time.core :as time]))

(defn- to-vector [channel]
  (loop [results []]
    (if-let [result (<!! channel)]
      (recur (conj results result))
      results)))

(defn- run-legacy-simulation [scenarios concurrency & [options]]
  (let [step-timeout (or (:timeout-in-ms options) 5000)]
    (-> (simulation/run-scenarios {:runner (choose-runner scenarios concurrency options)
                                   :timeout-in-ms step-timeout
                                   :context (:context options)}
                                  (weighted-scenarios (range concurrency) scenarios))
        to-vector)))

(defn- run-single-scenario [scenario concurrency]
  (to-vector (simulation/run {:name "Simulation"
                              :scenarios [scenario]}
                             {:concurrency concurrency
                              :timeout-in-ms 5000})))

(def request-count (atom 0))

(defn counting-request [cb context]
  (do
    (swap! request-count inc)
    (cb true)))

(defn successful-request [cb context]
  ;TODO Try to find a better way for this
  ;This is required so that multiple scenarios start roughly at the same time
  (Thread/sleep 50)
  (cb true (assoc context :to-next-request true)))

(defn read-return-value-from-context-request [cb context]
  (cb (:to-next-request context) context))

(defn slow-request [sleep-time cb context]
  (future (Thread/sleep sleep-time)
          (cb true)))

(defn failing-request [cb context] (cb false))

(defn- error-throwing-request [cb context] (throw (Exception. "Simulated")))

(defn- fake-async-http [url callback context]
  (future (Thread/sleep 50)
          (callback (= "success" url))))

(def scenario
  {:name "Test scenario"
   :requests [{:name "Request1" :fn successful-request}
              {:name "Request2" :fn failing-request}]})

(def context-testing-scenario
  {:name "Context testing scenario"
   :requests [{:name "Request1" :fn successful-request}
              {:name "Request2" :fn read-return-value-from-context-request}]})

(def counting-scenario
  {:name "Counting scenario"
   :requests [{:name "Request1" :fn counting-request}]})

(def scenario2
  {:name "Test scenario2"
   :requests [{:name "Request1" :fn (partial slow-request 50)}
              {:name "Request2" :fn failing-request}]})

(def timeout-scenario
  {:name "Timeout scenario"
   :requests [{:name "Request1" :fn (partial slow-request 2000)}]})

(def http-scenario
  {:name "Test http scenario"
   :requests [{:name "Request1" :http "success"}
              {:name "Request2" :http "fail"}]})

(def first-fails-scenario {:name "Scenario"
                           :requests [{:name "first" :fn failing-request}
                                      {:name "second" :fn successful-request}]})

(defn get-result [requests request-name]
  (:result (first (filter #(= request-name (:name %)) requests))))

(deftest simulation-returns-result-when-run-with-one-user-with-legacy-format
  (let [result (run-legacy-simulation [scenario] 1)]
    (is (equal? result [{:name "Test scenario"
                         :id 0
                         :start number?
                         :end number?
                         :requests [{:name "Request1"
                                     :id 0
                                     :start number?
                                     :end number?
                                     :result true}
                                    {:name "Request2"
                                     :id 0
                                     :start number?
                                     :end number?
                                     :result false}]}]))))

(deftest simulation-returns-result-when-run-with-one-user
  (let [result (run-single-scenario {:name "Test scenario"
                                     :steps [{:name "Step1"
                                              :request (fn [ctx] [true ctx])}
                                             {:name "Step2"
                                              :request (fn [ctx] [false ctx])}]}
                                    1)]
    (is (equal? result [{:name "Test scenario"
                         :id 0
                         :start number?
                         :end number?
                         :requests [{:name "Step1"
                                     :id 0
                                     :start number?
                                     :end number?
                                     :result true}
                                    {:name "Step2"
                                     :id 0
                                     :start number?
                                     :end number?
                                     :result false}]}]))))

(deftest when-function-returns-exception-it-is-handled-as-ko
  (let [s {:name "Exception scenario"
           :requests [{:name "Throwing" :fn error-throwing-request}]}
        result (run-legacy-simulation [s] 1)]
    (is (equal? result [{:name "Exception scenario"
                         :id 0
                         :start number?
                         :end number?
                         :requests [{:name "Throwing"
                                     :id 0
                                     :start number?
                                     :end number?
                                     :result false}]}]))))

(deftest simulation-passes-context-through-requests-in-scenario
  (let [result (first (run-legacy-simulation [context-testing-scenario] 1))]
    (is (= true (get-result (:requests result) "Request2")))))

(deftest simulation-passes-original-context-to-first-request
  (let [scenario {:name "scenario"
                  :requests [{:name "Request1"
                              :fn (fn [cb {:keys [test-val]}]
                                    (cb (= 5 test-val)))}]}
        result (first (run-legacy-simulation [scenario]
                                      1
                                      {:context {:test-val 5}}))]
    (is (= true (get-result (:requests result) "Request1")))))

(deftest simulation-skips-second-request-if-first-fails
  (let [result (run-legacy-simulation [first-fails-scenario] 1)]
    ;Note scenario is ran twice in this case to match number of handled requests which should be 2
    (is (equal? result
                (repeat 2 {:name "Scenario"
                           :id 0
                           :start number?
                           :end number?
                           :requests [{:name "first"
                                       :id 0
                                       :start number?
                                       :end number?
                                       :result false}]})))))

(deftest second-request-is-not-skipped-in-failure-if-skip-next-after-failure-is-unset
  (let [result (run-legacy-simulation [(assoc first-fails-scenario :skip-next-after-failure? false)] 1)]
    (is (equal? result
                [{:name "Scenario"
                           :id 0
                           :start number?
                           :end number?
                           :requests [{:name "first"
                                       :id 0
                                       :start number?
                                       :end number?
                                       :result false}
                                      {:name "second"
                                       :id 0
                                       :start number?
                                       :end number?
                                       :result true}]}]))))

(deftest simulation-returns-result-when-run-with-http-requests
  (with-redefs [httpkit/async-http-request fake-async-http]
    (let [result (first (run-legacy-simulation [http-scenario] 1))]
      (is (= "Test http scenario" (:name result)))
      (is (= true (get-result (:requests result) "Request1")))
      (is (= false (get-result (:requests result) "Request2"))))))

(deftest simulation-returns-result-when-run-with-multiple-scenarios-with-one-user
  (let [result (run-legacy-simulation [scenario scenario2] 2)]
    ;Stop condition is not synced between parallel scenarios
    ;so once in a while there might be one extra scenario
    ;This is ok tolerance for max requests
    (is (equal? (sort-by :id (take 2 result)) [{:name "Test scenario"
                                                :id 0
                                                :start number?
                                                :end number?
                                                :requests anything}
                                               {:name "Test scenario2"
                                                :id 1
                                                :start number?
                                                :end number?
                                                :requests anything}]))))

(deftest throws-exception-when-concurrency-is-smaller-than-number-of-parallel-scenarios
  (is (thrown? AssertionError (run-legacy-simulation [scenario scenario2] 1))))

(deftest with-given-number-of-requests
  (let [result (run-legacy-simulation [scenario] 1 {:requests 4})]
    (is (= "Test scenario" (-> result first :name)))
    (is (= 2 (-> result first :requests count)))
    (is (= "Test scenario" (-> result second :name)))
    (is (= 2 (-> result second :requests count)))))

(deftest with-multiple-number-of-requests
  (reset! request-count 0)
  (let [result (run-legacy-simulation [counting-scenario] 100 {:requests 2000})
        handled-requests (->> result (map :requests) count)]
    (is (approximately== handled-requests 2000))
    (is (= handled-requests @request-count))))

(deftest duration-given
  (let [result (run-legacy-simulation [scenario] 1 {:duration (time/millis 50)})]
    (is (not (empty? result)))))

(deftest fails-requests-when-they-take-longer-than-timeout
  (let [result (first (run-legacy-simulation [timeout-scenario] 1 {:timeout-in-ms 100}))]
    (is (= false (get-result (:requests result) "Request1")))))

(deftest scenario-weight
  (let [request1 {:name "Request1" :fn successful-request}
        request2 {:name "Request2" :fn successful-request}
        main-scenario {:name "Main" :weight 2 :requests [request1 request2]}
        second-scenario {:name "Second" :weight 1 :requests [request1]}
        result (group-by :name (run-legacy-simulation
                                 [main-scenario second-scenario]
                                 10
                                 {:requests 100}))
        count-requests (fn [name] (reduce + (map #(count (:requests %)) (get result name))))]
    ;TODO Add some check that 66% are for Main and 33% for Second
    (is (= 100 (+ (count-requests "Main") (count-requests "Second"))))))

(ns clj-gatling.simulation-test
  (:use clojure.test)
  (:require [clj-gatling.simulation :as simulation]
            [clj-gatling.httpkit :as httpkit]
            [clj-containment-matchers.clojure-test :refer :all]
            [clj-time.core :as time]))

(def request-count (atom 0))

(defn counting-request [cb context]
  (do
    (swap! request-count inc)
    (cb true)))

(defn successful-request [cb context]
  (cb true (assoc context :to-next-request true)))

(defn read-return-value-from-context-request [cb context]
  (cb (:to-next-request context) context))

(defn slow-request [sleep-time cb context]
  (future (Thread/sleep sleep-time)
          (cb true)))

(defn failing-request [cb context] (cb false))

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

(defn get-result [requests request-name]
  (:result (first (filter #(= request-name (:name %)) requests))))

(deftest simulation-returns-result-when-run-with-one-user
  (let [result (simulation/run-simulation [scenario] 1)]
    (is (equal? result [{:name "Test scenario"
                         :id 0
                         :start anything
                         :end anything
                         :requests [{:name "Request1"
                                     :id 0
                                     :start anything
                                     :end anything
                                     :result true}
                                    {:name "Request2"
                                     :id 0
                                     :start anything
                                     :end anything
                                     :result false}]}]))))

(deftest simulation-passes-context-through-requests-in-scenario
  (let [result (first (simulation/run-simulation [context-testing-scenario] 1))]
    (is (= true (get-result (:requests result) "Request2")))))

(deftest simulation-returns-result-when-run-with-http-requests
  (with-redefs [httpkit/async-http-request fake-async-http]
    (let [result (first (simulation/run-simulation [http-scenario] 1))]
      (is (= "Test http scenario" (:name result)))
      (is (= true (get-result (:requests result) "Request1")))
      (is (= false (get-result (:requests result) "Request2"))))))

(deftest simulation-returns-result-when-run-with-multiple-scenarios-and-one-user
  (let [result (simulation/run-simulation [scenario scenario2] 1)]
    (is (equal? result [{:name "Test scenario"
                         :id 0
                         :start anything
                         :end anything
                         :requests anything}
                        {:name "Test scenario2"
                         :id 0
                         :start anything
                         :end anything
                         :requests anything}]))))

(deftest with-given-number-of-requests
  (let [result (simulation/run-simulation [scenario] 1 {:requests 4})]
    (is (= "Test scenario" (-> result first :name)))
    (is (= 2 (-> result first :requests count)))
    (is (= "Test scenario" (-> result second :name)))
    (is (= 2 (-> result second :requests count)))))

(deftest with-multiple-number-of-requests
  (reset! request-count 0)
  (let [result (simulation/run-simulation [counting-scenario] 100 {:requests 2000})]
    (is (= 2000 (->> result (map :requests) count))
    (is (= 2000 @request-count)))))

(deftest duration-given
  (let [result (simulation/run-simulation [scenario] 1 {:duration (time/millis 50)})]
    (is (not (empty? result)))))

(deftest fails-requests-when-they-take-longer-than-timeout
  (let [result (first (simulation/run-simulation [timeout-scenario] 1 {:timeout-in-ms 100}))]
    (is (= false (get-result (:requests result) "Request1")))))

(deftest scenario-weight
  (let [request1 {:name "Request1" :fn successful-request}
        request2 {:name "Request2" :fn successful-request}
        main-scenario {:name "Main" :weight 2 :requests [request1 request2]}
        second-scenario {:name "Second" :weight 1 :requests [request1]}
        result (group-by :name (simulation/run-simulation
                                 [main-scenario second-scenario]
                                 10
                                 {:requests 100}))
        count-requests (fn [name] (reduce + (map #(count (:requests %)) (get result name))))]
    (is (= 68 (count-requests "Main")))
    (is (= 33 (count-requests "Second")))))

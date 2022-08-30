(ns clj-gatling.simulation-test
  (:require [clojure.test :refer :all]
            [clj-gatling.test-helpers :refer :all]
            [clj-gatling.httpkit :as httpkit]
            [clj-containment-matchers.clojure-test :refer :all]
            [clj-async-test.core :refer :all]
            [clojure.core.async :refer [go <! timeout]]
            [clj-time.core :as time])
  (:import (java.time Duration)))

(use-fixtures :once setup-error-file-path)

(def scenario
  {:name "Test scenario"
   :context {}
   :requests [{:name "Request1" :fn successful-request}
              {:name "Request2" :fn failing-request}]})

(def http-scenario
  {:name "Test http scenario"
   :requests [{:name "Request1" :http "success"}
              {:name "Request2" :http "fail"}]})

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
                                     :context-after map?
                                     :context-before map?
                                     :result true}
                                    {:name "Request2"
                                     :id 0
                                     :start number?
                                     :end number?
                                     :context-before map?
                                     :context-after map?
                                     :result false}]}]))))

(deftest simulation-returns-result-when-run-with-one-user
  (let [result (run-single-scenario {:name "Test scenario"
                                     :steps [(step "Step1" true)
                                             (step "Step2" false)]}
                                    :concurrency 1
                                    :users [0])]
    (is (equal? result [{:name "Test scenario"
                         :id 0
                         :start number?
                         :end number?
                         :requests [{:name "Step1"
                                     :id 0
                                     :start number?
                                     :end number?
                                     :context-after map?
                                     :context-before map?
                                     :result true}
                                    {:name "Step2"
                                     :id 0
                                     :start number?
                                     :end number?
                                     :context-before map?
                                     :context-after map?
                                     :result false}]}]))))

(deftest simulation-uses-given-user-ids
  (let [result (run-single-scenario {:name "Test scenario"
                                     :steps [(step "Step" true)]}
                                    :concurrency 2
                                    :requests 100
                                    :users [1 3])]
    (is (empty? (filter #(= 0 (:id %)) result)))
    (is (empty? (filter #(= 2 (:id %)) result)))
    (is (equal? (first (filter #(= 1 (:id %)) result)) {:name "Test scenario"
                                                        :id 1
                                                        :start number?
                                                        :end number?
                                                        :requests [{:name "Step"
                                                                    :id 1
                                                                    :start number?
                                                                    :end number?
                                                                    :context-before map?
                                                                    :context-after map?
                                                                    :result true}]}))
    (is (equal? (first (filter #(= 3 (:id %)) result))  {:name "Test scenario"
                                                         :id 3
                                                         :start number?
                                                         :end number?
                                                         :requests [{:name "Step"
                                                                     :id 3
                                                                     :start number?
                                                                     :end number?
                                                                     :context-before map?
                                                                     :context-after map?
                                                                     :result true}]}))))

(deftest simulation-with-request-returning-single-boolean-instead-of-tuple
  (let [results (run-single-scenario {:name "Test scenario"
                                      :steps [{:name "step"
                                               :request (fn [_] true)}]}
                                     :concurrency 1)]
    (doseq [result results]
      (is (equal? result {:name "Test scenario"
                          :id number?
                          :start number?
                          :end number?
                          :requests [{:name "step"
                                      :id number?
                                      :start number?
                                      :end number?
                                      :context-before map?
                                      :context-after map?
                                      :result true}]})))))

(deftest simulation-with-request-returning-channel-with-boolean
  (let [results (run-single-scenario {:name "Test scenario"
                                      :steps [{:name "step"
                                               :request (fn [_] (go true))}]}
                                     :concurrency 1)]
    (doseq [result results]
      (is (equal? result {:name "Test scenario"
                          :id number?
                          :start number?
                          :end number?
                          :requests [{:name "step"
                                      :id number?
                                      :start number?
                                      :end number?
                                      :context-before map?
                                      :context-after map?
                                      :result true}]})))))

(deftest when-function-throws-exception-it-is-handled-as-ko
  (let [s {:name "Exception scenario"
           :steps [{:name "Throwing" :request (fn [_] (throw (Exception. "Simulated")))}]}
        results (run-single-scenario s :concurrency 1)]
    (doseq [result results]
      (is (equal? result {:name "Exception scenario"
                          :id number?
                          :start number?
                          :end number?
                          :requests [{:name "Throwing"
                                      :id number?
                                      :start number?
                                      :end number?
                                      :context-before map?
                                      :context-after map?
                                      :result false
                                      :exception "Simulated"}]})))))

(deftest when-function-returns-exception-it-is-handled-as-ko
  (let [s {:name "Exception scenario"
           :steps [{:name "Throwing" :request (fn [_] (Exception. "Simulated"))}]}
        results (run-single-scenario s :concurrency 1)]
    (doseq [result results]
      (is (equal? result {:name "Exception scenario"
                          :id number?
                          :start number?
                          :end number?
                          :requests [{:name "Throwing"
                                      :id number?
                                      :start number?
                                      :end number?
                                      :context-before map?
                                      :context-after map?
                                      :result false
                                      :exception "Simulated"}]})))))

(deftest when-function-throws-exception-it-is-logged
  (delete-error-logs)
  (let [_ (-> {:name "Exception logging scenario"
               :steps [{:name "Throwing" :request (fn [_] (throw (Exception. "Simulated")))}]}
              (run-single-scenario :concurrency 1))]
    (is (-> (slurp error-file-path)
            (clojure.string/includes? "Simulated")))))

(deftest simulation-passes-context-through-requests-in-scenario
  (let [results (run-single-scenario {:name "scenario"
                                      :steps [(step "step1" true)
                                              {:name "step2"
                                               :request (fn [{:keys [to-next-request] :as ctx}]
                                                          [to-next-request ctx])}]}
                                     :concurrency 1)]
    (doseq [result results]
      (is (equal? result {:name "scenario"
                          :id number?
                          :start number?
                          :end number?
                          :requests [{:name "step1"
                                      :id number?
                                      :start number?
                                      :end number?
                                      :context-before map?
                                      :context-after {:user-id number?
                                                      :to-next-request true}
                                      :result true}
                                     {:name "step2"
                                      :id number?
                                      :start number?
                                      :end number?
                                      :context-before {:user-id number?
                                                       :to-next-request true}
                                      :context-after map?
                                      :result true}]})))))

(deftest simulation-passes-original-context-to-first-request
  (let [scenario {:name "scenario"
                  :steps [{:name "step1"
                           :request (fn [{:keys [test-val] :as ctx}]
                                      [(= 5 test-val) ctx])}]}
        results (run-single-scenario scenario :concurrency 1
                                     :context {:test-val 5})]
    (doseq [result results]
      (is (equal? result {:name "scenario"
                          :id number?
                          :start number?
                          :end number?
                          :requests [{:name "step1"
                                      :id number?
                                      :start number?
                                      :end number?
                                      :context-before {:test-val 5
                                                       :user-id number?}
                                      :context-after {:test-val 5
                                                      :user-id number?}
                                      :result true}]})))))

(deftest simulation-passes-scenario-specific-context
  (let [scenario {:name "scenario"
                  :context {:test-val 5}
                  :steps [{:name "step1"
                           :request (fn [{:keys [test-val] :as ctx}]
                                      [(= 5 test-val) ctx])}]}
        results (run-single-scenario scenario :concurrency 1)]
    (doseq [result results]
      (is (equal? result {:name "scenario"
                          :id number?
                          :start number?
                          :end number?
                          :requests [{:name "step1"
                                      :id number?
                                      :start number?
                                      :end number?
                                      :context-before {:test-val 5
                                                       :user-id number?}
                                      :context-after {:test-val 5
                                                      :user-id number?}
                                      :result true}]})))))

(deftest does-not-stop-simulation-in-middle-of-scenario-by-default
  (let [scenario {:name "scenario"
                  :steps [{:name "step 1"
                           :request (fn [_]
                                      (Thread/sleep 500)
                                      true)}
                          {:name "step 2"
                           :request (fn [ctx]
                                      (Thread/sleep 2000)
                                      true)}]}
        results (run-single-scenario scenario
                                     :concurrency 1
                                     :duration (Duration/ofMillis 100))]
    (doseq [result results]
      (is (equal? result {:name "scenario"
                          :id number?
                          :start number?
                          :end number?
                          :requests [{:name "step 1"
                                      :id number?
                                      :start number?
                                      :end number?
                                      :context-before map?
                                      :context-after map?
                                      :result true}
                                     {:name "step 2"
                                      :id number?
                                      :start number?
                                      :end number?
                                      :context-before map?
                                      :context-after map?
                                      :result true}]})))))

(deftest deprecated-joda-time-duration-can-be-given
  (let [scenario {:name "scenario"
                  :steps [{:name "step 1"
                           :request (fn [_]
                                      (Thread/sleep 500)
                                      true)}
                          {:name "step 2"
                           :request (fn [ctx]
                                      (Thread/sleep 2000)
                                      true)}]}
        results (run-single-scenario scenario
                                     :concurrency 1
                                     :duration (time/millis 100))]
    (doseq [result results]
      (is (equal? result {:name "scenario"
                          :id number?
                          :start number?
                          :end number?
                          :requests [{:name "step 1"
                                      :id number?
                                      :start number?
                                      :end number?
                                      :context-before map?
                                      :context-after map?
                                      :result true}
                                     {:name "step 2"
                                      :id number?
                                      :start number?
                                      :end number?
                                      :context-before map?
                                      :context-after map?
                                      :result true}]})))))

(deftest stops-simulation-in-middle-of-scenario-when-enabled
  (let [scenario {:name "scenario"
                  :allow-early-termination? true
                  :steps [{:name "step 1"
                           :request (fn [_]
                                      (Thread/sleep 500)
                                      true)}
                          {:name "step 2"
                           :request (fn [ctx]
                                      (Thread/sleep 2000)
                                      true)}]}
        results (run-single-scenario scenario
                                     :concurrency 1
                                     :allow-early-termination? true
                                     :duration (Duration/ofMillis 100))]
    (is (equal? (last results) {:name "scenario"
                                :id number?
                                :start number?
                                :end number?
                                :requests [{:name "step 1"
                                            :id number?
                                            :start number?
                                            :end number?
                                            :context-before map?
                                            :context-after map?
                                            :result true}]}))))

(deftest sleeps-for-given-time-before-starting-request
  (let [request-started (promise)
        scenario {:name "scenario"
                  :steps [{:name "step"
                           :sleep-before (fn [_] 500)
                           :request (fn [_]
                                      (deliver request-started true)
                                      true)}]}
        results (future (run-single-scenario scenario :concurrency 1))]
    (Thread/sleep 200)
    (is (not (realized? request-started)))
    (Thread/sleep 400)
    (is (realized? request-started))
    (is @request-started)
    (doseq [result @results]
      (is (equal? result {:name "scenario"
                          :id number?
                          :start number?
                          :end number?
                          :requests [{:name "step"
                                      :id number?
                                      :start number?
                                      :end number?
                                      :context-before map?
                                      :context-after map?
                                      :result true}]})))))

(def first-fails-scenario
  {:name "Scenario"
   :steps [(step "first" false)
           (step "second" true)]})

(deftest simulation-skips-second-request-if-first-fails
  (let [results (run-single-scenario first-fails-scenario :concurrency 1)]
    (doseq [result results]
      (is (equal? result {:name "Scenario"
                          :id number?
                          :start number?
                          :end number?
                          :requests [{:name "first"
                                      :id number?
                                      :start number?
                                      :end number?
                                      :context-before map?
                                      :context-after map?
                                      :result false}]})))))

(deftest second-request-is-not-skipped-in-failure-if-skip-next-after-failure-is-unset
  (let [results (run-single-scenario (assoc first-fails-scenario :skip-next-after-failure? false) :concurrency 1)]
    (doseq [result results]
      (is (equal? result {:name "Scenario"
                                   :id number?
                                   :start number?
                                   :end number?
                                   :requests [{:name "first"
                                               :id number?
                                               :start number?
                                               :end number?
                                               :context-before map?
                                               :context-after map?
                                               :result false}
                                              {:name "second"
                                               :id number?
                                               :start number?
                                               :end number?
                                               :context-before map?
                                               :context-after map?
                                               :result true}]})))))

(deftest simulation-returns-result-when-run-with-http-requests-using-legacy-format
  (with-redefs [httpkit/async-http-request fake-async-http]
    (let [result (first (run-legacy-simulation [http-scenario] 1))]
      (is (= "Test http scenario" (:name result)))
      (is (= true (get-result (:requests result) "Request1")))
      (is (= false (get-result (:requests result) "Request2"))))))

(deftest throws-exception-when-concurrency-is-smaller-than-number-of-parallel-scenarios
  (let [scenario1 {:name "scenario1" :steps [(step "step" true)]}
        scenario2 (assoc scenario1 :name "scenario2")]
    (is (thrown? AssertionError (run-two-scenarios scenario1 scenario2 :concurrency 1 :users [0])))))

(deftest with-given-number-of-requests
  (let [results (run-single-scenario {:name "scenario"
                                      :steps [(step "step" true)]} :concurrency 1 :users [0] :requests 2)]
    (is (= 2 (count results)))
    (doseq [result results]
      (is (equal? result {:name "scenario"
                          :start number?
                          :end number?
                          :id number?
                          :requests [{:name "step"
                                      :id number?
                                      :start number?
                                      :end number?
                                      :context-before map?
                                      :context-after map?
                                      :result true}]})))))

(deftest with-multiple-number-of-requests
  (let [request-count (atom 0)
        results (run-single-scenario {:name "scenario"
                                      :steps [{:name "step"
                                               :request (fn [ctx]
                                                          (swap! request-count inc)
                                                          [true ctx])}]}
                                     :concurrency 100
                                     :requests 2000)
        handled-requests (->> results (map :requests) count)]
    (is (approximately== handled-requests 2000 :accuracy 5))
    (is (= handled-requests @request-count))))

(deftest duration-given
  (let [results (run-single-scenario {:name "scenario"
                                      :steps [(step "step" true)]}
                                     :concurrency 1
                                     :duration (time/millis 50))]
    (is (not (empty? results)))))

(deftest with-simulation-hooks
  (let [pre-hook-called? (atom false)
        post-hook-called? (atom false)
        ctx-in-post-hook (atom {})]
    (run-single-scenario {:name "scenario"
                          :steps [(step "step" true)]}
                         :concurrency 1
                         :context {:value1 1}
                         :pre-hook (fn [_]
                                     (reset! pre-hook-called? true)
                                     {:value2 2})
                         :post-hook (fn [ctx]
                                      (reset! post-hook-called? true)
                                      (reset! ctx-in-post-hook ctx)))
    (testing "pre-hook function is called"
      (is (= true @pre-hook-called?)))
    (testing "post-hook function is called"
      (is (= true @post-hook-called?))
      (is (= {:value1 1 :value2 2} @ctx-in-post-hook)))))

(deftest with-scenario-hooks
  (let [pre-hook-called? (atom false)
        post-hook-called? (atom false)
        ctx-in-post-hook (atom {})]
    (run-single-scenario {:name "scenario"
                          :pre-hook (fn [{:keys [value]}]
                                     (reset! pre-hook-called? true)
                                     {:value (inc value)})
                          :post-hook (fn [ctx]
                                       (reset! ctx-in-post-hook ctx)
                                       (reset! post-hook-called? true))
                          :steps [(throwing-step "step")]}
                         :concurrency 1
                         :context {:value 1})
    (testing "pre-hook function is called"
      (is (= true @pre-hook-called?)))
    (testing "post-hook function is called"
      (is (= true @post-hook-called?))
      (is (= {:value 2} @ctx-in-post-hook)))))

(deftest fails-requests-when-they-take-longer-than-timeout
  (let [results (run-single-scenario {:name "scenario"
                                      :steps [{:name "step"
                                               :request (fn [ctx]
                                                          (go
                                                            (<! (timeout 500))
                                                            [true ctx]))}]}
                                     :concurrency 1
                                     :timeout-in-ms 100)]
    (doseq [result results]
      (is (equal? result {:name "scenario"
                          :start number?
                          :end number?
                          :id number?
                          :requests [{:name "step"
                                      :id number?
                                      :start number?
                                      :end number?
                                      :context-before map?
                                      :context-after map?
                                      :result false
                                      :exception "clj-gatling: request timed out"}]})))))

(defn- is-approximately-sorted? [xs & {:keys [accuracy] :or {accuracy 25}}]
  ;; Original vector might contain small inconsistencies
  ;; It could for example look like this [1 0 3 4 5 6]
  ;; This should be counted as a sorted list even though there is
  ;; an outlier, so we work out the % of non-matches and compare that
  (let [sorted-xs (sort xs)
        no-match  (->> (map #(= %1 %2) xs sorted-xs)
                       (filter false?))]
    (is (>= accuracy (->> (count xs) (/ (count no-match)) (* 100))) "vector should be sorted")))

(deftest with-2-arity-concurrency-function
  (let [concurrency-function-called? (atom false)
        context-to-fn (atom {})
        progress-distribution (atom [])]
    (run-single-scenario {:name "scenario"
                          :steps [(step "step" true)]}
                         :concurrency 10
                         :requests 100
                         :context {:value 1}
                         :concurrency-distribution (fn [progress context]
                                                     (reset! context-to-fn context)
                                                     (reset! concurrency-function-called? true)
                                                     (swap! progress-distribution conj progress)
                                                     (if (< progress 0.5)
                                                       0.1
                                                       1.0)))
    (testing "concurrency-function is called"
      (is (= true @concurrency-function-called?)))
    (testing "context is passed to concurrency-function"
      (is (= {:value 1} @context-to-fn)))
    (testing "Progress goes from 0 to 1"
      (is (every? #(and (>= % 0.0) (<= % 1.0)) @progress-distribution))
      (is #{0.1} @progress-distribution)
      (is #{1.0} @progress-distribution)
      (is-approximately-sorted? @progress-distribution))))

(deftest with-1-arity-concurrency-function
  (let [concurrency-function-called? (atom false)
        context-to-fn (atom {})
        duration-distribution (atom [])]
    (run-single-scenario {:name "scenario"
                          :steps [(step "step" true)]}
                         :concurrency 10
                         :requests 100
                         :context {:value 1}
                         :concurrency-distribution (fn [{:keys [progress duration context]}]
                                                     (reset! context-to-fn context)
                                                     (reset! concurrency-function-called? true)
                                                     (swap! duration-distribution conj (.toMillis duration))
                                                     (if (< progress 0.5)
                                                       0.1
                                                       1.0)))
    (testing "concurrency-function is called"
      (is (= true @concurrency-function-called?)))
    (testing "context is passed to concurrency-function"
      (is (= {:value 1} @context-to-fn)))
    (testing "Duration has ordered values"
      (is (> (count @duration-distribution) 10))
      (is-approximately-sorted? @duration-distribution))))

(deftest with-2-arity-rate-function
  (let [rate-function-called? (atom false)
        context-to-fn (atom {})
        progress-distribution (atom [])]
    (run-single-scenario {:name "scenario"
                          :steps [(step "step" true)]}
                         :rate 100
                         :users (range 10)
                         :requests 100
                         :context {:value 1}
                         :rate-distribution (fn [progress context]
                                              (reset! context-to-fn context)
                                              (reset! rate-function-called? true)
                                              (swap! progress-distribution conj progress)
                                              (if (< progress 0.2)
                                                0.1
                                                1.0)))
    (testing "rate-function is called"
      (is (= true @rate-function-called?)))
    (testing "context is passed to rate-function"
      (is (= {:value 1} @context-to-fn)))
    (testing "Progress goes from 0 to 1"
      (is (every? #(and (>= % 0.0) (<= % 1.0)) @progress-distribution))
      (is #{0.1} @progress-distribution)
      (is #{1.0} @progress-distribution)
      (is-approximately-sorted? @progress-distribution))))

(deftest with-1-arity-rate-function
  (let [rate-function-called? (atom false)
        context-to-fn (atom {})
        duration-distribution (atom [])]
    (run-single-scenario {:name "scenario"
                          :steps [(step "step" true)]}
                         :rate 100
                         :users (range 10)
                         :requests 100
                         :context {:value 1}
                         :rate-distribution (fn [{:keys [progress duration context]}]
                                              (reset! context-to-fn context)
                                              (reset! rate-function-called? true)
                                              (swap! duration-distribution conj (.toMillis duration))
                                              (if (< progress 0.2)
                                                0.1
                                                1.0)))
    (testing "rate-function is called"
      (is (= true @rate-function-called?)))
    (testing "context is passed to rate-function"
      (is (= {:value 1} @context-to-fn)))
    (testing "Duration has ordered values"
      (is (> (count @duration-distribution) 10))
      (is-approximately-sorted? @duration-distribution))))


(deftest progress-tracker-is-called-if-defined
  (let [progress-tracker-call-count (atom 0)
        default-progress-tracker-defined? (atom true)]
    (run-single-scenario {:name "progress-tracker-scenario"
                          :steps [(step "step" true)]}
                         :concurrency 1
                         :duration (Duration/ofMillis 500)
                         :default-progress-tracker (fn [_])
                         :progress-tracker (fn [{:keys [default-progress-tracker]}]
                                             (when-not (fn? default-progress-tracker)
                                               (reset! default-progress-tracker-defined? false))
                                             (swap! progress-tracker-call-count inc)))
    (is (< 1 @progress-tracker-call-count))
    (is (= true @default-progress-tracker-defined?))))

(deftest scenario-weight
  (let [main-scenario {:name "Main"
                       :weight 2
                       :steps [(step "step1" true)
                               (step "step2" true)]}
        second-scenario {:name "Second"
                         :weight 1
                         :steps [(step "step1" true)]}
        result (group-by :name
                         (run-two-scenarios main-scenario second-scenario :concurrency 10 :requests 10000))
        count-requests (fn [name] (reduce + (map #(count (:requests %)) (get result name))))]
    (is (approximately== (count-requests "Main") 6666 :accuracy 35))
    (is (approximately== (count-requests "Second") 3333 :accuracy 35))
    (is (approximately== (+ (count-requests "Main") (count-requests "Second")) 10000 :accuracy 5))))

(deftest after-force-stop-fn-is-called-new-scenarios-are-not-started-anymore
  (let [sent-requests-when-force-stop-requested (atom 0)
        force-stopping-tracker (fn [{:keys [force-stop-fn
                                            sent-requests]}]
                                 (force-stop-fn)
                                 (reset! sent-requests-when-force-stop-requested sent-requests))
        results (run-single-scenario {:name "progress-tracker-scenario"
                                      :steps [(step "step" true 10)]}
                                     :concurrency 1
                                     :requests 1000
                                     :progress-tracker force-stopping-tracker)
        request-count (count (map :requests results))]
    (is (< request-count 1000))
    ;; There is a race condition between calling the force-stop-fn and sending
    ;; more requests, and test requests are very fast, so this could be a little
    ;; out
    (is (some #{@sent-requests-when-force-stop-requested} (range request-count (+ request-count 3))))))

(deftest with-step-fn
  (let [result (run-single-scenario {:name "scenario"
                                     :step-fn (fn [context]
                                                (condp = (:current context)
                                                  nil       [(step "step1" true)
                                                             (assoc context :current :started)]
                                                  :started  [(step "step2" true)
                                                             (assoc context :current :step2)]
                                                  :step2    [nil context]))}
                                    :concurrency 1
                                    :duration (time/millis 50))]
    (is (= :step2 (-> result first :requests last :context-after :current)))
    (is (not (empty? result)))))

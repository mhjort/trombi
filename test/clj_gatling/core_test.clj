(ns clj-gatling.core-test
  (:require [clojure.test :refer :all]
            [clj-async-test.core :refer :all]
            [clj-gatling.test-helpers :as th]
            [clj-gatling.reporters.raw-reporter :as raw-reporter]
            [clj-gatling.core :refer [run run-async run-simulation]]))

(use-fixtures :once th/setup-error-file-path)

(def legacy-scenario
  {:name "Test scenario"
   :context {}
   :requests [{:name "Request1" :fn th/successful-request}
              {:name "Request2" :fn th/failing-request}]})

(defn- simulation [simu-name]
  {:name simu-name
   :scenarios [{:name "Test scenario"
                :steps [(th/step "Step1" true)
                        (th/step "Step2" false)]}]})

(deftest legacy-simulation-returns-summary
  (let [summary (run-simulation [legacy-scenario] 1 {})]
    (is (= {:ok 1 :ko 1} summary))))

(deftest simulation-returns-summary
  (let [summary (run (simulation "test-summary")
                     {:requests 100
                      :concurrency 1})]
    (is (approximately== (:ok summary) 50 :accuracy 5))
    (is (approximately== (:ko summary) 50 :accuracy 5))))

(deftest simulation-returns-summary-of-all-reporters
  (let [summary (run (simulation "test-all")
                     {:reporters [th/a-reporter th/b-reporter]
                      :concurrency 1})]
    (is (= summary {:a 1 :b 1}))))

(deftest simulation-can-be-run-asynchronously
  (let [{:keys [results]} (run-async (simulation "test-summary")
                                     {:requests 100
                                      :concurrency 1})]
    (is (approximately== (:ok @results) 50 :accuracy 5))
    (is (approximately== (:ko @results) 50 :accuracy 5))))

;;TODO Test also with multiple-reporters

(deftest simulation-can-be-stopped-when-running-asynchronously
  (let [{:keys [results force-stop-fn]} (run-async (simulation "test-summary")
                                                   {:requests 500
                                                    :concurrency 1})]
    (force-stop-fn)
    (is (< (:ok @results) 260))
    (is (< (:ko @results) 260))))

(deftest simulation-returns-raw-report-from-file
  (let [summary (run (simulation "test-file-raw")
                     {:reporters [raw-reporter/file-reporter]
                      :requests 100
                      :concurrency 1})]
    (is (approximately== (count (mapcat :requests (:raw summary))) 100 :accuracy 5))))

(deftest simulation-returns-raw-report-from-memory
  (let [summary (run (simulation "test-memory-raw")
                     {:reporters [raw-reporter/in-memory-reporter]
                      :requests 100
                      :concurrency 1})]
    (is (approximately== (count (mapcat :requests (:raw summary))) 100 :accuracy 5))))

;;This code can be used to test raw reporter in repl
(comment
  (let [average (fn [coll]
                  (long (/ (reduce + coll) (count coll))))
        map-vals (fn [f m]
                   (reduce-kv #(assoc %1 %2 (f %3)) {} m))
        calculate-averages (fn [data]
                             (map-vals #(average (map (fn [{:keys [start end]}]
                                                        (- end start)) %))
                                       (group-by :name (mapcat :requests data))))
        summary (run (simulation "test-raw")
                     {:reporters [raw-reporter/file-reporter]
                     ;;{:reporters [raw-reporter/in-memory-reporter]
                      :requests 100
                      :concurrency 1})]
    (calculate-averages (:raw summary))))

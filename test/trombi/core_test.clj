(ns trombi.core-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [clj-async-test.core :refer-macros [approximately==]]
            [trombi-gatling-highcharts-reporter.core :as highcharts]
            [trombi.test-helpers :as th]
            [trombi.reporters.raw-reporter :as raw-reporter]
            [trombi.core :refer [run run-async]]))

(use-fixtures :once th/setup-error-file-path)

(defn- simulation [simu-name]
  {:name simu-name
   :scenarios [{:name "Test scenario"
                :steps [(th/step "Step1" true)
                        (th/step "Step2" false)]}]})

(defn- multi-scenario-simulation [simu-name]
  {:name simu-name
   :scenarios [{:name "Test scenario1"
                :steps [(th/step "Step1" true)
                        (th/step "Step2" false)]}
               {:name "Test scenario2"
                :steps [(th/step "Step1" true)
                        (th/step "Step2" false)]}]})

(defn- mean [data]
  (Math/round (double (/ (reduce + data) (count data)))))

(deftest simulation-returns-summary
  ;This test tries to test that trombi can split concurrency to multiple scenarios in parallel
  ;and that concurrency is stable and request count still matches
  (let [concurrency-values (atom [])
        summary (run (multi-scenario-simulation "test-summary")
                     {:requests 2000
                      :progress-tracker (fn [{:keys [total-concurrency]}]
                                          (swap! concurrency-values conj total-concurrency))
                      :concurrency 10})]
    (is (approximately== 1000 (:ok summary) :accuracy 10))
    (is (approximately== 1000 (:ko summary) :accuracy 10))
    ;We drop first value because concurrency is not stable at the very beginning
    (is (approximately== (mean (drop 1 @concurrency-values)) 10 :accuracy 20))))

(deftest simulation-returns-summary-of-all-reporters
  (let [summary (run (simulation "test-all")
                     {:reporters [th/a-reporter th/b-reporter]
                      :concurrency 1})]
    (is (= summary {:a 1 :b 1}))))

(deftest simulation-works-with-stats-running
  (let [summary (run (simulation "test-stats")
                     {:reporters [th/a-reporter]
                      :experimental-test-runner-stats? true
                      :concurrency 1})]
    (is (= summary {:a 1}))))

(deftest simulation-can-be-run-asynchronously
  (let [{:keys [results]} (run-async (simulation "test-summary")
                                     {:requests 100
                                      :concurrency 1})]
    (is (approximately== (:ok @results) 50 :accuracy 5))
    (is (approximately== (:ko @results) 50 :accuracy 5))))

;;TODO Test also with multiple-reporters

(deftest simulation-returns-results-with-gatling-highcharts-reporter
  (let [summary (run (simulation "test-highcharts")
                     {:reporters [highcharts/reporter]
                      :requests 100
                      :concurrency 1})]
    (is (not (nil? (:highcharts summary))))))

(deftest simulation-can-be-stopped-when-running-asynchronously
  (let [tracker-calls (atom 0)
        {:keys [force-stop-fn]} (run-async (simulation "test-summary")
                                           {:duration (java.time.Duration/ofSeconds 10)
                                            :progress-tracker (fn [_]
                                                                (swap! tracker-calls inc))
                                            :concurrency 1})
        _ (Thread/sleep 100)
        tracker-calls-before-force-stop @tracker-calls]
    (force-stop-fn)
    ;In here we test that progress tracker is not called after force stop has been requested
    (Thread/sleep 200)
    ;With force stop it is not guaranteed that we get proper reports so we don't check the results
    (is (= tracker-calls-before-force-stop @tracker-calls))))

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

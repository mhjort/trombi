(ns trombi.reporters.raw-reporter-test
  (:require [clojure.test :refer :all]
            [trombi.simulation-util :refer [eval-if-needed]]
            [trombi.reporters.raw-reporter :as raw])
  (:import (java.nio.file Files)
           (java.nio.file.attribute FileAttribute)))

(def scenario-results
  [{:name "Test scenario" :id 1 :start 1391936496814 :end 1391936496814
    :requests [{:id 1 :name "Request1" :start 1391936496853 :end 1391936497299 :result true}
               {:id 1 :name "Request2" :start 1391936497299 :end 1391936497996 :result true}]}
   {:name "Test scenario" :id 0 :start 1391936496808 :end 1391936496808
    :requests [{:id 0 :name "Request1" :start 1391936497998 :end 1391936498426 :result true}
               {:id 0 :name "Request2" :start 1391936498430 :end 1391936498450 :result false}]}
   {:name "Test scenario2" :id 0 :start 1391936496808 :end 1391936496808
    :requests [{:id 0 :name "Request1" :start 1391936497998 :end 1391936498426 :result true}]}])

(defn- simulate-report-generation [{:keys [collect combine]} {:keys [generate as-str]}]
  (let [batches (partition 1 scenario-results)
        [collection1 collection2 collection3] (mapv #(collect {} {:batch %1 :node-id 0 :batch-id %2}) batches (range))
        first-combination (combine collection1 collection2)
        report (generate (combine first-combination collection3))]
    [report (as-str report)]))

(deftest in-memory-reporter
  (let [collector ((eval-if-needed (:collector raw/in-memory-reporter)) {})
        generator ((eval-if-needed (:generator raw/in-memory-reporter)) {})
        [report as-str] (simulate-report-generation collector generator)]
    (is (= scenario-results report))
    (is (= "Finished 5 requests." as-str))))

(defn- create-temp-dir []
  (str (Files/createTempDirectory "raw-reporter-test" (into-array FileAttribute []))))

(deftest file-reporter
  (let [results-dir (create-temp-dir)
        params {:results-dir results-dir :context {}}
        collector ((eval-if-needed (:collector raw/file-reporter)) params)
        generator ((eval-if-needed (:generator raw/file-reporter)) params)
        [report as-str] (simulate-report-generation collector generator)]
    (is (= scenario-results report))
    (is (= (str "Generated raw report to " results-dir "/raw.log") as-str))))

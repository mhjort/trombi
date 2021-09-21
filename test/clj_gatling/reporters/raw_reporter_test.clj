(ns clj-gatling.reporters.raw-reporter-test
  (:require [clojure.test :refer :all]
            [clj-gatling.simulation-util :refer [eval-if-needed]]
            [clj-gatling.reporters.raw-reporter :as raw]))

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
  (let [report (generate (combine (collect {} {:batch (take 2 scenario-results)})
                     (collect {} {:batch (drop 2 scenario-results)})))]
    [report (as-str report)]))

(deftest in-memory-reporter
  (let [collector ((eval-if-needed (:collector raw/in-memory-reporter)) {})
        generator ((eval-if-needed (:generator raw/in-memory-reporter)) {})
        [report as-str] (simulate-report-generation collector generator)]
    (is (= scenario-results report))
    (is (= "Finished 5 requests." as-str))))

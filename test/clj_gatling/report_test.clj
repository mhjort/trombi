(ns clj-gatling.report-test
  (:require [clojure.test :refer :all]
            [clj-gatling.report :as report]
            [clj-gatling.reporters.short-summary :as short-summary]
            [clojure.core.async :as a :refer [onto-chan chan]]
            [clj-containment-matchers.clojure-test :refer :all])
  (:import (java.time LocalDateTime)))

(def scenario-results
  [{:name "Test scenario" :id 1 :start 1391936496814 :end 1391936496814
    :requests [{:id 1 :name "Request1" :start 1391936496853 :end 1391936497299 :result true}
               {:id 1 :name "Request2" :start 1391936497299 :end 1391936497996 :result true}]}
   {:name "Test scenario" :id 0 :start 1391936496808 :end 1391936496808
    :requests [{:id 0 :name "Request1" :start 1391936497998 :end 1391936498426 :result true}
               {:id 0 :name "Request2" :start 1391936498430 :end 1391936498450 :result false}]}
   {:name "Test scenario2" :id 0 :start 1391936496808 :end 1391936496808
    :requests [{:id 0 :name "Request1" :start 1391936497998 :end 1391936498426 :result true}]}])

(defn- from [coll]
  (let [c (chan)]
    (onto-chan c coll)
    c))

(def simulation
  {:name "mySimulation"
   :scenarios []})

(def response-time-collector
  (fn [_]
    {:reporter-key :response-times
     :collect (fn [simu {:keys [batch]}]
                (mapcat #(map (fn [{:keys [start end]}]
                                (- end start)) (:requests %)) batch))
     :combine concat}))

(deftest maps-scenario-results-to-log-lines
  (let [result-lines [(promise) (promise)]
        start-time (LocalDateTime/of 2014 2 9 11 1 36)
        output-writer (fn [simulation idx result]
                        (deliver (nth result-lines idx) result))
        summary (report/create-result-lines simulation
                                            2
                                            (from scenario-results)
                                            output-writer)]
    (is (equal? summary {:ok 4 :ko 1}))
    (is (equal? @(first result-lines) (take 2 scenario-results)))
    (is (equal? @(second result-lines) [(last scenario-results)]))))

(deftest waits-results-to-be-written-before-returning
  (let [result-lines [(atom nil) (atom nil)]
        start-time (LocalDateTime/of 2014 2 9 11 1 36)
        slow-writer (fn [simulation idx result]
                      (Thread/sleep 100)
                      (reset! (nth result-lines idx) result))]
    (report/create-result-lines simulation
                                2
                                (from scenario-results)
                                slow-writer)
    (is (equal? @(first result-lines) (take 2 scenario-results)))
    (is (equal? @(second result-lines) [(last scenario-results)]))))

(deftest parses-summary-using-multiple-reporters
  (let [summary (report/parse-in-batches simulation
                                         0
                                         2
                                         (from scenario-results)
                                         [(assoc (short-summary/collector {}) :reporter-key :short)
                                          (response-time-collector {})])]
    (is (equal? summary {:short {:ok 4 :ko 1} :response-times [446 697 428 20 428]}))))

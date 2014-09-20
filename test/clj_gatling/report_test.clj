(ns clj-gatling.report-test
  (:use [clojure.test]
        [clj-time.core :only [local-date-time]])
  (:require [clj-gatling.report :as report]
            [clj-containment-matchers.core :refer [contains-exactly?]]))

(def scenario-result 
  [{:name "Test scenario" :id 1 :start 1391936496814 :end 1391936496814
    :requests [{:id 1 :name "Request1" :start 1391936496853 :end 1391936497299 :result true} 
               {:id 1 :name "Request2" :start 1391936497299 :end 1391936497996 :result true}]}
   {:name "Test scenario" :id 0 :start 1391936496808 :end 1391936496808
    :requests [{:id 0 :name "Request1" :start 1391936497998 :end 1391936498426 :result true} 
               {:id 0 :name "Request2" :start 1391936498430 :end 1391936498450 :result false}]}
   {:name "Test scenario2" :id 0 :start 1391936496808 :end 1391936496808
    :requests [{:id 0 :name "Request1" :start 1391936497998 :end 1391936498426 :result true}]}])

(def expected-lines
  [["clj-gatling" "simulation" "RUN" "20140209110136" "\u0020" "2.0"]
   ["Test scenario" "1" "USER" "START" "1391936496814" "1"]
   ["Test scenario" "1" "REQUEST" "" "Request1" "1391936496853" "1391936496853" "1391936497299" "1391936497299" "OK" "\u0020"]
   ["Test scenario" "1" "REQUEST" "" "Request2" "1391936497299" "1391936497299" "1391936497996" "1391936497996" "OK" "\u0020"]
   ["Test scenario" "1" "USER" "END" "1391936496814" "1391936496814"]
   ["Test scenario" "0" "USER" "START" "1391936496808" "0"]
   ["Test scenario" "0" "REQUEST" "" "Request1" "1391936497998" "1391936497998" "1391936498426" "1391936498426" "OK" "\u0020"]
   ["Test scenario" "0" "REQUEST" "" "Request2" "1391936498430" "1391936498430" "1391936498450" "1391936498450" "KO" "\u0020"]
   ["Test scenario" "0" "USER" "END" "1391936496808" "1391936496808"]
   ["Test scenario2" "0" "USER" "START" "1391936496808" "0"]
   ["Test scenario2" "0" "REQUEST" "" "Request1" "1391936497998" "1391936497998" "1391936498426" "1391936498426" "OK" "\u0020"]
   ["Test scenario2" "0" "USER" "END" "1391936496808" "1391936496808"]])

(defn run-line-match [expected actual]
  (contains-exactly? (first expected) (first actual)))

(defn scenario-line-match [expected actual]
  (contains-exactly? (second expected) (second actual)))

(defn request-line-match [expected actual]
  (contains-exactly? (nth expected 2) (nth actual 2)))

(deftest maps-scenario-results-to-log-lines
  (let [start-time (local-date-time 2014 2 9 11 1 36)
        result-lines (report/create-result-lines start-time scenario-result)]
    (run-line-match expected-lines result-lines)
    (scenario-line-match expected-lines result-lines)
    (request-line-match expected-lines result-lines)
    (contains-exactly? expected-lines result-lines)))

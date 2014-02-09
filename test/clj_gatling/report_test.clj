(ns clj-gatling.report-test
  (:use clojure.test)
  (:require [clj-gatling.report :as report]))

(def scenario-result 
  [{:name "Test scenario" :id 1 :start 1391936496814 :end 1391936496814
    :requests [{:id 1 :name "Request1" :start 1391936496853 :end 1391936497299 :result "OK"} 
               {:id 1 :name "Request2" :start 1391936497299 :end 1391936497996 :result "OK"}]}
   {:name "Test scenario" :id 0 :start 1391936496808 :end 1391936496808
    :requests [{:id 0 :name "Request1" :start 1391936497998 :end 1391936498426 :result "OK"} 
               {:id 0 :name "Request2" :start 1391936498430 :end 1391936498450 :result "OK"}]}])

(def expected-lines
  [["RUN" "20140209110136" "simulation" "\u0020"] 
   ["SCENARIO" "Test scenario" "1" "1391936496814" "1391936496814"] 
   ["REQUEST" "Test scenario" "1" "" "Request1" "1391936496853" "1391936496853" "1391936497299" "1391936497299" "OK" "\u0020"]
   ["REQUEST" "Test scenario" "1" "" "Request2" "1391936497299" "1391936497299" "1391936497996" "1391936497996" "OK" "\u0020"] 
   ["SCENARIO" "Test scenario" "0" "1391936496808" "1391936496808"] 
   ["REQUEST" "Test scenario" "0" "" "Request1" "1391936497998" "1391936497998" "1391936498426" "1391936498426" "OK" "\u0020"]
   ["REQUEST" "Test scenario" "0" "" "Request2" "1391936498430" "1391936498430" "1391936498450" "1391936498450" "OK" "\u0020"]])

(defn run-line-match [expected actual]
  (is (= (first expected) (first actual))))

(defn scenario-line-match [expected actual]
  (is (= (second expected) (second actual))))

(defn request-line-match [expected actual]
  (is (= (nth expected 2) (nth actual 2))))

(deftest maps-scenario-results-to-log-lines
  (let [result-lines (report/create-result-lines scenario-result)]
    (run-line-match expected-lines result-lines)
    (scenario-line-match expected-lines result-lines)
    (request-line-match expected-lines result-lines)
    (is (= expected-lines result-lines))))

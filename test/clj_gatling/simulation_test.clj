(ns clj-gatling.simulation-test
  (:use clojure.test)
  (:require [clj-gatling.simulation :as simulation]))

(defn successful-request [id] true)
(defn failing-request [id] false)

(def scenario
  {:name "Test scenario"
   :requests [{:name "Request1" :fn successful-request}
              {:name "Request2" :fn failing-request}]})

(defn get-result [requests request-name]
  (:result (first (filter #(= request-name (:name %)) requests))))

(deftest simulation-returns-result-when-run-with-one-user
  (let [result (first (simulation/run-simulation [scenario] 1))]
    (is (= "Test scenario" (:name result)))
    (is (= true (get-result (:requests result) "Request1")))
    (is (= false (get-result (:requests result) "Request2")))))


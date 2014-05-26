(ns clj-gatling.simulation-test
  (:use clojure.test)
  (:require [clj-gatling.simulation :as simulation]))

(defn successful-request [id] true)
(defn failing-request [id] false)
(defn- fake-http [url id]
  (Thread/sleep 50)
  (= "success" url))

(def scenario
  {:name "Test scenario"
   :requests [{:name "Request1" :fn successful-request}
              {:name "Request2" :fn failing-request}]})

(def scenario2
  {:name "Test scenario2"
   :requests [{:name "Request1" :fn successful-request}
              {:name "Request2" :fn failing-request}]})

(def http-scenario
  {:name "Test http scenario"
   :requests [{:name "Request1" :http "success"}
              {:name "Request2" :http "fail"}]})

(defn get-result [requests request-name]
  (:result (first (filter #(= request-name (:name %)) requests))))

(deftest simulation-returns-result-when-run-with-one-user
  (let [result (first (simulation/run-simulation [scenario] 1))]
    (is (= "Test scenario" (:name result)))
    (is (= true (get-result (:requests result) "Request1")))
    (is (= false (get-result (:requests result) "Request2")))))

(deftest simulation-returns-result-when-run-with-http-requests
  (with-redefs [simulation/http-request fake-http]
    (let [result (first (simulation/run-simulation [http-scenario] 1))]
      (is (= "Test http scenario" (:name result)))
      (is (= true (get-result (:requests result) "Request1")))
      (is (= false (get-result (:requests result) "Request2"))))))

(deftest simulation-returns-result-when-run-with-multiple-scenarios-and-one-user
  (let [result (last (simulation/run-simulation [scenario scenario2] 1))]
    (is (= "Test scenario2" (:name result)))
    (is (= true (get-result (:requests result) "Request1")))
    (is (= false (get-result (:requests result) "Request2")))))


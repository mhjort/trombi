(ns clj-gatling.example
  (:require [org.httpkit.client :as http]))

(defn run-request [id]
  ;(println (str "Simulating request for " id))
  (Thread/sleep (rand 1000))
  (> 0.7 (rand 1)))

(defn http-request [url id]
  (let [{:keys [status headers body error] :as resp} @(http/get url)]
    (= 200 status)))

(def test-scenario
  {:name "Test scenario"
   :requests [{:name "Request1" :fn run-request}
              {:name "GoogleRequest" :fn (partial http-request "http://www.google.fi")}]})

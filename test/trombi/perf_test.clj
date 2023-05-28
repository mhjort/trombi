(ns trombi.perf-test
  (:require [trombi.core :as gatling])
  (:gen-class))

(defn- create-scenario [url]
  {:name "Test scenario"
   :requests [{:name "Request" :http url}]})

(defn run-simulation [url users]
  (gatling/run-simulation [(create-scenario url)] users {:root "tmp"}))

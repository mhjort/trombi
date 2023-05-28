(ns clj-gatling.core
  (:require [syklotroni.core :as syklotroni]))

(defn run-simulation [legacy-scenarios concurrency & [options]]
  (syklotroni/run-simulation legacy-scenarios concurrency options))

(defn run [simulation options]
  (syklotroni/run simulation options))

(defn run-async [simulation options]
  (syklotroni/run-async simulation options))

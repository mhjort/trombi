(ns clj-gatling.core
  (:require [trombi.core :as trombi]))

(defn run-simulation [legacy-scenarios concurrency & [options]]
  (trombi/run-simulation legacy-scenarios concurrency options))

(defn run [simulation options]
  (trombi/run simulation options))

(defn run-async [simulation options]
  (trombi/run-async simulation options))

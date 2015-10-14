(ns clj-gatling.core
  (:import (org.joda.time LocalDateTime))
  (:require [clojure-csv.core :as csv]
            [clj-gatling.chart :as chart]
            [clj-gatling.report :as report]
            [clj-gatling.scenario-parser :as scenario-parser]
            [clj-gatling.simulation :as simulation]))

(defn create-dir [dir]
  (.mkdirs (java.io.File. dir)))

(defn run-simulation [scenarios users & [options]]
 (let [start-time (LocalDateTime.)
       results-dir (or (:root options) "target/results")
       step-timeout (or (:timeout-in-ms options) 5000)
       result (simulation/run-scenarios step-timeout
                                        (scenario-parser/scenarios->runnable-scenarios scenarios users options))
       csv (csv/write-csv (report/create-result-lines start-time result) :delimiter "\t" :end-of-line "\n")]
   (create-dir (str results-dir "/input"))
   (spit (str results-dir "/input/simulation.log") csv)
   (chart/create-chart results-dir)
   (println (str "Open " results-dir "/index.html"))))

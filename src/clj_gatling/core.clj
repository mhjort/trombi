(ns clj-gatling.core
  (:import (org.joda.time LocalDateTime))
  (:require [clj-gatling.chart :as chart]
            [clj-gatling.report :as report]
            [clj-gatling.simulation :as simulation]))

(def results-dir "target/results")

(defn create-dir [dir]
  (.mkdirs (java.io.File. dir)))

(defn run-simulation [scenario users]
 (let [start-time (LocalDateTime.)
       result (simulation/run-simulation scenario users)
       csv (csv/write-csv (report/create-result-lines start-time result) :delimiter "\t" :end-of-line "\n")]
   (create-dir (str results-dir "/input"))
   (spit (str results-dir "/input/simulation.log") csv)
   (chart/create-chart results-dir)
   (println (str "Open " results-dir "/output/index.html"))))

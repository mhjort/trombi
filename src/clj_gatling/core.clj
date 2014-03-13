(ns clj-gatling.core
  (:import (org.joda.time LocalDateTime))
  (:require [clojure-csv.core :as csv]
            [clj-gatling.chart :as chart]
            [clj-gatling.report :as report]
            [clj-gatling.simulation :as simulation]))

(defn create-dir [dir]
  (.mkdirs (java.io.File. dir)))

(defn run-simulation [scenario users & [options]]
 (let [start-time (LocalDateTime.)
       results-dir (if (nil? (:root options))
                      "target/results"
                      (:root options))
       result (simulation/run-simulation scenario users)
       csv (csv/write-csv (report/create-result-lines start-time result) :delimiter "\t" :end-of-line "\n")]
   (create-dir (str results-dir "/input"))
   (spit (str results-dir "/input/simulation.log") csv)
   (chart/create-chart results-dir)
   (println (str "Open " results-dir "/output/index.html"))))

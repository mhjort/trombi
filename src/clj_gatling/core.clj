(ns clj-gatling.core
  (:import (scala.collection.mutable HashMap)
           (io.gatling.charts.report ReportsGenerator)
           (io.gatling.charts.result.reader FileDataReader)
           (io.gatling.core.config GatlingConfiguration)
           (org.joda.time LocalDateTime))
  (:require [clojure-csv.core :as csv]
            [clj-gatling.report :as report]
            [clj-gatling.simulation :as simulation]))

(def results-dir "target/results")

(defn create-dir [dir]
  (.mkdirs (java.io.File. dir)))

(defn create-chart [results-dir]
  (let [conf (HashMap.)]
    (.put conf "gatling.core.directory.results" results-dir)
    (GatlingConfiguration/setUp conf)
    (ReportsGenerator/generateFor "output" (FileDataReader. "input"))))

(defn run-simulation [scenario users]
 (let [start-time (LocalDateTime.)
       result (simulation/run-simulation scenario users)
       csv (csv/write-csv (report/create-result-lines start-time result) :delimiter "\t" :end-of-line "\n")]
   (create-dir (str results-dir "/input"))
   (spit (str results-dir "/input/simulation.log") csv)
   (create-chart results-dir)
   (println (str results-dir "/output/index.html"))))

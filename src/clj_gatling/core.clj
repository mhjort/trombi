(ns clj-gatling.core
  (:import (scala.collection.mutable HashMap)
           (io.gatling.charts.report ReportsGenerator)
           (io.gatling.charts.result.reader FileDataReader)
           (io.gatling.core.config GatlingConfiguration))
  (:require [clojure-csv.core :as csv]
            [clj-gatling.report :as report]
            [clj-gatling.simulation :as simulation]))

(defn create-results-dir []
  (dorun (map #(.mkdir (java.io.File. %)) ["results", "results/1"])))

(defn create-chart [dir]
  (let [conf (HashMap.)]
    (.put conf "gatling.core.directory.results" dir)
    (GatlingConfiguration/setUp conf)
    (ReportsGenerator/generateFor "out" (FileDataReader. "1"))))

(defn run-simulation [scenario users]
 (let [result (simulation/run-simulation scenario users)
       csv (csv/write-csv (report/create-result-lines result) :delimiter "\t" :end-of-line "\n")]
   (println csv)
   (create-results-dir)
   (spit "results/1/simulation.log" csv)
   (create-chart "results")
   (println "Open results/out/index.html")))

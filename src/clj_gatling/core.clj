(ns clj-gatling.core
  (:import (io.gatling.charts.report ReportsGenerator)
           (io.gatling.charts.result.reader FileDataReader)
           (io.gatling.core.config GatlingConfiguration))
  (:require [clojure-csv.core :as csv]
            [clj-gatling.report :as report]
            [clj-gatling.simulation :as simulation]
            [clj-gatling.example :as example])
  (:gen-class))

(defn create-chart [dir]
  (let [conf (scala.collection.mutable.HashMap.)]
    (.put conf "gatling.core.directory.results" dir)
    (GatlingConfiguration/setUp conf)
    (ReportsGenerator/generateFor "out" (FileDataReader. "23"))))

(defn -main [users]
  (let [result (simulation/run-simulation example/test-scenario (read-string users))
        csv (csv/write-csv (report/create-result-lines result) :delimiter "\t" :end-of-line "\n")]
    (println csv)
    (spit "results/23/simulation.log" csv)
    (create-chart "results")
    (println "Open results/out/index.html")))

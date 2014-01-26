(ns clj-gatling.core
  (:import (io.gatling.charts.report ReportsGenerator)
           (io.gatling.charts.result.reader FileDataReader)
           (io.gatling.core.config GatlingConfiguration))
  (:require [clojure.core.async :as async :refer [go <! >!]]
            [clojure-csv.core :as csv])
  (:gen-class))

(defn process [id]
  (println (str "executing" id))
  (str "processed " id))

(defn run-simulation [threads]
  (println (str "Run simulation with " threads " threads"))
  (let [cs (repeatedly threads async/chan)
        ps (map vector (iterate inc 1) cs)]
    (doseq [[i c] ps] (go (>! c (process i))))
    (dotimes [i threads]
      (let [[v c] (async/alts!! cs)]
        (println v)))
    (doseq [c cs] (async/close! c))))

(defn create-chart [dir]
  (let [conf (scala.collection.mutable.HashMap.)]
    (.put conf "gatling.core.directory.results" dir)
    (GatlingConfiguration/setUp conf)
    (ReportsGenerator/generateFor "out" (FileDataReader. "23"))))

(defn -main [threads]
  (run-simulation (read-string threads))
  (let [result (csv/write-csv [
    ["RUN" "20140124213040" "basicexamplesimulation" "\u0020"]
    ["REQUEST" "Scenario name" "0" "" "request_1" "1390591841187" "1390591841187" "1390591841245" "1390591841338" "OK" "\u0020"]
    ["SCENARIO"	"Scenario name"	"0"	"1390591841156"	"1390591841393"]
                               ]
    :delimiter "\t" :end-of-line "\n")]
    (println result)
    (spit "results/23/simulation.log" result)
    (create-chart "results")))

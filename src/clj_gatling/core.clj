(ns clj-gatling.core
  (:import (io.gatling.charts.report ReportsGenerator)
           (io.gatling.charts.result.reader FileDataReader)
           (io.gatling.core.config GatlingConfiguration))
  (:require [clojure.core.async :as async :refer [go <! >!]]
            [clojure-csv.core :as csv])
  (:gen-class))

(defn process [id]
  (let [start (System/currentTimeMillis)]
    (println (str "Doing some heavy calculation for " id))
    (Thread/sleep (rand 1000))
    {:id id :start start :end (System/currentTimeMillis)}))


(defn run-simulation [threads]
  (println (str "Run simulation with " threads " threads"))
  (let [cs (repeatedly threads async/chan)
        ps (map vector (iterate inc 1) cs)]
    (doseq [[i c] ps] (go (>! c (process i))))
    (let [result (for [i (range threads)]
      (let [[v c] (async/alts!! cs)]
        v))]
      (println result)
      (doseq [c cs] (async/close! c))
      result)))

(defn create-chart [dir]
  (let [conf (scala.collection.mutable.HashMap.)]
    (.put conf "gatling.core.directory.results" dir)
    (GatlingConfiguration/setUp conf)
    (ReportsGenerator/generateFor "out" (FileDataReader. "23"))))

(defn map-request [line]
  (let [start (.toString (:start line))
        end (.toString (:end line))]
    ["REQUEST" "Scenario name" "0" "" "request_1" start start "1390591841245" end "OK" "\u0020"]))

(defn map-scenario [line]
  (let [start (.toString (:start line))
        end (.toString (:end line))]
    ["SCENARIO"	"Scenario name"	"0"	start end]))

(defn create-result [times]
  (let [header ["RUN" "20140124213040" "basicexamplesimulation" "\u0020"]]
    [header (map-request (first times)) (map-scenario (first times))]))

(defn -main [threads]
  (let [result (run-simulation (read-string threads))
        csv (csv/write-csv (create-result result) :delimiter "\t" :end-of-line "\n")]
    (println csv)
    (spit "results/23/simulation.log" csv)
    (create-chart "results")))

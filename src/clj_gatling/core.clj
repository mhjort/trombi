(ns clj-gatling.core
  (:use [clojure.set :only [rename-keys]])
  (:import (io.gatling.charts.report ReportsGenerator)
           (io.gatling.charts.result.reader FileDataReader)
           (io.gatling.core.config GatlingConfiguration))
  (:require [clojure.core.async :as async :refer [go <! >!]]
            [clojure-csv.core :as csv])
  (:gen-class))

(def test-scenario {:requests ["Request1" "Request2"]})

(defmacro bench [expr]
  `(let [start# (System/currentTimeMillis)
         result# ~expr]
      {:result result# :start start# :end (System/currentTimeMillis) }))

(defn run-request [name id]
  ;(println (str "Simulating request for " id))
  (Thread/sleep (rand 1000))
  "OK")

(defn run-scenario [scenario id]
  (assoc
    (rename-keys 
      (bench
        (map #(assoc (bench (run-request % id)) :name % :id id) (:requests scenario)))
      {:result :requests})
    :id id))

(defn run-simulation [threads]
  (println (str "Run simulation with " threads " threads"))
  (let [cs (repeatedly threads async/chan)
        ps (map vector (iterate inc 0) cs)]
    (doseq [[i c] ps] (go (>! c (run-scenario test-scenario i))))
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

(defn map-request [request]
  (let [start (.toString (:start request))
        end (.toString (:end request))]
    ["REQUEST" "Scenario name" (.toString (:id request)) "" (:name request) start start "1390591841245" end "OK" "\u0020"]))

(defn flatten-one-level [coll]  
  (mapcat #(if (sequential? %) % [%]) coll))

(defn map-scenario [scenario]
  (let [start (.toString (:start scenario))
        end (.toString (:end scenario))
        requests (apply concat (map #(vector (map-request %)) (:requests scenario)))]
    (conj requests ["SCENARIO" "Scenario name" (.toString (:id scenario)) start end])))

(defn create-result-lines [result]
  (let [header ["RUN" "20140124213040" "basicexamplesimulation" "\u0020"]
        scenarios (apply concat (map #(vector (map-scenario %)) result))
       result-lines (conj (flatten-one-level scenarios) header)]
    (println result-lines)
    result-lines))

(defn -main [threads]
  (let [result (run-simulation (read-string threads))
        csv (csv/write-csv (create-result-lines result) :delimiter "\t" :end-of-line "\n")]
    (println csv)
    (spit "results/23/simulation.log" csv)
    (create-chart "results")
    (println "Open results/out/index.html")))

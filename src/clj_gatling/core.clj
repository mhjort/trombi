(ns clj-gatling.core
  (:use [clojure.set :only [rename-keys]])
  (:import (io.gatling.charts.report ReportsGenerator)
           (io.gatling.charts.result.reader FileDataReader)
           (io.gatling.core.config GatlingConfiguration))
  (:require [clojure.core.async :as async :refer [go <! >!]]
            [clojure-csv.core :as csv]
            [clj-gatling.report :as report]
            [clj-gatling.example :as example])
  (:gen-class))

(defmacro bench [expr]
  `(let [start# (System/currentTimeMillis)
         result# ~expr]
      {:result result# :start start# :end (System/currentTimeMillis) }))

(defn run-scenario [scenario id]
  (assoc
    (rename-keys 
      (bench
        (map #(assoc (bench ((:fn %) id)) :name (:name %) :id id) (:requests scenario)))
      {:result :requests})
    :id id :name (:name scenario)))

(defn run-simulation [scenario users]
  (println (str "Run simulation with " users " users"))
  (let [cs (repeatedly users async/chan)
        ps (map vector (iterate inc 0) cs)]
    (doseq [[i c] ps] (go (>! c (run-scenario scenario i))))
    (let [result (for [i (range users)]
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

(defn -main [users]
  (let [result (run-simulation example/test-scenario (read-string users))
        csv (csv/write-csv (report/create-result-lines result) :delimiter "\t" :end-of-line "\n")]
    (println csv)
    (spit "results/23/simulation.log" csv)
    (create-chart "results")
    (println "Open results/out/index.html")))

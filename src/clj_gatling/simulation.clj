(ns clj-gatling.simulation
  (:use [clojure.set :only [rename-keys]])
  (:require [clojure.core.async :as async :refer [go <! >!]]))

(defmacro bench [expr]
  `(let [start# (System/currentTimeMillis)
         result# ~expr]
      {:result result# :start start# :end (System/currentTimeMillis) }))

(defn- run-scenario [scenario id]
  (assoc
    (rename-keys 
      (bench
        (map #(assoc (bench ((:fn %) id)) :name (:name %) :id id) (:requests scenario)))
      {:result :requests})
    :id id :name (:name scenario)))

(defn- collect-result [cs]
  (let [[result c] (async/alts!! cs)]
    result))

(defn- run-parallel-and-collect-results [function times]
  (let [cs (repeatedly times async/chan)
        ps (map vector (iterate inc 0) cs)]
    (doseq [[i c] ps] (go (>! c (function i))))
    (let [results (repeatedly times (partial collect-result cs))]
      (dorun results) ;Lazy results must be evaluated before channels are closed
      (doseq [c cs] (async/close! c))
      results)))

(defn- run-nth-scenario-with-multiple-users [scenarios users i]
  (let [scenario (nth scenarios i)]
     (println (str "Running scenario " (:name scenario) " with " users " users."))
     (run-parallel-and-collect-results (partial run-scenario scenario) users)))

(defn run-simulation [scenarios users]
  (let [function (partial run-nth-scenario-with-multiple-users scenarios users) 
        results (run-parallel-and-collect-results function (count scenarios))]
    (flatten results)))

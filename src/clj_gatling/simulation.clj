(ns clj-gatling.simulation
  (:use [clojure.set :only [rename-keys]])
  (:require [clojure.core.async :as async :refer [go <! >!]]))

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

(defn collect-result [cs]
  (let [[result c] (async/alts!! cs)]
    result))

(defn run-simulation [scenarios users]
  (let [cs (repeatedly users async/chan)
        ps (map vector (iterate inc 0) cs)]
    (println (str "Running scenario " (:name (first scenarios)) " with " users " users."))
    (doseq [[i c] ps] (go (>! c (run-scenario (first scenarios) i))))
    (let [results (repeatedly users (partial collect-result cs))]
      (dorun results) ;Lazy results must be evaluated before channels are closed
      (doseq [c cs] (async/close! c))
      results)))

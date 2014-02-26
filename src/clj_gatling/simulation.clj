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

(defn run-simulation [scenario users]
  (let [cs (repeatedly users async/chan)
        ps (map vector (iterate inc 0) cs)]
    (doseq [[i c] ps] (go (>! c (run-scenario scenario i))))
    (let [result (for [i (range users)]
      (let [[v c] (async/alts!! cs)]
        v))]
      (println result)
      (doseq [c cs] (async/close! c))
      result)))

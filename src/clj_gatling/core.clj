(ns clj-gatling.core
  (:require [clojure.core.async :as async :refer [go <! >!]])
  (:gen-class))

(defn process [id]
  (println (str "executing" id))
  (str "processed " id))

(defn -main [threads]
  (println (str "Run simulation with " threads " threads"))
  (let [cs (repeatedly (read-string threads) async/chan)
        ps (map vector (iterate inc 1) cs)]
    (doseq [[i c] ps] (go (>! c (process i))))
    (dotimes [i (read-string threads)]
      (let [[v c] (async/alts!! cs)] 
        (println v)))
    (doseq [c cs] (async/close! c))))

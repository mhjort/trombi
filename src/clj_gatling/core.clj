(ns clj-gatling.core
  (:require [clojure.core.async :as async :refer [go <! >!]]))

(defn process [id]
  (println "executing")
  (str "processed " id))

(defn main [threads]
  (println (str "Run simulation with " threads " threads"))
  (let [cs (repeatedly (read-string threads) async/chan)]
    (doseq [c cs] (go (>! c (process 1))))
    (dotimes [i (read-string threads)]
      (let [[v c] (async/alts!! cs)] 
        (println v)))
    (doseq [c cs] (async/close! c))))

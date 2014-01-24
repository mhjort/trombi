(ns clj-gatling.core
  (:import (io.gatling.charts.report ReportsGenerator))
  (:require [clojure.core.async :as async :refer [go <! >!]])
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

(defn -main [threads]
  (run-simulation (read-string threads)))
;  (ReportsGenerator/generateFor "out" nil))

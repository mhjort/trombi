(ns syklotroni.stats
  (:require [clojure.core.async :refer [chan go alts! >!! <!! timeout]])
  (:import (java.time LocalDateTime)
           (java.lang.management ManagementFactory)))

(defn- calculate-stats [stat-rows]
  (let [active-thread-counts (map :active-thread-count stat-rows)]
    {:active-thread-count {:average (int (/ (reduce + active-thread-counts) (count stat-rows)))
                           :max (apply max active-thread-counts)}}))

(defn no-op []
  {:stop-fn #() :print-fn #()})

(defn start-stats-gathering []
  (let [poison-pill (chan)
        stats-ch
        (go
          (loop [stat-rows []]
            (let [[_ ch] (alts! [poison-pill (timeout 200)])]
              (if-not (= poison-pill ch)
                (let [stats-row-item {:timestamp (LocalDateTime/now)
                                      :active-thread-count (.getThreadCount (ManagementFactory/getThreadMXBean))}]
                  (recur (conj stat-rows stats-row-item)))
                stat-rows))))]
    {:stop-fn #(>!! poison-pill true)
     :print-fn #(println "Test runner statistics:" (calculate-stats (<!! stats-ch)))}))

(comment
  (let [{:keys [stop-fn print-fn]} (start-stats-gathering)]
    (Thread/sleep 600)
    (stop-fn)
    (print-fn)
    ))

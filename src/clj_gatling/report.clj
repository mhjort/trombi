(ns clj-gatling.report
  (:require [clojure.core.async :as a :refer [thread <!!]]))

(defn create-result-lines [simulation buffer-size results-channel output-writer]
  (let [summary (fn [result] (frequencies (mapcat #(map :result (:requests %)) result)))
        ;Note! core.async/partition is deprecated function.
        ;This should be changed to use transducers instead
        results (a/partition buffer-size results-channel)
        write-results (loop [idx 0
                             threads []]
                        (if-let [result (<!! results)]
                          (let [t (thread
                                    (output-writer simulation idx result)
                                    (summary result))]
                            (recur (inc idx) (conj threads t)))
                          threads))]
    (reduce (fn [m [k v]]
              (if k
                (update m :ok + v)
                (update m :ko + v)))
            {:ok 0 :ko 0}
            (mapcat #(<!! %) write-results))))

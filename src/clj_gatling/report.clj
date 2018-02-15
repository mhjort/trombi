(ns clj-gatling.report
  (:require [schema.core :refer [validate]]
            [clojure.set :refer [rename-keys]]
            [clj-gatling.schema :as schema]
            [clojure.core.async :as a :refer [to-chan thread <!!]]))

(defn create-result-lines [simulation buffer-size results-channel output-writer]
  (validate schema/Simulation simulation)
  (let [summary (fn [result]
                  (rename-keys (frequencies (mapcat #(map :result (:requests %)) result))
                               {true :ok false :ko}))
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
    (reduce (fn [m curr]
              (println m curr)
              (merge-with + m (apply hash-map curr)))
            {}
            (mapcat #(<!! %) write-results))))

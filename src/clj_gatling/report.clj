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

(def short-summary-reporter
  {:reporter-key :short
   :parser  (fn [_ _ batch]
              (rename-keys (frequencies (mapcat #(map :result (:requests %)) batch))
                           {true :ok false :ko}))
   :combiner #(merge-with + %1 %2)})

(defn- parse-with-reporters [simulation idx batch reporters]
  (reduce
    into
    (map (fn [{:keys [reporter-key parser]}]
           {reporter-key (parser simulation idx batch)}) reporters)))

(defn combine-with-reporters [reporters a b]
  (let [reporters-map (reduce (fn [m curr]
                                (assoc m (:reporter-key curr) curr))
                              {}
                              reporters)]
    (reduce-kv (fn [m k v]
              (let [combiner (:combiner (k reporters-map))]
                (update m k #(combiner % (k b)))))
               a
               reporters-map)))

(defn parse-in-batches [simulation batch-size results-channel reporters]
  (validate schema/Simulation simulation)
  (let [;Note! core.async/partition is deprecated function.
        ;This should be changed to use transducers instead
        results (a/partition batch-size results-channel)
        write-results (loop [idx 0
                             threads []]
                        (if-let [result (<!! results)]
                          (let [t (thread
                                    (parse-with-reporters simulation idx result reporters))]
                            (recur (inc idx) (conj threads t)))
                          threads))]
    (reduce (partial combine-with-reporters reporters)
            (map #(<!! %) write-results))))

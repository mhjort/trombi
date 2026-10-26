(ns trombi.report
  (:require [schema.core :refer [validate]]
            [clojure.set :refer [rename-keys]]
            [trombi.schema :as schema]
            [clojure.core.async :refer [thread <!!]]))

(defn- take-batch
  "Takes up to batch-size values from the channel. Returns nil when the channel
  has been closed and there are no values left."
  [batch-size channel]
  (loop [batch []]
    (if (< (count batch) batch-size)
      (if-some [v (<!! channel)]
        (recur (conj batch v))
        (not-empty batch))
      batch)))

(defn create-result-lines [simulation buffer-size results-channel output-writer]
  (validate schema/Simulation simulation)
  (let [summary (fn [result]
                  (rename-keys (frequencies (mapcat #(map :result (:requests %)) result))
                               {true :ok false :ko}))
        write-results (loop [idx 0
                             threads []]
                        (if-let [result (take-batch buffer-size results-channel)]
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

(defn- parse-with-reporters [simulation batch reporters]
  (reduce
    into
    (map (fn [{:keys [reporter-key collect]}]
           {reporter-key (collect simulation batch)}) reporters)))

(defn- create-reporters-map [reporters]
  (reduce (fn [m curr]
            (assoc m (:reporter-key curr) curr))
          {}
          reporters))

(def reporters-map (memoize create-reporters-map))

(defn combine-with-reporters [reporters a b]
  (reduce-kv (fn [m k _]
               (let [combine (:combine (k (reporters-map reporters)))]
                 (update m k #(combine % (k b)))))
             a
             (reporters-map reporters)))

(defn generate-with-reporters [reporters combined-values-per-report-map]
  ;In force stop case results might be empty
  (when combined-values-per-report-map
    (reduce-kv (fn [m k _]
                 (let [generate (:generate (k (reporters-map reporters)))]
                   (update m k #(generate %))))
               combined-values-per-report-map
               (reporters-map reporters))))

(defn as-str-with-reporters [reporters summary]
  (map (fn [[k reporter]]
         (str "Result for reporter `" (name k) "`: " ((:as-str reporter) (k summary))))
       (reporters-map reporters)))

(defn parse-in-batches [simulation node-id batch-size results-channel reporters]
  (validate schema/Simulation simulation)
  (let [write-results (loop [idx 0
                             threads []]
                        (if-let [result (take-batch batch-size results-channel)]
                          (let [t (thread
                                    (parse-with-reporters simulation
                                                          {:node-id node-id :batch-id idx :batch result}
                                                          reporters))]
                            (recur (inc idx) (conj threads t)))
                          threads))
        results-from-channels (map #(<!! %) write-results)]
    ;In force stop case results might be empty
    (when (seq results-from-channels)
      (reduce (partial combine-with-reporters reporters)
              results-from-channels))))

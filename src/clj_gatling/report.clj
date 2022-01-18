(ns clj-gatling.report
  (:require [schema.core :refer [validate]]
            [clojure.set :refer [rename-keys]]
            [clj-gatling.schema :as schema]
            [clojure.core.async :as a :refer [thread <!!]]))

(defn create-result-lines [simulation buffer-size results-channel output-writer]
  (validate schema/Simulation simulation)
  (let [summary (fn [result]
                  (rename-keys (frequencies (mapcat #(map :result (:requests %)) result))
                               {true :ok false :ko}))
        ;;Note! core.async/partition is deprecated function.
        ;;This should be changed to use transducers instead
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

(defn generate-with-reporters [reporters a]
  (reduce-kv (fn [m k _]
               (let [generate (:generate (k (reporters-map reporters)))]
                 (update m k #(generate %))))
             a
             (reporters-map reporters)))

(defn as-str-with-reporters [reporters summary]
  (map (fn [[k reporter]]
         (str "Result for reporter `" (name k) "`: " ((:as-str reporter) (k summary))))
       (reporters-map reporters)))

(defn parse-in-batches [simulation node-id batch-size results-channel reporters]
  (validate schema/Simulation simulation)
  (let [;;Note! core.async/partition is deprecated function.
        ;;This should be changed to use transducers instead
        results (a/partition batch-size results-channel)
        write-results (loop [idx 0
                             threads []]
                        (if-let [result (<!! results)]
                          (let [t (thread
                                    (parse-with-reporters simulation
                                                          {:node-id node-id :batch-id idx :batch result}
                                                          reporters))]
                            (recur (inc idx) (conj threads t)))
                          threads))]
    (reduce (partial combine-with-reporters reporters)
            (map #(<!! %) write-results))))

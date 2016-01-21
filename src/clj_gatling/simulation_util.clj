(ns clj-gatling.simulation-util
  (:import [clj_gatling.simulation_runners FixedRequestNumberRunner DurationRunner]))

(defn- distinct-request-count [scenarios]
  (reduce + (map #(count (:requests %)) scenarios)))

(defn- smallest-vector [vector-of-vectors]
  (reduce (fn [m k]
          (if (< (count k) (count m))
            k
            m))
        (first vector-of-vectors)
        (rest vector-of-vectors)))

(defn- idx-of-smallest-vector [vector-of-vectors]
  (.indexOf vector-of-vectors (smallest-vector vector-of-vectors)))

(defn- idx-of-first-vector-with-nil [vector-of-vectors]
  (.indexOf vector-of-vectors
            (first (filter #(.contains % nil) vector-of-vectors))))

(defn split-to-number-of-buckets [xs bucket-count]
  (reduce (fn [m v]
            (update m (idx-of-smallest-vector m) conj v))
          (vec (repeat bucket-count []))
          xs))

(defn split-to-buckets-with-sizes [xs bucket-sizes]
  (reduce (fn [m v]
            (update m (idx-of-first-vector-with-nil m) #(conj (drop-last %) v)))
          (mapv #(repeat % nil) bucket-sizes)
          xs))

(defn- weighted [weights value]
  (let [sum-of-weights (reduce + weights)]
    (map #(max 1 (Math/round (double (* value (/ % sum-of-weights))))) weights)))

(defn weighted-scenarios [users scenarios]
  {:pre [(>= (count users) (count scenarios))]}
  (let [weights            (map #(or (:weight %) 1) scenarios)
        concurrencies      (weighted weights (count users))
        with-concurrencies (map #(assoc %1 :concurrency %2)
                                scenarios
                                concurrencies)]
    (map #(assoc %1 :users %2)
         with-concurrencies
         (split-to-buckets-with-sizes users
                                      (map :concurrency with-concurrencies)))))

(defn choose-runner [scenarios concurrency options]
  (let [duration (:duration options)
        requests (or (:requests options) (* concurrency (distinct-request-count scenarios)))]
    (if (nil? duration)
      (FixedRequestNumberRunner. requests)
      (DurationRunner. duration))))

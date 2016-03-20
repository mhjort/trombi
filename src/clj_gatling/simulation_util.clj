(ns clj-gatling.simulation-util
  (:require [clj-time.core :as t]
            [clj-time.format :as f])
  (:import [java.util List]
           [clj_gatling.simulation_runners FixedRequestNumberRunner DurationRunner]))

(defn- distinct-request-count [scenarios]
  (reduce + (map #(count (:requests %)) scenarios)))

(defn- smallest-vector [vector-of-vectors]
  (reduce (fn [m k]
          (if (< (count k) (count m))
            k
            m))
        (first vector-of-vectors)
        (rest vector-of-vectors)))

(defn- idx-of-smallest-vector [^List vector-of-vectors]
  (.indexOf vector-of-vectors (smallest-vector vector-of-vectors)))

(defn- idx-of-first-vector-with-nil [^List vector-of-vectors]
  (.indexOf vector-of-vectors
            (first (filter (fn [^List xs]
                             (.contains xs nil)) vector-of-vectors))))

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
    ;We might get rounding errors and therefore we have to loop and
    ;reduce weights until sum of weights is equal to given value
    (loop [^List xs (mapv #(max 1
                         (Math/round (double (* value (/ % sum-of-weights)))))
                   weights)]
      (let [max-elem-idx (.indexOf xs (apply max xs))]
        (if (> (reduce + xs) value)
          (recur (update xs max-elem-idx dec))
          xs)))))

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

(defn timestamp-str []
  (let [custom-formatter (f/formatter "yyyyMMddHHmmssSSS")]
    (f/unparse custom-formatter (t/now))))

(ns clj-gatling.simulation-util
  (:require [clj-time.core :as t]
            [clj-time.format :as f])
  (:import [java.util List]
           [java.io File]
           [java.io StringWriter PrintWriter]
           [clj_gatling.simulation_runners FixedRequestNumberRunner DurationRunner]))

(defn create-dir [^String dir]
  (.mkdirs (File. dir)))

(defn append-file
  "Append `contents` to file at `path`. The file is created
  if it doesn't already exist."
  [^String path contents]
  (with-open [w (clojure.java.io/writer path :append true)]
    (.write w contents)))

(defn path-join [& paths]
  (.getCanonicalFile (apply clojure.java.io/file paths)))

;; FIXME: Is this method in the right namespace?
(defn exception->str
  "Convert an exception object to a string representation."
  [^Exception e]
  (let [sw (StringWriter.)
        pw (PrintWriter. sw)]
    (.printStackTrace e pw)
    (.toString sw)))

(defn log-exception
  "Log exception `e` to file at `path`."
  [path e]
  (append-file path (exception->str e)))

(defn- distinct-request-count [scenarios]
  (reduce +
          (map #(max (count (:steps %))
                     (count (:requests %))) ;For legacy support
               scenarios)))

(defn- idx-of-first-vector-with-nil [^List vector-of-vectors]
  (.indexOf vector-of-vectors
            (first (filter (fn [^List xs]
                             (.contains xs nil)) vector-of-vectors))))

(defn- generate-empty-buckets [bucket-sizes]
  (mapv #(repeat % nil) bucket-sizes))

(defn split-to-buckets-with-sizes [xs bucket-sizes]
  (reduce (fn [m v]
            (update m (idx-of-first-vector-with-nil m) #(conj (butlast %) v)))
          (generate-empty-buckets bucket-sizes)
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
    (map #(assoc (dissoc %1 :weight) :users %2)
         scenarios
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

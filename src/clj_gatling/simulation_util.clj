(ns clj-gatling.simulation-util
  (:require [clj-time.core :as t]
            [clj-time.format :as f]
            [clojure.java.io :as io]
            [clojure.string :as str])
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
  (with-open [w (io/writer path :append true)]
    (.write w contents)))

(defn path-join [& paths]
  (.getCanonicalPath (apply io/file paths)))

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

(defn- split-by-weight [total weights]
  (let [sum-weights (reduce + weights)
        xs (map #(int (* total (/ % sum-weights))) weights)
        mismatch (- total (reduce + xs))]
    (map #(+ %1 %2) xs (concat (repeat mismatch 1) (repeat total 0)))))

(defn split-to-buckets [ids bucket-sizes]
  (loop [start-idx 0
         result []
         sizes bucket-sizes]
    (if (empty? sizes)
      result
      (recur (+ start-idx (first sizes))
             (conj result (subvec ids start-idx (+ start-idx (first sizes))))
             (drop 1 sizes)))))

(defn weighted-scenarios [users scenarios]
  {:pre [(>= (count users) (count scenarios))]}
  (let [weights            (map #(or (:weight %) 1) scenarios)
        xs                 (split-to-buckets (vec users)
                                             (split-by-weight (count users) weights))]
    (map #(assoc (dissoc %1 :weight) :users %2)
         scenarios
         xs)))

(defn choose-runner [scenarios concurrency options]
  (let [duration (:duration options)
        requests (or (:requests options) (* concurrency (distinct-request-count scenarios)))]
    (if (nil? duration)
      (FixedRequestNumberRunner. requests)
      (DurationRunner. duration))))

(defn timestamp-str []
  (let [custom-formatter (f/formatter "yyyyMMddHHmmssSSS")]
    (f/unparse custom-formatter (t/now))))

(defn create-report-name
  "Create a gatling compatible filename for report output: 'SimulationName-Timestamp'"
  [simulation-name]
  (let [sanitized-prefix (str/replace (or simulation-name "empty_name") #"[^a-zA-Z0-9_]" "")]
    (str sanitized-prefix "-" (timestamp-str))))

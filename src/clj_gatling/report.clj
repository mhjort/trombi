(ns clj-gatling.report
  (:require [clojure-csv.core :refer [write-csv]]
            [clj-time.format :refer [formatter unparse-local]]
            [clojure.core.async :as a :refer [thread <!!]]))

(defn- flatten-one-level [coll]
  (mapcat #(if (sequential? %) % [%]) coll))

(defn map-request [scenario-name request]
  (let [start (str (:start request))
        end (str (:end request))
        id (str (:id request))
        execution-start start
        request-end start
        response-start end
        execution-end end
        result (if (:result request) "OK" "KO")]
    [scenario-name id "REQUEST" "" (:name request) execution-start request-end response-start execution-end result "\u0020"]))

(defn- scenario->rows [scenario]
  (let [start (str (:start scenario))
        end (str (:end scenario))
        id (str (:id scenario))
        scenario-start [(:name scenario) id "USER" "START" start id]
        scenario-end [(:name scenario) id "USER" "END" end end]
        requests (mapcat #(vector (map-request (:name scenario) %)) (:requests scenario))]
    (concat [scenario-start] requests [scenario-end])))

(defn gatling-csv-lines [start-time simulation idx results]
  (let [timestamp (unparse-local (formatter "yyyyMMddhhmmss") start-time)
        header ["clj-gatling" (:name simulation) "RUN" timestamp "\u0020" "2.0"]]
    (conj (flatten-one-level (mapcat #(vector (scenario->rows %)) results)) header)))

(defn gatling-csv-writer [path start-time simulation idx results]
  (let [result-lines (gatling-csv-lines start-time simulation idx results)
        csv (write-csv result-lines :delimiter "\t" :end-of-line "\n")]
    (spit (str path "/simulation" idx ".log") csv)))

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

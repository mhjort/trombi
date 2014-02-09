(ns clj-gatling.report
  (:use [clj-time.format :only [formatter unparse-local]])
  (:import (org.joda.time LocalDateTime)))

(defn flatten-one-level [coll]  
  (mapcat #(if (sequential? %) % [%]) coll))

(defn map-request [scenario-name request]
  (let [start (.toString (:start request))
        end (.toString (:end request))
        execution-start start
        request-end start
        response-start end
        execution-end end]
    ["REQUEST" scenario-name (.toString (:id request)) "" (:name request) execution-start request-end response-start execution-end "OK" "\u0020"]))

(defn map-scenario [scenario]
  (let [start (.toString (:start scenario))
        end (.toString (:end scenario))
        requests (apply concat (map #(vector (map-request (:name scenario) %)) (:requests scenario)))]
    (conj requests ["SCENARIO" (:name scenario) (.toString (:id scenario)) start end])))

(defn first-scenario-start [result]
  (LocalDateTime. (:start (first (sort-by :id result)))))

(defn create-result-lines [result]
  (let [timestamp (unparse-local (formatter "yyyyMMddhhmmss") (first-scenario-start result))
        header ["RUN" timestamp "simulation" "\u0020"]
        scenarios (apply concat (map #(vector (map-scenario %)) result))
       result-lines (conj (flatten-one-level scenarios) header)]
    (println result-lines)
    result-lines))

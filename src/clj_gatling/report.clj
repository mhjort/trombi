(ns clj-gatling.report
  (:use [clj-time.format :only [formatter unparse-local]]))

(defn flatten-one-level [coll]  
  (mapcat #(if (sequential? %) % [%]) coll))

(defn map-request [scenario-name request]
  (let [start (.toString (:start request))
        end (.toString (:end request))
        execution-start start
        request-end start
        response-start end
        execution-end end
        result (if (:result request) "OK" "KO")]
    ["REQUEST" scenario-name (.toString (:id request)) "" (:name request) execution-start request-end response-start execution-end result "\u0020"]))

(defn map-scenario [scenario]
  (let [start (.toString (:start scenario))
        end (.toString (:end scenario))
        requests (apply concat (map #(vector (map-request (:name scenario) %)) (:requests scenario)))]
    (conj requests ["SCENARIO" (:name scenario) (.toString (:id scenario)) start end])))

(defn first-scenario-start [result]
  (LocalDateTime. (:start (first (sort-by :id result)))))

(defn create-result-lines [start-time result]
  (let [timestamp (unparse-local (formatter "yyyyMMddhhmmss") start-time)
        header ["RUN" timestamp "simulation" "\u0020"]
        scenarios (apply concat (map #(vector (map-scenario %)) result))]
    (conj (flatten-one-level scenarios) header)))

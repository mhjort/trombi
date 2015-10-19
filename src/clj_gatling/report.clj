(ns clj-gatling.report
  (:use [clj-time.format :only [formatter unparse-local]]))

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
    [scenario-name id "REQUEST" "" (:name request)  execution-start request-end response-start execution-end result "\u0020"]))

(defn- map-scenario [scenario]
  (let [start (str (:start scenario))
        end (str (:end scenario))
        id (str (:id scenario))
        scenario-start [(:name scenario) id "USER" "START" start id]
        scenario-end [(:name scenario) id "USER" "END" end end]
        requests (mapcat #(vector (map-request (:name scenario) %)) (:requests scenario))]
    (concat [scenario-start] requests [scenario-end])))

(defn create-result-lines [start-time result]
  (let [timestamp (unparse-local (formatter "yyyyMMddhhmmss") start-time)
        header ["clj-gatling" "simulation" "RUN" timestamp "\u0020" "2.0"]
        scenarios (mapcat #(vector (map-scenario %)) result)]
    (conj (flatten-one-level scenarios) header)))

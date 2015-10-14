(ns clj-gatling.scenario-parser
  (:import [clj_gatling.scenario_runners FixedRequestNumberRunner DurationRunner]))

(defn- distinct-request-count [scenarios]
  (reduce + (map #(count (:requests %)) scenarios)))

(defn- weighted [weights value]
  (let [sum-of-weights (reduce + weights)]
    (map #(max 1 (Math/round (double (* value (/ % sum-of-weights))))) weights)))

(defn- calculate-weighted-scenarios [concurrency number-of-requests scenarios]
  (let [weights            (map #(or (:weight %) 1) scenarios)
        requests           (weighted weights number-of-requests)
        concurrencies      (weighted weights concurrency)]
    (map #(assoc %1 :concurrency %2 :number-of-requests %3)
          scenarios
          concurrencies
          requests)))

(defn scenarios->runnable-scenarios [scenarios users & [options]]
  (let [requests (or (:requests options) (* users (distinct-request-count scenarios)))
        duration (:duration options)
        step-timeout (or (:timeout-in-ms options) 5000)
        runner (fn [scenario] (if (nil? duration)
                                (FixedRequestNumberRunner. scenario)
                                (DurationRunner. (assoc scenario :duration duration))))
        runnable-scenarios (calculate-weighted-scenarios users requests scenarios)]
    (map #(assoc % :runner (runner %)) runnable-scenarios)))

(ns clj-gatling.scenario-parser
  (:import [clj_gatling.scenario_runners FixedRequestNumberRunner DurationRunner]))

(defn- distinct-request-count [scenarios]
  (reduce + (map #(count (:requests %)) scenarios)))

(defn- weighted [weights value]
  (let [sum-of-weights (reduce + weights)]
    (map #(max 1 (Math/round (double (* value (/ % sum-of-weights))))) weights)))

(defn weighted-scenarios [concurrency scenarios]
  (let [weights            (map #(or (:weight %) 1) scenarios)
        concurrencies      (weighted weights concurrency)]
    (map #(assoc %1 :concurrency %2)
          scenarios
          concurrencies)))

(defn choose-runner [scenarios users options]
  (let [duration (:duration options)
        requests (or (:requests options) (* users (distinct-request-count scenarios)))]
    (if (nil? duration)
      (FixedRequestNumberRunner. requests)
      (DurationRunner. duration))))

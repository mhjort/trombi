(ns metrics-simulation.core
  (:require [trombi.core :as trombi]
            [trombi.reporters.raw-reporter :as raw-reporter]
            [metrics-simulation.simulations :refer [simulations]])
  (:import (java.time Duration))
  (:gen-class))

(defn- ramp-up-distribution [percentage-at _]
  (cond
    (< percentage-at 0.1) 0.1
    (< percentage-at 0.2) 0.2
    :else 1.0))

(defn- map-vals [f m]
  (reduce-kv #(assoc %1 %2 (f %3)) {} m))

(defn- update-result [start end previous]
  (let [request-time (- end start)]
    (if previous
      (-> previous
          (update :scenario-count inc)
          (update :scenario-time-sum + request-time))
      {:scenario-count 1 :scenario-time-sum request-time})))

(defn- calculate-scenario-averages [data]
  (let [scenario-counts (reduce (fn [m {:keys [start end] :as scenario}]
                                  (let [scenario-name (:name scenario)]
                                    (update m scenario-name (partial update-result start end))))
                                {}
                                data)]
    (map-vals (fn [{:keys [scenario-count scenario-time-sum]}]
                (long (/ scenario-time-sum scenario-count)))
              scenario-counts)))

(defn -main [simulation users-or-rate requests-or-duration & [option]]
  (let [simulation (or ((keyword simulation) simulations)
                       (throw (Exception. (str "No such simulation " simulation))))]
    (condp = option
      "--with-duration" (trombi/run simulation
                                     {:concurrency (read-string users-or-rate)
                                      :concurrency-distribution ramp-up-distribution
                                      :duration (Duration/ofSeconds (read-string requests-or-duration))})
      "--with-rate" (trombi/run simulation
                                 {:rate (read-string users-or-rate)
                                  :requests (read-string requests-or-duration)})
      "--no-report" (trombi/run simulation
                                 {:concurrency (read-string users-or-rate)
                                  :concurrency-distribution ramp-up-distribution
                                  :reporter {:writer (fn [_ _ _])
                                             :generator (fn [simulation]
                                                          (println "Ran" simulation "without report"))}
                                  :requests (read-string requests-or-duration)})
      "--raw-report" (println "Scenario averages calculated from raw report:"
                              (calculate-scenario-averages (:raw (trombi/run simulation
                                                                              {:concurrency (read-string users-or-rate)
                                                                               :concurrency-distribution ramp-up-distribution
                                                                               :reporters [raw-reporter/file-reporter]
                                                                               :requests (read-string requests-or-duration)}))))
      "--ramp-up" (trombi/run simulation
                               {:concurrency (read-string users-or-rate)
                                :concurrency-distribution ramp-up-distribution
                                :root "tmp"
                                :requests (read-string requests-or-duration)})
      (let [result (trombi/run simulation
                   {:concurrency (read-string users-or-rate)
                    :root "tmp"
                    :experimental-test-runner-stats? true
                    :requests (read-string requests-or-duration)})]
        (println "Returned: " result)))))

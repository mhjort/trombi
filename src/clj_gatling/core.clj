(ns clj-gatling.core
  (:require [trombi.core :as trombi]
            [trombi.report :as report]
            [trombi.simulation :as simulation]
            [trombi.progress-tracker :as progress-tracker]
            [trombi-gatling-highcharts-reporter.core :as highcharts]
            [trombi-gatling-highcharts-reporter.reporter :refer [csv-writer]]
            [trombi-gatling-highcharts-reporter.generator :refer [create-chart]]
            [trombi.legacy-util :refer [legacy-scenarios->scenarios]]
            [trombi.simulation-util :refer [create-dir
                                                 path-join
                                                 weighted-scenarios
                                                 choose-runner
                                                 create-report-name]])
  (:import (java.time ZonedDateTime)))

(def buffer-size 20000)

(defn- create-results-dir
  ([root] (create-results-dir root nil))
  ([root simulation-name]
   (let [results-dir (path-join root (create-report-name simulation-name))]
     (create-dir (path-join results-dir "input"))
     results-dir)))

;;Legacy function for running tests with old format (pre 0.8)
(defn run-simulation [legacy-scenarios concurrency & [options]]
  (let [start-time (ZonedDateTime/now)
        results-dir (create-results-dir (or (:root options) "target/results"))
        step-timeout (or (:timeout-in-ms options) 5000)
        scenarios (legacy-scenarios->scenarios legacy-scenarios)
        {:keys [results]} (simulation/run-scenarios {:runner (choose-runner scenarios concurrency options)
                                                     :timeout-in-ms step-timeout
                                                     :concurrency concurrency
                                                     :context (:context options)
                                                     :error-file (or (:error-file options)
                                                                     (path-join results-dir "error.log"))
                                                     :progress-tracker (progress-tracker/create-console-progress-tracker)}
                                                    (weighted-scenarios (range concurrency) scenarios))
        summary (report/create-result-lines {:name "Simulation" :scenarios scenarios}
                                            buffer-size
                                            results
                                            (partial csv-writer
                                                     (path-join results-dir "input")
                                                     start-time))]
    (create-chart results-dir)
    (println (str "Open " results-dir "/index.html"))
    summary))

;clj-gatling used to have both short summary and highcharts reporters as default reporters
;Trombi has only short summary as a default
(defn- add-backwards-compatible-reporters [options]
  (let [final-reporters (when-not (:reporters options)
                          (concat trombi/default-reporters
                                  [highcharts/reporter]))
        final-options (assoc options :reporters final-reporters)]
    final-options))

(defn run [simulation options]
  (trombi/run simulation (add-backwards-compatible-reporters options)))

(defn run-async [simulation options]
  (trombi/run-async simulation (add-backwards-compatible-reporters options)))

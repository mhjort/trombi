(ns clj-gatling.core
  (:import (java.time LocalDateTime))
  (:require [clojider-gatling-highcharts-reporter.core :refer [gatling-highcharts-reporter]]
            [clojider-gatling-highcharts-reporter.reporter :refer [csv-writer]]
            [clojider-gatling-highcharts-reporter.generator :refer [create-chart]]
            [clj-gatling.report :as report]
            [clj-gatling.reporters.short-summary :as short-summary]
            [clj-gatling.schema :as schema]
            [clj-gatling.progress-tracker :as progress-tracker]
            [schema.core :refer [validate]]
            [clj-gatling.pipeline :as pipeline]
            [clj-gatling.legacy-util :refer [legacy-scenarios->scenarios
                                             legacy-reporter->reporter]]
            [clj-gatling.simulation-util :refer [create-dir
                                                 path-join
                                                 weighted-scenarios
                                                 eval-if-needed
                                                 choose-runner
                                                 create-report-name]]
            [clj-gatling.simulation :as simulation]))

(def buffer-size 20000)

(defn- create-results-dir
  ([root] (create-results-dir root nil))
  ([root simulation-name]
   (let [results-dir (path-join root (create-report-name simulation-name))]
     (create-dir (path-join results-dir "input"))
     results-dir)))

;; Legacy function for running tests with old format (pre 0.8)
(defn run-simulation [legacy-scenarios concurrency & [options]]
  (let [start-time (LocalDateTime/now)
        results-dir (create-results-dir (or (:root options) "target/results"))
        step-timeout (or (:timeout-in-ms options) 5000)
        scenarios (legacy-scenarios->scenarios legacy-scenarios)
        result (simulation/run-scenarios {:runner (choose-runner scenarios concurrency options)
                                          :timeout-in-ms step-timeout
                                          :context (:context options)
                                          :error-file (or (:error-file options)
                                                          (path-join results-dir "error.log"))
                                          :progress-tracker (progress-tracker/create-console-progress-tracker)}
                                         (weighted-scenarios (range concurrency) scenarios))
        summary (report/create-result-lines {:name "Simulation" :scenarios scenarios}
                                            buffer-size
                                            result
                                            (partial csv-writer
                                                     (path-join results-dir "input")
                                                     start-time))]
    (create-chart results-dir)
    (println (str "Open " results-dir "/index.html"))
    summary))

(defn- create-reporters [reporter results-dir simulation]
  (let [r (if reporter
            (do
              (println "Warn! :reporter option is deprecated. Use :reporters instead")
              (legacy-reporter->reporter :custom
                                         reporter
                                         simulation))
            (legacy-reporter->reporter :highcharts
                                       (gatling-highcharts-reporter results-dir)
                                       simulation))]
    [short-summary/reporter r]))

(defn run [simulation {:keys [concurrency concurrency-distribution rate rate-distribution root
                              timeout-in-ms context requests duration reporter reporters error-file
                              executor nodes progress-tracker] :as options
                       :or {concurrency 1
                            root "target/results"
                            executor pipeline/local-executor
                            nodes 1
                            progress-tracker (progress-tracker/create-console-progress-tracker)
                            timeout-in-ms 5000
                            context {}}}]
  (validate schema/Options options)
  (let [simulation-name (:name (eval-if-needed simulation))
        results-dir (create-results-dir root simulation-name)
        multiple-reporters? (not (nil? reporters))
        reporters (or reporters
                      (create-reporters reporter results-dir simulation))
        summary (pipeline/run simulation (assoc options
                                                :concurrency concurrency
                                                :concurrency-distribution concurrency-distribution
                                                :rate rate
                                                :rate-distribution rate-distribution
                                                :timeout-in-ms timeout-in-ms
                                                :context context
                                                :executor executor
                                                :progress-tracker progress-tracker
                                                :reporters reporters
                                                :results-dir results-dir
                                                :nodes nodes
                                                :batch-size buffer-size
                                                :requests requests
                                                :error-file (or error-file
                                                                (path-join results-dir "error.log"))
                                                :duration duration))]
    (println "Simulation" simulation-name "finished.")
    (if multiple-reporters?
      summary
      (:short summary))))

(ns trombi.core
  (:import (java.time ZonedDateTime))
  (:require [clojider-gatling-highcharts-reporter.core :refer [gatling-highcharts-reporter]]
            [clojider-gatling-highcharts-reporter.reporter :refer [csv-writer]]
            [clojider-gatling-highcharts-reporter.generator :refer [create-chart]]
            [trombi.report :as report]
            [trombi.reporters.short-summary :as short-summary]
            [trombi.schema :as schema]
            [trombi.progress-tracker :as progress-tracker]
            [schema.core :refer [validate]]
            [trombi.pipeline :as pipeline]
            [trombi.legacy-util :refer [legacy-scenarios->scenarios
                                             legacy-reporter->reporter]]
            [trombi.simulation-util :refer [create-dir
                                                 path-join
                                                 weighted-scenarios
                                                 eval-if-needed
                                                 choose-runner
                                                 create-report-name]]
            [trombi.simulation :as simulation]
            [clojure.core.async :refer [thread]]))

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

(defn- run-with-pipeline [simulation {:keys [concurrency concurrency-distribution rate rate-distribution root
                                             timeout-in-ms context requests duration reporter reporters error-file
                                             executor nodes progress-tracker experimental-test-runner-stats?] :as options
                                      :or {concurrency 1
                                           root "target/results"
                                           executor pipeline/local-executor
                                           nodes 1
                                           timeout-in-ms 5000
                                           context {}
                                           experimental-test-runner-stats? false}}]
  (validate schema/Options options)
  (let [simulation-name (:name (eval-if-needed simulation))
        results-dir (create-results-dir root simulation-name)
        default-progress-tracker (progress-tracker/create-console-progress-tracker)
        reporters (or reporters
                      (create-reporters reporter results-dir simulation))]
    (pipeline/run simulation (assoc options
                                    :concurrency concurrency
                                    :concurrency-distribution concurrency-distribution
                                    :rate rate
                                    :rate-distribution rate-distribution
                                    :timeout-in-ms timeout-in-ms
                                    :context context
                                    :executor executor
                                    :progress-tracker (or progress-tracker default-progress-tracker)
                                    :default-progress-tracker default-progress-tracker
                                    :reporters reporters
                                    :results-dir results-dir
                                    :nodes nodes
                                    :batch-size buffer-size
                                    :requests requests
                                    :error-file (or error-file
                                                    (path-join results-dir "error.log"))
                                    :duration duration
                                    :experimental-test-runner-stats? experimental-test-runner-stats?))))

(defn run [simulation {:keys [reporters] :as options}]
  (let [multiple-reporters? (not (nil? reporters))
        {:keys [summary]} (run-with-pipeline simulation options)]
    (if multiple-reporters?
      @summary
      (:short @summary))))

(defn run-async [simulation {:keys [reporters] :as options}]
  (let [multiple-reporters? (not (nil? reporters))
        {:keys [summary force-stop-fn]} (run-with-pipeline simulation options)]
    (if multiple-reporters?
      {:results summary :force-stop-fn force-stop-fn}
      (let [results (promise)]
        (thread
          (deliver results (:short @summary)))
        {:results results :force-stop-fn force-stop-fn}))))

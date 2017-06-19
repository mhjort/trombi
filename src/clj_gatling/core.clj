(ns clj-gatling.core
  (:import [org.joda.time LocalDateTime])
  (:require [clojider-gatling-highcharts-reporter.core :refer [gatling-highcharts-reporter]]
            [clojider-gatling-highcharts-reporter.reporter :refer [csv-writer]]
            [clojider-gatling-highcharts-reporter.generator :refer [create-chart]]
            [clj-gatling.report :as report]
            [clj-gatling.legacy-util :refer [legacy-scenarios->scenarios]]
            [clj-gatling.simulation-util :refer [create-dir
                                                 path-join
                                                 weighted-scenarios
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

;Legacy function for running tests with old format (pre 0.8)
(defn run-simulation [legacy-scenarios concurrency & [options]]
  (let [start-time (LocalDateTime.)
        results-dir (create-results-dir (or (:root options) "target/results"))
        step-timeout (or (:timeout-in-ms options) 5000)
        scenarios (legacy-scenarios->scenarios legacy-scenarios)
        result (simulation/run-scenarios {:runner (choose-runner scenarios concurrency options)
                                          :timeout-in-ms step-timeout
                                          :context (:context options)
                                          :error-file (or (:error-file options)
                                                          (path-join results-dir "error.log"))}
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

(defn run [simulation {:keys [concurrency concurrency-distribution root timeout-in-ms context
                              requests duration reporter error-file]
                       :or {concurrency 1
                            root "target/results"
                            timeout-in-ms 5000
                            context {}}}]
  (let [results-dir (create-results-dir root (:name simulation))
        reporter (or reporter (gatling-highcharts-reporter results-dir))
        result (simulation/run simulation {:concurrency concurrency
                                           :concurrency-distribution concurrency-distribution
                                           :timeout-in-ms timeout-in-ms
                                           :context context
                                           :requests requests
                                           :error-file (or error-file
                                                           (path-join results-dir "error.log"))
                                           :duration duration})
        summary (report/create-result-lines simulation
                                            buffer-size
                                            result
                                            (:writer reporter))]
    ((:generator reporter) simulation)
    (println "Simulation" (:name simulation) "finished.")
    summary))

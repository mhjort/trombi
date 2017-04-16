(ns clj-gatling.core
  (:import [org.joda.time LocalDateTime])
  (:require [clojider-gatling-highcharts-reporter.core :refer [csv-writer
                                                               create-chart
                                                               gatling-highcharts-reporter]]
            [clj-gatling.report :as report]
            [clj-gatling.simulation-util :refer [create-dir
                                                 path-join
                                                 weighted-scenarios
                                                 choose-runner
                                                 timestamp-str]]
            [clj-gatling.simulation :as simulation]))

(def buffer-size 20000)

(defn- create-results-dir [root]
  (let [results-dir (path-join root (timestamp-str))]
    (create-dir (path-join results-dir "input"))
    results-dir))

;Legacy function for running tests with old format (pre 0.8)
(defn run-simulation [scenarios concurrency & [options]]
  (let [start-time (LocalDateTime.)
        results-dir (create-results-dir (or (:root options) "target/results"))
        step-timeout (or (:timeout-in-ms options) 5000)
        result (simulation/run-scenarios {:runner (choose-runner scenarios concurrency options)
                                          :timeout-in-ms step-timeout
                                          :context (:context options)
                                          :error-file (or (:error-file options)
                                                          (path-join results-dir "error.log"))}
                                         (weighted-scenarios (range concurrency) scenarios)
                                         true)]
    (let [summary (report/create-result-lines start-time
                                              buffer-size
                                              result
                                              (partial csv-writer
                                                       (path-join results-dir "input")
                                                       (LocalDateTime.)))]
      (create-chart results-dir)
      (println (str "Open " results-dir "/index.html"))
      summary)))

(defn run [simulation {:keys [concurrency root timeout-in-ms context
                              requests duration reporter error-file]
                       :or {concurrency 1
                            root "target/results"
                            timeout-in-ms 5000
                            context {}}}]
  (let [results-dir (create-results-dir root)
        reporter (or reporter (gatling-highcharts-reporter results-dir))
        result (simulation/run simulation {:concurrency concurrency
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

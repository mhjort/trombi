(ns clj-gatling.core
  (:import [org.joda.time LocalDateTime])
  (:require [clj-gatling.chart :as chart]
            [clj-gatling.report :as report]
            [clj-gatling.simulation-util :refer [create-dir
                                                 weighted-scenarios
                                                 choose-runner
                                                 timestamp-str]]
            [clj-gatling.simulation :as simulation]))

(def buffer-size 20000)

(defn- gatling-highcharts-reporter [results-dir]
  (let [start-time (LocalDateTime.)]
    {:writer (partial report/gatling-csv-writer results-dir start-time)
     :generator #(do
                   (chart/create-chart results-dir)
                   (println (str "Open file://" results-dir "/index.html")))}))

(defn- create-results-dir [root]
  (let [results-dir (str root "/" (timestamp-str))]
    (create-dir (str results-dir "/input"))
    results-dir))

(defn run-simulation [scenarios concurrency & [options]]
  (let [start-time (LocalDateTime.)
        results-dir (create-results-dir (or (:root options) "target/results"))
        step-timeout (or (:timeout-in-ms options) 5000)
        result (simulation/run-scenarios {:runner (choose-runner scenarios concurrency options)
                                          :timeout-in-ms step-timeout
                                          :context (:context options)}
                                         (weighted-scenarios (range concurrency) scenarios)
                                         true)]
    (let [summary (report/create-result-lines start-time
                                              buffer-size
                                              result
                                              (partial report/gatling-csv-writer
                                                       (LocalDateTime.)
                                                       (str results-dir "/input")))]
      (chart/create-chart results-dir)
      (println (str "Open " results-dir "/index.html"))
      summary)))

(defn run [simulation {:keys [concurrency root timeout-in-ms context requests duration reporter]
                       :or {concurrency 1
                            root "target/results"
                            reporter (gatling-highcharts-reporter root)
                            timeout-in-ms 5000
                            context {}}}]
  (let [results-dir (create-results-dir root)
        result (simulation/run simulation {:concurrency concurrency
                                           :timeout-in-ms timeout-in-ms
                                           :context context
                                           :requests requests
                                           :duration duration})
        summary (report/create-result-lines buffer-size
                                            result
                                            (:writer reporter))]
    ((:generator reporter))
    (println "Simulation" (:name simulation) "finished.")
    summary))

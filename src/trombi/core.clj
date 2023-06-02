(ns trombi.core
  (:require [trombi.reporters.short-summary :as short-summary]
            [trombi.schema :as schema]
            [trombi.progress-tracker :as progress-tracker]
            [schema.core :refer [validate]]
            [trombi.pipeline :as pipeline]
            [trombi.legacy-util :refer [legacy-reporter->reporter]]
            [trombi.simulation-util :refer [create-dir
                                                 path-join
                                                 eval-if-needed
                                                 create-report-name]]
            [clojure.core.async :refer [thread]]))

(def buffer-size 20000)

(defn- create-results-dir
  ([root] (create-results-dir root nil))
  ([root simulation-name]
   (let [results-dir (path-join root (create-report-name simulation-name))]
     (create-dir (path-join results-dir "input"))
     results-dir)))

(def default-reporters [short-summary/reporter])

(defn- parse-deprecated-reporter-option [reporter simulation]
  (when reporter
    (println "Warn! :reporter option is deprecated. Use :reporters instead")
    (legacy-reporter->reporter :custom
                               reporter
                               simulation)))

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
                      (concat default-reporters (parse-deprecated-reporter-option reporter simulation)))]
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

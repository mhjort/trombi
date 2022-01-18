(ns clj-gatling.test-helpers
  (:require [clojure.core.async :refer [<!!]]
            [clj-gatling.simulation :as simulation]
            [clj-gatling.schema :as schema]
            [schema.core :refer [validate]]
            [clj-gatling.legacy-util :refer [legacy-scenarios->scenarios]]
            [clj-gatling.simulation-util :refer [choose-runner
                                                 weighted-scenarios
                                                 create-dir]]
            [clojure.java.io :as io]))

(defn- to-vector [channel]
  (loop [results []]
    (if-let [result (<!! channel)]
      (recur (conj results result))
      results)))

(def error-file-path "target/test-results/error.log")

(defn setup-error-file-path [f]
  (let [file (io/file error-file-path)]
    (when (not (.exists file))
      (create-dir (.getParent file))))
  (f))

(defn delete-error-logs []
  (when (.exists (io/file error-file-path))
    (io/delete-file error-file-path)))

(defn run-legacy-simulation [legacy-scenarios concurrency & [options]]
  (let [step-timeout (or (:timeout-in-ms options) 5000)
        scenarios (legacy-scenarios->scenarios legacy-scenarios)]
    (-> (simulation/run-scenarios {:runner (choose-runner scenarios concurrency options)
                                   :timeout-in-ms step-timeout
                                   :context (:context options)
                                   :error-file error-file-path
                                   :progress-tracker (fn [_])}
                                  (weighted-scenarios (range concurrency) scenarios))
        to-vector)))

(defn run-single-scenario [scenario & {:keys [concurrency
                                              concurrency-distribution
                                              rate
                                              rate-distribution
                                              context
                                              timeout-in-ms
                                              requests
                                              duration
                                              users
                                              progress-tracker
                                              pre-hook
                                              post-hook]
                                       :or {timeout-in-ms 5000
                                            progress-tracker (fn [_])}}]
  (to-vector (simulation/run {:name "Simulation"
                              :pre-hook pre-hook
                              :post-hook post-hook
                              :scenarios [scenario]}
                             {:concurrency concurrency
                              :concurrency-distribution concurrency-distribution
                              :rate rate
                              :rate-distribution rate-distribution
                              :timeout-in-ms timeout-in-ms
                              :requests requests
                              :duration duration
                              :users users
                              :context context
                              :error-file error-file-path
                              :progress-tracker progress-tracker})))

(defn run-two-scenarios [scenario1 scenario2 & {:keys [concurrency requests]}]
  (to-vector (simulation/run {:name "Simulation"
                              :scenarios [scenario1 scenario2]}
                             {:concurrency concurrency
                              :requests requests
                              :timeout-in-ms 5000
                              :error-file error-file-path
                              :progress-tracker (fn [_])})))

(defn successful-request [cb context]
  ;;TODO Try to find a better way for this
  ;;This is required so that multiple scenarios start roughly at the same time
  (Thread/sleep 50)
  (cb true (assoc context :to-next-request true)))

(defn failing-request [cb context] (cb false))

(defn fake-async-http [url callback context]
  (future (Thread/sleep 50)
          (callback (= "success" url))))

(defn step [step-name return]
  {:name step-name
   :request (fn [ctx]
              ;;Note! Highcharts reporter fails if start and end times are exactly the same values
              (Thread/sleep (inc (rand-int 2)))
              [return (assoc ctx :to-next-request return)])})

(defn throwing-step [step-name]
  {:name step-name
   :request (fn [_] (throw (Exception. "Simulated")))})

(defn get-result [requests request-name]
  (:result (first (filter #(= request-name (:name %)) requests))))

(defn stub-reporter [reporter-key]
  {:reporter-key reporter-key
   :collector (fn [input]
                (validate schema/CollectorInput input)
                {:collect  (fn [_ batch] [1])
                 :combine concat})
   :generator (fn [input]
                (validate schema/GeneratorInput input)
                {:generate (partial reduce +)
                 :as-str str})})

(def a-reporter
  (stub-reporter :a))

(def b-reporter
  (stub-reporter :b))

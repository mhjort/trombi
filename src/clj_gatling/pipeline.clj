(ns clj-gatling.pipeline
  (:require [clj-gatling.report :refer [combine-with-reporters
                                        generate-with-reporters
                                        as-str-with-reporters
                                        parse-in-batches]]
            [clj-gatling.simulation :as simu]
            [clj-gatling.simulation-util :refer [eval-if-needed
                                                 split-equally
                                                 split-number-equally]]
            [clojure.string :as string]
            [clojure.core.async :refer [thread <!!]]))

(defn- init-report-generators [reporters results-dir context]
  (map (fn [{:keys [reporter-key generator]}]
         (let [generator-creator (eval-if-needed generator)]
           (assoc (generator-creator {:results-dir results-dir
                                      :context context})
                  :reporter-key reporter-key)))
       reporters))

(defn- init-report-collectors [reporters results-dir context]
  (map (fn [{:keys [reporter-key collector]}]
         (let [collector-creator (eval-if-needed collector)]
           (assoc (collector-creator {:results-dir results-dir
                                      :context context})
                  :reporter-key reporter-key)))
       reporters))

(defn simulation-runner [simulation {:keys [node-id
                                            batch-size
                                            reporters
                                            results-dir
                                            context] :as options}]
  (let [evaluated-simulation (eval-if-needed simulation)
        results (simu/run evaluated-simulation options)
        report-collectors (init-report-collectors reporters results-dir context)
        raw-summary (parse-in-batches evaluated-simulation node-id batch-size results report-collectors)]
    raw-summary))

(defn local-executor [node-id simulation options]
  (println "Starting local executor with id:" node-id)
  (simulation-runner simulation options))

(defn prun [f users-by-node requests-by-node]
  (let [results (loop [users-by-node users-by-node
                       requests-by-node requests-by-node
                       threads []]
                  (if-let [users (first users-by-node)]
                    (let [t (thread (f (count threads) users (first requests-by-node)))]
                      (recur (rest users-by-node) (rest requests-by-node) (conj threads t)))
                    threads))]
    (map #(<!! %) results)))

(defn- assoc-if-not-nil [m k v]
  (if v
    (assoc m k v)
    m))

(defn max-users
  "Determines the maximum number of concurrent requests that can be running,
  given the rate at which they are created and time out. Rounds up"
  [rate timeout-in-ms]
  (-> timeout-in-ms
      (/ 1000) ;; To get timeout in s, as rate is per sec
      (* rate)
      (Math/ceil)
      (int)))

(defn run [simulation
           {:keys [nodes
                   executor
                   concurrency
                   rate
                   timeout-in-ms
                   reporters
                   requests
                   results-dir
                   context] :as options}]
  (let [users-by-node (if rate
                        (split-equally nodes (range (max-users rate timeout-in-ms)))
                        (split-equally nodes (range concurrency)))
        requests-by-node (when requests
                           (split-number-equally nodes requests))
        report-generators (init-report-generators reporters results-dir context)
        report-collectors (init-report-collectors reporters results-dir context)
        results-by-node (prun (fn [node-id users requests]
                                (executor node-id
                                          simulation
                                          (-> options
                                              (dissoc :executor)
                                              (assoc :users users)
                                              (assoc :node-id node-id)
                                              (assoc-if-not-nil :requests requests))))
                              users-by-node
                              requests-by-node)
        result (reduce (partial combine-with-reporters report-collectors)
                       results-by-node)
        summary (generate-with-reporters report-generators result)]
    (println (string/join "\n" (as-str-with-reporters report-generators summary)))
    summary))

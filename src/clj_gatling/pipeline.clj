(ns clj-gatling.pipeline
  (:require [clj-gatling.report :refer [combine-with-reporters
                                        generate-with-reporters
                                        parse-in-batches
                                        short-summary-reporter]]
            [clj-gatling.simulation :as simu]
            [clj-gatling.simulation-util :refer [split-equally
                                                 split-number-equally]]
            [clojure.set :refer [rename-keys]]
            [clojure.core.async :refer [thread <!!]]))

(defn local-executor [node-id simulation-fn simulation options]
  (println "Starting local executor with id:" node-id)
  (simulation-fn simulation options))

(defn- simulation-runner [simulation {:keys [batch-size reporters] :as options}]
  (let [results (simu/run simulation options)
        raw-summary (parse-in-batches simulation batch-size results reporters)]
    raw-summary))

(defn prun [f users-by-node requests-by-node]
  (let [results (loop [users-by-node users-by-node
                       request-by-node requests-by-node
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

(defn run [simulation
           {:keys [nodes executor concurrency reporters requests] :as options}]
  (let [users-by-node (split-equally nodes (range concurrency))
        requests-by-node (when requests
                           (split-number-equally nodes requests))
        results-by-node (prun (fn [node-id users requests]
                                (executor node-id
                                          simulation-runner
                                          simulation (-> options
                                                         (assoc :users users)
                                                         (assoc-if-not-nil :requests requests))))
                              users-by-node
                              requests-by-node)
        result (reduce (partial combine-with-reporters reporters)
            results-by-node)]
    (generate-with-reporters reporters result)))

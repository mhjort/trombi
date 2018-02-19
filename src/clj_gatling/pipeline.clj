(ns clj-gatling.pipeline
  (:require [clj-gatling.report :refer [combine-with-reporters
                                        parse-in-batches
                                        short-summary-reporter]]
            [clj-gatling.simulation :as simu]
            [clj-gatling.simulation-util :refer [split-equally]]
            [clojure.set :refer [rename-keys]]
            [clojure.core.async :refer [thread <!!]]))

(defn local-executor [node-id simulation-fn simulation options]
  (println "Starting local executor with id:" node-id)
  (simulation-fn simulation options))

(defn- simulation-runner [simulation {:keys [batch-size reporters] :as options}]
  (let [results (simu/run simulation options)
        raw-summary (parse-in-batches simulation batch-size results reporters)]
    raw-summary))

(defn prun [f users-by-node]
  (let [results (loop [users-by-node users-by-node
                       threads []]
                  (if-let [users (first users-by-node)]
                    (let [t (thread (f (count threads) users))]
                      (recur (rest users-by-node) (conj threads t)))
                    threads))]
    (map #(<!! %) results)))

(defn run [simulation
           {:keys [nodes executor concurrency reporters] :as options}]
  (let [users-by-node (split-equally nodes (range concurrency))
        results-by-node (prun (fn [node-id users]
                                (executor node-id
                                          simulation-runner
                                          simulation (assoc options :users users)))
                              users-by-node)]
    (reduce (partial combine-with-reporters reporters)
            results-by-node)))

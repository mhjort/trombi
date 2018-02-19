(ns clj-gatling.pipeline
  (:require [clj-gatling.report :refer [combine-with-reporters
                                        parse-in-batches
                                        short-summary-reporter]]
            [clj-gatling.simulation :as simu]
            [clj-gatling.simulation-util :refer [split-equally]]
            [clojure.set :refer [rename-keys]]
            [clojure.core.async :refer [onto-chan to-chan chan go go-loop <!!]]))

(defn local-executor [node-id simulation-fn simulation options]
  (println "Starting local executor with id:" node-id)
  (simulation-fn simulation options))

(defn- simulation-runner [simulation {:keys [batch-size reporters] :as options}]
  (let [results (simu/run simulation options)
        raw-summary (parse-in-batches simulation batch-size results reporters)]
    raw-summary))

(defn run [simulation
           {:keys [nodes executor concurrency reporters] :as options}]
  (let [users-by-node (split-equally nodes (range concurrency))
        results-by-node (pmap #(executor %2
                                         simulation-runner
                                         simulation
                                         (assoc options :users %1))
                              users-by-node
                              (range nodes))]
    (reduce (partial combine-with-reporters reporters)
            results-by-node)))

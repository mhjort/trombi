(ns clj-gatling.pipeline
  (:require [clj-gatling.report :refer [combine-with-reporters
                                        generate-with-reporters
                                        parse-in-batches
                                        short-summary-reporter]]
            [clj-gatling.simulation :as simu]
            [clj-gatling.simulation-util :refer [eval-if-needed
                                                 split-equally
                                                 split-number-equally]]
            [clojure.set :refer [rename-keys]]
            [clojure.core.async :refer [thread <!!]]))

(defn init-reporters [reporters results-dir context]
  (map (fn [reporter]
         (let [reporter-creator (eval-if-needed reporter)]
           (reporter-creator {:results-dir results-dir
                              :context context})))
       reporters))

(defn simulation-runner [simulation {:keys [node-id
                                            batch-size
                                            reporters
                                            initialized-reporters
                                            results-dir
                                            context] :as options}]
  (let [evaluated-simulation (eval-if-needed simulation)
        results (simu/run evaluated-simulation options)
        initialized-reporters (init-reporters reporters results-dir context)
        raw-summary (parse-in-batches evaluated-simulation node-id batch-size results initialized-reporters)]
    raw-summary))

(defn local-executor [node-id simulation options]
  (println "Starting local executor with id:" node-id)
  (simulation-runner simulation options))

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
           {:keys [nodes
                   executor
                   concurrency
                   reporters
                   initialized-reporters
                   requests
                   results-dir
                   context] :as options}]
  (let [users-by-node (split-equally nodes (range concurrency))
        requests-by-node (when requests
                           (split-number-equally nodes requests))
        initialized-reporters (init-reporters reporters results-dir context)
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
        result (reduce (partial combine-with-reporters initialized-reporters)
                       results-by-node)]
    (generate-with-reporters initialized-reporters result)))

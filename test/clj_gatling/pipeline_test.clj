(ns clj-gatling.pipeline-test
  (:require [clojure.test :refer :all]
            [clj-gatling.test-helpers :refer :all]
            [clj-gatling.report :refer [short-summary-reporter]]
            [clj-gatling.pipeline :as pipeline]))

(def test-simu
  {:name "Test simulation"
   :scenarios [{:name "Test scenario"
                :steps [{:name "Step1" :request (fn [_] true)}
                        {:name "Step2" :request (fn [_] false)}]}]})

(defn- stub-executor [node-ids]
  (fn [node-id simulation-fn simulation options]
    (swap! node-ids conj node-id)
    (simulation-fn simulation options)))

(deftest running-pipeline
  (let [node-ids (atom #{})
        executor (stub-executor node-ids)]
    (pipeline/run test-simu {:executor executor
                             :nodes 3
                             :concurrency 20
                             :timeout-in-ms 1000
                             :batch-size 5
                             :reporters [short-summary-reporter]})
    (is (= #{0 1 2} @node-ids))))

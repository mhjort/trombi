(ns clj-gatling.pipeline-test
  (:require [clojure.test :refer :all]
            [clj-gatling.test-helpers :refer :all]
            [clj-containment-matchers.clojure-test :refer :all]
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
        executor (stub-executor node-ids)
        reporters [(stub-reporter :a)
                   (stub-reporter :b)]
        summary (pipeline/run test-simu {:executor executor
                                         :nodes 3
                                         :concurrency 5
                                         :requests 25
                                         :timeout-in-ms 1000
                                         :batch-size 10
                                         :reporters reporters})]
    ;Stub reporter returns number of batches parsed per reporter
    (is (equal? summary {:a 3 :b 3}))
    (is (= #{0 1 2} @node-ids))))

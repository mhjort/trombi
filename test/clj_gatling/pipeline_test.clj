(ns clj-gatling.pipeline-test
  (:require [clojure.test :refer [deftest is]]
            [clj-gatling.test-helpers :as th]
            [clj-gatling.pipeline :as pipeline]))

(deftest max-users
  (is (= 48000 (pipeline/max-users 800 60000)))
  (is (= 500 (pipeline/max-users 100 5000)))
  (is (= 120 (pipeline/max-users 4 30000)))
  (is (= 12 (pipeline/max-users 10 1200)))
  (is (= 3 (pipeline/max-users 2 1200))))

(defn- stub-executor [node-ids]
  (fn [node-id simulation options]
    (swap! node-ids conj node-id)
    (pipeline/simulation-runner simulation options)))

(deftest running-pipeline
  (let [node-ids (atom #{})
        executor (stub-executor node-ids)
        reporters [th/a-reporter
                   th/b-reporter]
        {:keys [summary force-stop-fn]} (pipeline/run 'clj-gatling.example/test-simu {:executor executor
                                                                                      :nodes 3
                                                                                      :context {}
                                                                                      :results-dir "tmp"
                                                                                      :concurrency 5
                                                                                      :requests 25
                                                                                      :timeout-in-ms 1000
                                                                                      :progress-tracker (fn [_])
                                                                                      :batch-size 10
                                                                                      :reporters reporters})]
    ;;Stub reporter returns number of batches parsed per reporter
    (force-stop-fn) ;;Makes sure that calling force-stop-fn does not throw an exception
    (is (= {:a 3 :b 3} @summary))
    (is (= #{0 1 2} @node-ids))))

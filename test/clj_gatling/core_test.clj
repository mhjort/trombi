(ns clj-gatling.core-test
  (:require [clojure.test :refer :all]
            [clj-gatling.test-helpers :as th]
            [clj-gatling.core :refer [run run-simulation]]))

(use-fixtures :once th/setup-error-file-path)

(def legacy-scenario
  {:name "Test scenario"
   :context {}
   :requests [{:name "Request1" :fn th/successful-request}
              {:name "Request2" :fn th/failing-request}]})

(defn- simulation [simu-name]
  {:name simu-name
   :scenarios [{:name "Test scenario"
                :steps [(th/step "Step1" true)
                        (th/step "Step2" false)]}]})

(deftest legacy-simulation-returns-summary
  (let [summary (run-simulation [legacy-scenario] 1 {})]
    (is (= {:ok 1 :ko 1} summary))))

(deftest simulation-returns-summary
  (let [summary (run (simulation "test-summary")
                     {:concurrency 1})]
    (is (= {:ok 1 :ko 1} summary))))

(deftest simulation-returns-summary-of-all-reporters
  (let [summary (run (simulation "test-all")
                     {:reporters [th/a-reporter th/b-reporter]
                      :concurrency 1})]
    (is (= summary {:a 1 :b 1}))))

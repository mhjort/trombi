(ns clj-gatling.core-test
  (:require [clojure.test :refer :all]
            [clj-gatling.test-helpers :refer :all]
            [clj-containment-matchers.clojure-test :refer :all]
            [clj-gatling.core :refer [run run-simulation]]
            [clj-async-test.core :refer :all]
            [clj-time.core :as time]))

(use-fixtures :once setup-error-file-path)

(def legacy-scenario
  {:name "Test scenario"
   :context {}
   :requests [{:name "Request1" :fn successful-request}
              {:name "Request2" :fn failing-request}]})

(defn- simulation [simu-name]
  {:name simu-name
   :scenarios [{:name "Test scenario"
                :steps [(step "Step1" true)
                        (step "Step2" false)]}]})

(deftest legacy-simulation-returns-summary
  (let [summary (run-simulation [legacy-scenario] 1 {})]
    (is (= {:ok 1 :ko 1} summary))))

(deftest simulation-returns-summary
  (let [summary (run (simulation "test-summary")
                     {
                      :concurrency 1})]
    (is (= {:ok 1 :ko 1} summary))))

(deftest simulation-returns-summary-of-all-reporters
  (let [summary (run (simulation "test-all")
                     {:reporters [(stub-reporter :a) (stub-reporter :b)]
                      :concurrency 1})]
    (is (equal? summary {:a 1 :b 1}))))

(ns clj-gatling.core-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [trombi.test-helpers :as th]
            [clj-gatling.core :refer [run-simulation]]))

(use-fixtures :once th/setup-error-file-path)

(def legacy-scenario
  {:name "Test scenario"
   :context {}
   :requests [{:name "Request1" :fn th/successful-request}
              {:name "Request2" :fn th/failing-request}]})

(deftest legacy-simulation-returns-summary
  (let [summary (run-simulation [legacy-scenario] 1 {})]
    (is (= {:ok 1 :ko 1} summary))))

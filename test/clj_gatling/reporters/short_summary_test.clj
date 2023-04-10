(ns clj-gatling.reporters.short-summary-test
  (:require [clojure.test :refer :all]
            [clj-gatling.simulation-util :refer [eval-if-needed]]
            [clj-gatling.reporters.short-summary :refer [reporter]]))

(def scenario-results
  [{:name "Test scenario" :id 1 :start 1391936496000 :end 1391936496100
    :requests [{:id 1 :name "Request1" :start 1391936496000 :end 1391936496020 :result true}
               {:id 1 :name "Request2" :start 1391936496030 :end 1391936496100 :result true}]}
   {:name "Test scenario" :id 0 :start 1391936496200 :end 1391936496400
    :requests [{:id 0 :name "Request1" :start 1391936496200 :end 1391936496300 :result true}
               {:id 0 :name "Request2" :start 1391936496320 :end 1391936496400 :result false}]}
   {:name "Test scenario2" :id 0 :start 1391936496000 :end 1391936496500
    :requests [{:id 0 :name "Request1" :start 1391936496000 :end 1391936496500 :result true}]}])

(defn- simulate-report-generation [{:keys [collect combine]} {:keys [generate as-str]}]
  (let [batches (partition 1 scenario-results)
        [collection1 collection2 collection3] (mapv #(collect {} {:batch %1 :node-id 0 :batch-id %2}) batches (range))
        first-combination (combine collection1 collection2)
        report (generate (combine first-combination collection3))]
    [report (as-str report)]))

(deftest short-summary-test
  (testing "short-summary is defined correctly"
    (is (= {:reporter-key :short
            :collector 'clj-gatling.reporters.short-summary/collector
            :generator 'clj-gatling.reporters.short-summary/generator}
           reporter))
  (let [collector ((eval-if-needed (:collector reporter)) {})
        generator ((eval-if-needed (:generator reporter)) {})]
      (is (= [{:ok 4 :ko 1 :response-time {:global {:min 20
                                                    :max 500
                                                    :mean 154}}}
              "Total number of requests: 5, successful: 4, failed: 1."]
             (simulate-report-generation collector generator))))))

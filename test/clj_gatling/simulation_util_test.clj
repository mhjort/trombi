(ns clj-gatling.simulation-util-test
  (:require [clojure.test :refer :all]
            [clj-gatling.test-helpers :refer :all]
            [clojure.test.check.properties :as prop]
            [clj-containment-matchers.clojure-test :refer :all]
            [clojure.test.check.clojure-test :refer[defspec]]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clj-gatling.simulation-util :as simulation-util]))

(def scenario-generator (gen/vector
                          (gen/hash-map :name gen/string-ascii
                                        :weight (gen/choose 1 100))
                          1 10))

(def all-user-ids-are-distributed
  (prop/for-all [users (gen/choose 10 5000)
                 scenarios scenario-generator]
                (= users
                   (count (mapcat :users
                                  (simulation-util/weighted-scenarios (range users) scenarios))))))

(def at-least-one-user-is-distributed-to-every-scenario
  (prop/for-all [users (gen/choose 10 5000)
                 scenarios scenario-generator]
                (every? pos? (map #(count (:users %))
                                  (simulation-util/weighted-scenarios (range users) scenarios)))))

(defspec splits-all-users-to-weighted-scenarios
  100
  all-user-ids-are-distributed)

(defspec at-least-one-user-is-distributed
  100
  at-least-one-user-is-distributed-to-every-scenario)

;;(tc/quick-check 100 splits-all-users-to-weighted-scenarios)
;;(tc/quick-check 100 all-user-ids-are-distributed)

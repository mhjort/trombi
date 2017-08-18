(ns clj-gatling.simulation-util-test
  (:require [clojure.test :refer :all]
            [clj-gatling.test-helpers :refer :all]
            [clojure.test.check.properties :as prop]
            [clj-containment-matchers.clojure-test :refer :all]
            [clojure.test.check.clojure-test :refer[defspec]]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clj-gatling.simulation-util :refer [weighted-scenarios]]))

(def scenario-generator (gen/vector
                          (gen/hash-map :name gen/string-ascii
                                        :weight (gen/choose 1 100))
                          1 10))

(def all-user-ids-are-distributed
  (prop/for-all [users (gen/choose 10 5000)
                 scenarios scenario-generator]
                (= users
                   (count (mapcat :users (weighted-scenarios (range users) scenarios))))))

(defspec splits-all-users-to-weighted-scenarios
  100
  all-user-ids-are-distributed)

(ns trombi.simulation-util-test
  (:require [clojure.test :refer :all]
            [trombi.test-helpers :refer :all]
            [clojure.test.check.properties :as prop]
            [clj-containment-matchers.clojure-test :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [trombi.simulation-util :as simulation-util])
  (:import (clojure.lang ExceptionInfo)))

(def scenario-generator (gen/vector
                          (gen/hash-map :name gen/string-ascii
                                        :weight (gen/choose 1 100))
                          1 10))

;;Splitting by weight throws when total is too small for the weights
;;(see throws-exception-if-too-few-requests). Total of at least the sum of
;;weights guarantees that every scenario gets at least one without overflow.
(def weighted-input-generator
  (gen/let [scenarios scenario-generator
            users     (gen/choose (max 100 (reduce + (map :weight scenarios))) 5000)
            rate      (gen/choose (max 10 (reduce + (map :weight scenarios))) 2000)]
    {:scenarios scenarios :users users :rate rate}))

(def all-user-ids-are-distributed
  (prop/for-all [{:keys [users scenarios]} weighted-input-generator]
    (= users
       (count (mapcat :users
                      (simulation-util/weighted-scenarios (range users) scenarios))))))

(def at-least-one-user-is-distributed-to-every-scenario
  (prop/for-all [{:keys [users scenarios]} weighted-input-generator]
    (every? pos? (map #(count (:users %))
                      (simulation-util/weighted-scenarios (range users) scenarios)))))

(def rate-is-distributed-if-given
  (prop/for-all [{:keys [users rate scenarios]} weighted-input-generator]
    (= rate
       (apply + (map :rate
                     (simulation-util/weighted-scenarios (range users) rate scenarios))))))

(def every-scenario-has-some-rate-if-given
  (prop/for-all [{:keys [users rate scenarios]} weighted-input-generator]
    (every? pos? (map :rate
                      (simulation-util/weighted-scenarios (range users) rate scenarios)))))

(defspec splits-all-users-to-weighted-scenarios
  100
  all-user-ids-are-distributed)

(defspec at-least-one-user-is-distributed
  100
  at-least-one-user-is-distributed-to-every-scenario)

(defspec splits-rate-to-weighted-scenarios
  100
  rate-is-distributed-if-given)

(deftest throws-exception-if-too-few-requests
  (is (thrown? ExceptionInfo
               (simulation-util/weighted-scenarios (range 10)
                                                   10
                                                   [{:name "A" :weight 1} {:name "B" :weight 18} {:name "C" :weight 1}]))))

(defspec every-scenario-has-some-rate
  100
  every-scenario-has-some-rate-if-given)

;;(tc/quick-check 100 splits-all-users-to-weighted-scenarios)
;;(tc/quick-check 100 all-user-ids-are-distributed)

(defn nullary [])
(defn unary [x])
(defn binary [x y])
(defn ternary [x y z])

(deftest arg-count
  (is (= 0 (simulation-util/arg-count nullary)))
  (is (= 1 (simulation-util/arg-count unary)))
  (is (= 2 (simulation-util/arg-count binary)))
  (is (= 3 (simulation-util/arg-count ternary)))
  (is (= 2 (simulation-util/arg-count (comp unary binary))))
  (is (= 0 (simulation-util/arg-count #(println "foo"))))
  (is (= 1 (simulation-util/arg-count #(println %1))))
  (is (= 2 (simulation-util/arg-count #(println %1 %2))))
  (is (= 3 (simulation-util/arg-count #(println %3))))
  (is (= 0 (simulation-util/arg-count (fn []))))
  (is (= 1 (simulation-util/arg-count (fn [x]))))
  (is (= 2 (simulation-util/arg-count (fn [x y]))))
  (is (= 3 (simulation-util/arg-count (fn [x y z])))))

(deftest failure-message
  (is (= "Assert failed: (= 1 2)"
         (simulation-util/failure-message (AssertionError. "Assert failed: (= 1 2)"))))
  (is (= "Exception message"
         (simulation-util/failure-message (Exception. "Exception message"))))
  (is (= "ExceptionInfo message"
         (simulation-util/failure-message (ex-info "ExceptionInfo message" {:data "foo"}))))
  (is (= "Throwable message"
         (simulation-util/failure-message (Throwable. "Throwable message"))))
  (is (= "java.lang.RuntimeException: RuntimeException message"
         (simulation-util/failure-message (RuntimeException. "RuntimeException message")))))

(deftest clean-result
  (is (= {:result true} (simulation-util/clean-result {:result true})))
  (is (= {:result false :exception "Message"}
         (simulation-util/clean-result {:result false :exception (Exception. "Message")}))))

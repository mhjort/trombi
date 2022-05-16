(ns clj-gatling.simulation-runners-test
  (:require [clojure.test :refer [deftest testing is]]
            [clj-gatling.simulation-runners :refer [calculate-progress]])
  (:import (java.time Duration LocalDateTime)
           (clj_gatling.simulation_runners DurationRunner FixedRequestNumberRunner)))

(deftest duration-runner
  (let [runner (DurationRunner. (Duration/ofSeconds 10))
        now (LocalDateTime/now)]
    (testing "Progress is 0.0 when no time has passed"
      (is (= [0.0 (Duration/ofSeconds 0)] (calculate-progress runner 0 now now))))
    (testing "Progress is 0.5 when half the time has been passed"
      (is (= [0.5 (Duration/ofSeconds 5)] (calculate-progress runner 0 now (.plusSeconds now 5)))))
    (testing "Progress is 1.0 when time has passed"
      (is (= [1.0 (Duration/ofSeconds 10)] (calculate-progress runner 10 now (.plusSeconds now 10)))))
    (testing "Progress is 1.0 when more than max time has passed"
      (is (= [1.0 (Duration/ofSeconds 12)] (calculate-progress runner 10 now (.plusSeconds now 12)))))))

(deftest fixed-request-number-runner
  (let [runner (FixedRequestNumberRunner. 10)
        now (LocalDateTime/now)]
    (testing "Progress is 0.0 when no requests have been sent"
      (is (= [0.0 (Duration/ofSeconds 0)] (calculate-progress runner 0 now now))))
    (testing "Progress is 0.5 when half the requests have been sent"
      (is (= [0.5 (Duration/ofSeconds 0)] (calculate-progress runner 5 now now))))
    (testing "Progress is 1.0 when all the requests have been sent"
      (is (= [1.0 (Duration/ofSeconds 0)] (calculate-progress runner 10 now now))))
    (testing "Progress is 1.0 when more than max the requests have been sent"
      (is (= [1.0 (Duration/ofSeconds 0)] (calculate-progress runner 12 now now))))))

(ns clj-gatling.simulation-runners
  (:import (java.time Duration LocalDateTime)))

(set! *warn-on-reflection* true)

(defprotocol RunnerProtocol
  (calculate-progress [runner sent-requests start])
  (continue-run? [runner sent-requests start])
  (runner-info [runner]))

(deftype DurationRunner [^Duration duration]
  RunnerProtocol
  (calculate-progress [_ _ start]
    (let [now (LocalDateTime/now)
          time-taken-in-millis (.toMillis (Duration/between ^LocalDateTime start now))
          duration-in-millis (max (.toMillis duration) 1)]
      (float (/ time-taken-in-millis duration-in-millis))))
  (continue-run? [_ _ start]
    (.isBefore (LocalDateTime/now) (.plus ^LocalDateTime start duration)))
  (runner-info [_] (str "duration " duration)))

(deftype FixedRequestNumberRunner [number-of-requests]
  RunnerProtocol
  (calculate-progress [runner sent-requests _]
    (float (/ sent-requests number-of-requests)))
  (continue-run? [runner sent-requests _]
    (< sent-requests number-of-requests))
  (runner-info [_] (str "number of requests " number-of-requests)))

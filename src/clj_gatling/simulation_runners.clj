(ns clj-gatling.simulation-runners
  (:import (java.time Duration LocalDateTime)))

(set! *warn-on-reflection* true)

(defprotocol RunnerProtocol
  (calculate-progress [runner sent-requests start now])
  (continue-run? [runner sent-requests start next])
  (runner-info [runner]))

(deftype DurationRunner [^Duration duration]
  RunnerProtocol
  (calculate-progress [_ _ start now]
    (let [time-taken (Duration/between ^LocalDateTime start ^LocalDateTime now)
          time-taken-in-millis (.toMillis time-taken)
          duration-in-millis (max (.toMillis duration) 1)]
      [(float (/ time-taken-in-millis duration-in-millis)) time-taken]))
  (continue-run? [_ _ start next]
    (.isBefore ^LocalDateTime next (.plus ^LocalDateTime start duration)))
  (runner-info [_] (str "duration " duration)))

(deftype FixedRequestNumberRunner [number-of-requests]
  RunnerProtocol
  (calculate-progress [_ sent-requests start now]
    (let [time-taken (Duration/between ^LocalDateTime start ^LocalDateTime now)]
      [(float (/ sent-requests number-of-requests)) time-taken]))
  (continue-run? [_ sent-requests _ _]
    (< sent-requests number-of-requests))
  (runner-info [_] (str "number of requests " number-of-requests)))

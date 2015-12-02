(ns clj-gatling.scenario-runners
  (:require [clj-time.core :as time]
            [clj-time.local :as local-time]))

(defprotocol RunnerProtocol
  (continue-run? [runner sent-requests scenario-start])
  (runner-info [runner]))

(deftype DurationRunner [scenario]
  RunnerProtocol
  (continue-run? [runner _ scenario-start]
    (time/before? (local-time/local-now)
                  (time/plus scenario-start (:duration scenario))))
  (runner-info [_] (str " with duration " (:duration scenario))))

(deftype FixedRequestNumberRunner [number-of-requests]
  RunnerProtocol
  (continue-run? [runner sent-requests _]
    (< sent-requests number-of-requests))
  (runner-info [_] ""))

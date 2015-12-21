(ns clj-gatling.simulation-runners
  (:require [clj-time.core :as time]
            [clj-time.local :as local-time]))

(defprotocol RunnerProtocol
  (continue-run? [runner sent-requests start])
  (runner-info [runner]))

(deftype DurationRunner [duration]
  RunnerProtocol
  (continue-run? [runner _ start]
    (time/before? (local-time/local-now)
                  (time/plus start duration)))
  (runner-info [_] (str "duration " duration)))

(deftype FixedRequestNumberRunner [number-of-requests]
  RunnerProtocol
  (continue-run? [runner sent-requests _]
    (< sent-requests number-of-requests))
  (runner-info [_] (str "number of requests " number-of-requests)))

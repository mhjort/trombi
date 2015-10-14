(ns clj-gatling.scenario-runners
  (:require [clj-time.core :as time]
            [clj-time.local :as local-time]))

(defprotocol RunnerProtocol
  (continue-run? [runner handled-requests scenario-start])
  (runner-info [runner]))

(deftype DurationRunner [scenario]
  RunnerProtocol
  (continue-run? [runner _ scenario-start]
    (time/before? (local-time/local-now)
                  (time/plus scenario-start (:duration scenario))))
  (runner-info [_] (str "duration " (:duration scenario))))

(deftype FixedRequestNumberRunner [scenario]
  RunnerProtocol
  (continue-run? [runner handled-requests _]
    (< handled-requests (:number-of-requests scenario)))
  (runner-info [_] (str "requests " (:number-of-requests scenario))))

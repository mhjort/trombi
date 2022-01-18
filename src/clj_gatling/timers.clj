(ns clj-gatling.timers
  (:require [clojure.core.async.impl.protocols :as impl]
            [clojure.core.async.impl.channels :as channels])
  (:import [java.util.concurrent DelayQueue Delayed TimeUnit]))

;;Core.async has a limit of 1024 concurrent limits for waiting a same timeout channel
;;Internally core.async has a cache of timeout channels of same value
;;See: https://github.com/clojure/core.async/blob/master/src/main/clojure/clojure/core/async/impl/timers.clj
;;This is a version of timers.clj where all that caching has removed
(set! *warn-on-reflection* true)

(defonce ^:private ^DelayQueue timeouts-queue
  (DelayQueue.))

(deftype TimeoutQueueEntry [channel ^long timestamp]
  Delayed
  (getDelay [this time-unit]
    (.convert time-unit
              (- timestamp (System/currentTimeMillis))
              TimeUnit/MILLISECONDS))
  (compareTo
   [this other]
   (let [ostamp (.timestamp ^TimeoutQueueEntry other)]
     (if (< timestamp ostamp)
       -1
       (if (= timestamp ostamp)
         0
         1))))
  impl/Channel
  (close! [this]
    (impl/close! channel)))

(defn- timeout-worker
  []
  (let [q timeouts-queue]
    (loop []
      (let [^TimeoutQueueEntry tqe (.take q)]
        (impl/close! tqe))
      (recur))))

(defonce timeout-daemon
  (delay
   (doto (Thread. ^Runnable timeout-worker "clj-gatling.timers/timeout-daemon")
     (.setDaemon true)
     (.start))))

(defn timeout
  "returns a channel that will close after msecs"
  [^long msecs]
  @timeout-daemon
  (let [timeout (+ (System/currentTimeMillis) msecs)
        timeout-channel (channels/chan nil)
        timeout-entry (TimeoutQueueEntry. timeout-channel timeout)]
    (.put timeouts-queue timeout-entry)
    timeout-channel))

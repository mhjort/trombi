(ns clj-gatling.simulation
  (:require [org.httpkit.client :as http]
            [clj-time.core :as time]
            [clj-time.local :as local-time]
            [clojure.core.async :as async :refer [go go-loop put! <!! alts! <! >!]]))

(defprotocol RunnerProtocol
  (continue-run? [runner current-time i])
  (runner-info [runner]))

(deftype DurationRunner [duration]
  RunnerProtocol
  (continue-run? [_ start i] (time/before? (local-time/local-now) (time/plus start duration)))
  (runner-info [_] (str "duration " duration)))

(deftype FixedRequestNumberRunner [requests]
  RunnerProtocol
  (continue-run? [_ _ i] (< i requests))
  (runner-info [_] (str "requests " requests)))

(defn- now [] (System/currentTimeMillis))

(defn- async-http-request [url user-id callback]
  (let [check-status (fn [{:keys [status]}] (callback (= 200 status)))]
    (http/get url {} check-status)))

(defn- request-fn [request]
  (if-let [url (:http request)]
    (partial async-http-request url)
    (:fn request)))

(defn- request-result [id request-name success start]
  {:id id :name request-name :result success :start start :end (now)})

(defn async-function-with-timeout [request timeout user-id result-channel]
  (let [start    (now)
        response (async/chan)
        function (memoize (request-fn request))]
    (go
      (function user-id #(put! response
                               {:name (:name request)
                                :id user-id
                                :start start
                                :end (now)
                                :result %}))
      (let [[result c] (alts! [response (async/timeout timeout)])]
        (if (= c response)
          (>! result-channel result)
          (>! result-channel {:name (:name request)
                              :id user-id
                              :start start
                              :end (now)
                              :result false}))))))

(defn- run-requests [requests timeout user-id result-channel]
  (let [c (async/chan)]
    (go-loop [r requests
              results []]
      (async-function-with-timeout (first r) timeout user-id c)
      (let [result (<! c)]
        (if (empty? (rest r))
          (>! result-channel (conj results result))
          (recur (rest r) (conj results result)))))))

(defn run-scenario-version2 [runner concurrency number-of-requests timeout scenario]
  (println (str "Running scenario " (:name scenario) " with " concurrency " concurrency and
            " (runner-info runner) "."))
  (let [cs       (repeatedly concurrency async/chan)
        ps       (map vector (iterate inc 0) cs)
        results  (async/chan)
        requests (-> scenario :requests)
        scenario-start (local-time/local-now)]
    (doseq [[user-id c] ps]
      (run-requests requests timeout user-id c))
    (go-loop [^long i 0]
      (let [[result c] (alts! cs)]
        (when (continue-run? runner scenario-start (+ i concurrency))
          (run-requests requests timeout (+ i concurrency) c))
        (>! results {:name (:name scenario)
                     :id (:id (first result))
                     :start (:start (first result))
                     :end (:end (last result))
                     :requests result})
        (when (continue-run? runner scenario-start i)
          (recur (inc i)))))
    (repeatedly number-of-requests #(<!! results))))

(defn run-scenarios-version2 [runner concurrency number-of-requests timeout scenarios]
  (let [results (async/chan)]
    (go-loop [s scenarios]
      (>! results
          (run-scenario-version2 runner concurrency number-of-requests timeout (first scenarios)))
      (when-not (empty? (rest s))
        (recur (rest s))))
  (apply concat (repeatedly (count scenarios) #(<!! results)))))

(defn run-simulation [scenarios users & [options]]
  (let [requests (or (:requests options) users)
        duration (:duration options)
        step-timeout (or (:timeout-in-ms options) 5000)
        runner (if (nil? duration)
                 (FixedRequestNumberRunner. requests)
                 (DurationRunner. duration))]
    (run-scenarios-version2 runner users requests step-timeout scenarios)))

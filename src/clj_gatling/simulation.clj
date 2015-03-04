(ns clj-gatling.simulation
  (:require [clj-gatling.httpkit :as http]
            [clj-time.core :as time]
            [clj-time.local :as local-time]
            [clojure.core.async :as async :refer [go go-loop put! <!! alts! <! >!]]))

(defprotocol RunnerProtocol
  (continue-run? [runner current-time i])
  (runner-info [runner]))

(deftype DurationRunner [duration]
  RunnerProtocol
  (continue-run? [_ start _] (time/before? (local-time/local-now) (time/plus start duration)))
  (runner-info [_] (str "duration " duration)))

(deftype FixedRequestNumberRunner [requests]
  RunnerProtocol
  (continue-run? [_ _ i] (< i requests))
  (runner-info [_] (str "requests " requests)))

(defn- now [] (System/currentTimeMillis))

(defn- request-fn [request]
  (if-let [url (:http request)]
    (partial http/async-http-request url)
    (:fn request)))

(defn- request-result [id request-name success start]
  {:id id :name request-name :result success :start start :end (now)})

(defn async-function-with-timeout [request timeout user-id context result-channel]
  (let [start    (now)
        response (async/chan)
        function (memoize (request-fn request))
        callback (fn [result & context]
                   (put! response [{:name (:name request)
                                   :id user-id
                                   :start start
                                   :end (now)
                                   :result result} (first context)]))]
    (go
      (function user-id context callback)
      (let [[result c] (alts! [response (async/timeout timeout)])]
        (if (= c response)
          (>! result-channel result)
          (>! result-channel [{:name (:name request)
                               :id user-id
                               :start start
                               :end (now)
                               :result false} (first context)]))))))

(defn- run-requests [requests timeout user-id result-channel]
  (let [c (async/chan)]
    (go-loop [r requests
              context {}
              results []]
      (async-function-with-timeout (first r) timeout user-id context c)
      (let [[result new-ctx] (<! c)]
        (if (empty? (rest r))
          (>! result-channel (conj results result))
          (recur (rest r) new-ctx (conj results result)))))))

(defn- response->result [scenario result]
  {:name (:name scenario)
   :id (:id (first result))
   :start (:start (first result))
   :end (:end (last result))
   :requests result})

(defn- run-requests-constantly [runner cs timeout scenario scenario-start concurrency]
  (let [requests (:requests scenario)
        results (async/chan)]
    (go-loop [^long i 0]
      (let [[result c] (alts! cs)]
        (when (continue-run? runner scenario-start (+ i concurrency))
          (run-requests requests timeout (+ i concurrency) c))
        (>! results (response->result scenario result))
        (when (continue-run? runner scenario-start i)
          (recur (inc i)))))
    results))

(defn run-scenario [runner concurrency number-of-requests timeout scenario]
  (println (str "Running scenario " (:name scenario) " with " concurrency " concurrency and
            " (runner-info runner) "."))
  (let [cs       (repeatedly concurrency async/chan)
        ps       (map vector (iterate inc 0) cs)
        requests (:requests scenario)
        scenario-start (local-time/local-now)]
    (doseq [[user-id c] ps]
      (run-requests requests timeout user-id c))
    (let [results (run-requests-constantly runner cs timeout scenario scenario-start concurrency)]
      (repeatedly number-of-requests #(<!! results)))))

(defn- distinct-request-count [scenarios]
  (reduce + (map #(count (:requests %)) scenarios)))

(defn run-scenarios [runner concurrency number-of-requests timeout scenarios]
  (let [requests-for-scenario  (int (/ number-of-requests (distinct-request-count scenarios)))
        results                (async/chan)]
    (go-loop [s scenarios]
      (>! results
          (run-scenario runner concurrency requests-for-scenario timeout (first s)))
      (when (seq (rest s))
        (recur (rest s))))
  (apply concat (repeatedly (count scenarios) #(<!! results)))))

(defn run-simulation [scenarios users & [options]]
  (let [requests (or (:requests options) (* users (distinct-request-count scenarios)))
        duration (:duration options)
        step-timeout (or (:timeout-in-ms options) 5000)
        runner (if (nil? duration)
                 (FixedRequestNumberRunner. requests)
                 (DurationRunner. duration))]
    (run-scenarios runner users requests step-timeout scenarios)))

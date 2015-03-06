(ns clj-gatling.simulation
  (:require [clj-gatling.httpkit :as http]
            [clj-time.core :as time]
            [clj-time.local :as local-time]
            [clojure.core.async :as async :refer [go go-loop put! <!! alts! <! >!]]))

(defn- now [] (System/currentTimeMillis))

(defn- request-fn [request]
  (if-let [url (:http request)]
    (partial http/async-http-request url)
    (:fn request)))

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

(defprotocol RunnerProtocol
  (run-requests-constantly [runner cs timeout scenario scenario-start])
  (runner-info [runner]))

(deftype DurationRunner [duration]
  RunnerProtocol
  (run-requests-constantly [_ cs timeout scenario scenario-start]
    (let [results (async/chan)
          continue-run? #(time/before? (local-time/local-now) (time/plus scenario-start duration))]
      (go-loop [^long i 0]
        (let [[result c] (alts! cs)]
          (when (continue-run?)
            (run-requests (:requests scenario) timeout (+ i (:concurrency scenario)) c))
          (>! results (response->result scenario result))
          (when (continue-run?)
            (recur (inc i)))))
      results))
  (runner-info [_] (str "duration " duration)))

(deftype FixedRequestNumberRunner [^long number-of-requests]
  RunnerProtocol
  (run-requests-constantly [_ cs timeout scenario scenario-start]
    (let [results (async/chan)]
      (go-loop [^long requests-left number-of-requests
                ^long user-id (:concurrency scenario)]
        (let [[result c] (alts! cs)]
          (when (< user-id number-of-requests)
            (run-requests (:requests scenario) timeout user-id c))
          (>! results (response->result scenario result))
          (when (pos? requests-left)
            (recur (dec requests-left) (inc user-id)))))
      results))
  (runner-info [_] (str "requests " number-of-requests)))

(defn- request-result [id request-name success start]
  {:id id :name request-name :result success :start start :end (now)})

(defn run-scenario [runner timeout scenario]
  (let [concurrency        (:concurrency scenario)
        number-of-requests (:number-of-requests scenario)]
    (println (str "Running scenario " (:name scenario) " with " concurrency " concurrency and
              " (runner-info runner) "."))
    (let [cs       (repeatedly concurrency async/chan)
          ps       (map vector (iterate inc 0) cs)
          requests (:requests scenario)
          scenario-start (local-time/local-now)]
      (doseq [[user-id c] ps]
        (run-requests requests timeout user-id c))
      (let [results (run-requests-constantly runner cs timeout scenario scenario-start)]
        (repeatedly number-of-requests #(<!! results))))))

(defn- distinct-request-count [scenarios]
  (reduce + (map #(count (:requests %)) scenarios)))

(defn- weighted [weights value]
  (let [sum-of-weights (reduce + weights)]
    (map #(Math/round (double (* value (/ % sum-of-weights)))) weights)))

(defn run-scenarios [runner concurrency number-of-requests timeout scenarios]
  (let [results            (async/chan)
        weights            (map #(or (:weight %) 1) scenarios)
        requests           (weighted weights (/ number-of-requests
                                                (distinct-request-count scenarios)))
        concurrencies      (weighted weights concurrency)
        runnable-scenarios (map #(assoc %1 :concurrency %2 :number-of-requests %3)
                                scenarios
                                concurrencies
                                requests)]
    (go-loop [s runnable-scenarios]
      (>! results
          (run-scenario runner timeout (first s)))
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

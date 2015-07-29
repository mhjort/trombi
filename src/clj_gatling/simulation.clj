(ns clj-gatling.simulation
  (:require [clj-gatling.httpkit :as http]
            [clj-time.core :as time]
            [clj-time.local :as local-time]
            [clojure.core.async :as async :refer [go go-loop put! thread <!! alts! <! >!]]))

(defn- now [] (System/currentTimeMillis))

(defn- bench [f]
  (fn [start-promise end-promise callback context]
    (deliver start-promise (now))
    (f (fn [result & [context]]
         (deliver end-promise (now))
         (callback result context))
       context)))

(defn- request-fn [request]
  (bench (if-let [url (:http request)]
            (partial http/async-http-request url)
            (:fn request))))

(defn async-function-with-timeout [request timeout user-id context]
  (let [start-promise (promise)
        end-promise (promise)
        response (async/chan)
        function (memoize (request-fn request))
        callback (fn [result context]
                   (put! response [{:name (:name request)
                                   :id user-id
                                   :start @start-promise
                                   :end @end-promise
                                   :result result} context]))]
    (go
      (function start-promise end-promise callback (assoc context :user-id user-id))
      (let [[result c] (alts! [response (async/timeout timeout)])]
        (if (= c response)
          result
          [{:name (:name request)
            :id user-id
            :start @start-promise
            :end (now)
            :result false} (first context)])))))

(defn- run-requests [requests timeout user-id result-channel]
  (go-loop [r requests
            context {}
            results []]
    (let [[result new-ctx] (<! (async-function-with-timeout (first r) timeout user-id context))]
      (if (empty? (rest r))
        (>! result-channel (conj results result))
        (recur (rest r) new-ctx (conj results result))))))

(defn- response->result [scenario result]
  {:name (:name scenario)
   :id (:id (first result))
   :start (:start (first result))
   :end (:end (last result))
   :requests result})

(defprotocol RunnerProtocol
  (run-requests-constantly [runner cs timeout scenario-start])
  (runner-info [runner]))

(deftype DurationRunner [scenario]
  RunnerProtocol
  (run-requests-constantly [_ cs timeout scenario-start]
    (let [results (async/chan)
          continue-run? #(time/before? (local-time/local-now)
                                       (time/plus scenario-start (:duration scenario)))]
      (go-loop [^long i 0]
        (let [[result c] (alts! cs)]
          (when (continue-run?)
            (run-requests (:requests scenario) timeout (+ i (:concurrency scenario)) c))
          (>! results (response->result scenario result))
          (when (continue-run?)
            (recur (inc i)))))
      results))
  (runner-info [_] (str "duration " (:duration scenario))))

(deftype FixedRequestNumberRunner [scenario]
  RunnerProtocol
  (run-requests-constantly [_ cs timeout scenario-start]
    (let [results (async/chan)
          requests-in-scenario (count (:requests scenario))]
      (go-loop [^long requests-left (:number-of-requests scenario)
                ^long user-id (:concurrency scenario)]
        (let [[result c] (alts! cs)]
          (when (< user-id (:number-of-requests scenario))
            (run-requests (:requests scenario) timeout user-id c))
          (>! results (response->result scenario result))
          (when (pos? requests-left)
            (recur (- requests-left requests-in-scenario) (inc user-id)))))
      results))
  (runner-info [_] (str "requests " (:number-of-requests scenario))))

(defn- request-result [id request-name success start]
  {:id id :name request-name :result success :start start :end (now)})

(defn run-scenario [timeout scenario]
  (let [concurrency        (:concurrency scenario)
        number-of-requests (:number-of-requests scenario)]
    (println (str "Running scenario " (:name scenario) " with " concurrency " concurrency and
              " (runner-info (:runner scenario)) "."))
    (let [cs       (repeatedly concurrency async/chan)
          ps       (map vector (iterate inc 0) cs)
          requests (:requests scenario)
          scenario-start (local-time/local-now)]
      (doseq [[user-id c] ps]
        (run-requests requests timeout user-id c))
      (let [results (run-requests-constantly (:runner scenario) cs timeout scenario-start)]
        (repeatedly (/ number-of-requests (count requests)) #(<!! results))))))

(defn- distinct-request-count [scenarios]
  (reduce + (map #(count (:requests %)) scenarios)))

(defn- weighted [weights value]
  (let [sum-of-weights (reduce + weights)]
    (map #(Math/round (double (* value (/ % sum-of-weights)))) weights)))

(defn- calculate-weighted-scenarios [concurrency number-of-requests scenarios]
  (let [weights            (map #(or (:weight %) 1) scenarios)
        requests           (weighted weights number-of-requests)
        concurrencies      (weighted weights concurrency)]
    (map #(assoc %1 :concurrency %2 :number-of-requests %3)
          scenarios
          concurrencies
          requests)))

(defn run-scenarios [timeout scenarios]
  (let [results (map (fn [scenario] (thread (run-scenario timeout scenario)))
                     scenarios)]
    (apply concat (map #(<!! %) results))))

(defn run-simulation [scenarios users & [options]]
  (let [requests (or (:requests options) (* users (distinct-request-count scenarios)))
        duration (:duration options)
        step-timeout (or (:timeout-in-ms options) 5000)
        runner (fn [scenario] (if (nil? duration)
                                (FixedRequestNumberRunner. scenario)
                                (DurationRunner. scenario)))
        runnable-scenarios (calculate-weighted-scenarios users requests scenarios)]
    (run-scenarios step-timeout (map #(assoc % :runner (runner %) :duration duration) runnable-scenarios))))

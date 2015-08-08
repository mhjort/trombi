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
            :result false} context])))))

(defn- run-requests [requests timeout user-id result-channel]
  (go-loop [r requests
            context {}
            results []]
    (let [[result new-ctx] (<! (async-function-with-timeout (first r) timeout user-id context))]
      (if (empty? (rest r))
        (>! result-channel (conj results result))
        (recur (rest r) new-ctx (conj results result))))))

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

(defn- request-result [id request-name success start]
  {:id id :name request-name :result success :start start :end (now)})

(defn- response->result [scenario result]
  {:name (:name scenario)
   :id (:id (first result))
   :start (:start (first result))
   :end (:end (last result))
   :requests result})

(defn- run-scenario-once [scenario timeout user-id]
  (let [result-channel (async/chan)]
    (go-loop [r (:requests scenario)
              context {}
              results []]
             (let [[result new-ctx] (<! (async-function-with-timeout (first r) timeout user-id context))]
               (if (empty? (rest r))
                 (>! result-channel (conj results result))
                 (recur (rest r) new-ctx (conj results result)))))
    result-channel))

(defn- run-scenario-constantly [scenario timeout user-id]
  (let [c (async/chan)]
    (go-loop []
        (>! c (<! (run-scenario-once scenario timeout user-id)))
        (recur))
    c))

(defn run-scenario [timeout scenario]
  (let [scenario-start (local-time/local-now)
        response-chan (async/merge (map #(run-scenario-constantly scenario timeout %)
                                        (range (:concurrency scenario))))]
    (loop [responses []
           handled-requests 0]
      (if (continue-run? (:runner scenario) handled-requests scenario-start)
        (let [result (response->result scenario (<!! response-chan))]
          (recur (conj responses result) (+ handled-requests (count (:requests result)))))
        responses))))

(defn- print-scenario-info [scenario]
  (let [concurrency        (:concurrency scenario)
        number-of-requests (:number-of-requests scenario)]
    (println "Running scenario" (:name scenario)
             "with concurrency" concurrency
             "and" (runner-info (:runner scenario)) ".")))

(defn run-scenarios [timeout scenarios]
  (let [results (doall (map (fn [scenario]
                              (print-scenario-info scenario)
                              (thread (run-scenario timeout scenario)))
                        scenarios))]
    (mapcat #(<!! %) results)))

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

(defn run-simulation [scenarios users & [options]]
  (let [requests (or (:requests options) (* users (distinct-request-count scenarios)))
        duration (:duration options)
        step-timeout (or (:timeout-in-ms options) 5000)
        runner (fn [scenario] (if (nil? duration)
                                (FixedRequestNumberRunner. scenario)
                                (DurationRunner. (assoc scenario :duration duration))))
        runnable-scenarios (calculate-weighted-scenarios users requests scenarios)]
    (run-scenarios step-timeout (map #(assoc % :runner (runner %)) runnable-scenarios))))

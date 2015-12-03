(ns clj-gatling.simulation
  (:require [clj-gatling.httpkit :as http]
            [clj-gatling.simulation-runners :refer :all]
            [clj-time.local :as local-time]
            [clojure.core.async :as async :refer [go go-loop close! put! <!! alts! <! >!]]))

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
        exception-chan (async/chan)
        function (memoize (request-fn request))
        callback (fn [result context]
                   (put! response [{:name (:name request)
                                    :id user-id
                                    :start @start-promise
                                    :end @end-promise
                                    :result result} context]))]
    (go
      (try
        (function start-promise end-promise callback (assoc context :user-id user-id))
      (catch Exception e
        (put! exception-chan e)))
      (let [[result c] (alts! [response (async/timeout timeout) exception-chan])]
        (if (= c response)
          result
          [{:name (:name request)
            :id user-id
            :start @start-promise
            :end (now)
            :result false} context])))))

(defn- response->result [scenario result]
  {:name (:name scenario)
   :id (:id (first result))
   :start (:start (first result))
   :end (:end (last result))
   :requests result})

(defn- run-scenario-once [options scenario user-id]
  (let [timeout (:timeout options)
        result-channel (async/chan)
        skip-next-after-failure? (if (nil? (:skip-next-after-failure? scenario))
                                    true
                                    (:skip-next-after-failure? scenario))
        request-failed? #(not (:result %))]
    (go-loop [r (:requests scenario)
              context {}
              results []]
             (let [[result new-ctx] (<! (async-function-with-timeout (first r) timeout user-id context))]
               (if (or (empty? (rest r))
                       (and skip-next-after-failure?
                           (request-failed? result)))
                 (>! result-channel (conj results result))
                 (recur (rest r) new-ctx (conj results result)))))
    result-channel))

(defn- run-scenario-constantly [options scenario user-id]
  (let [c (async/chan)]
    (go-loop []
        (>! c (<! (run-scenario-once options scenario user-id)))
        (recur))
    c))

(defn- print-scenario-info [scenario]
  (let [concurrency        (:concurrency scenario)]
    (println "Running scenario" (:name scenario)
             "with concurrency" concurrency)))

(defn- run-scenario [options scenario]
  (print-scenario-info scenario)
  (let [responses (async/merge (map #(run-scenario-constantly options scenario %)
                                    (range (:concurrency scenario))))
        results (async/chan)]
    (go-loop []
             (>! results (response->result scenario (<! responses)))
             (recur))
    results))

(defn run-scenarios [options scenarios]
  (let [start (local-time/local-now)
        runner (:runner options)
        responses (async/merge (map (partial run-scenario options) scenarios))
        results (async/chan)]
    (go-loop [handled-requests 0]
             (if (continue-run? runner handled-requests start)
               (let [result (<! responses)]
                 (>! results result)
                 (recur (+ handled-requests (count (:requests result)))))
               (close! results)))
    results))

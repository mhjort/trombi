(ns clj-gatling.simulation
  (:require [clj-gatling.httpkit :as http]
            [clj-gatling.simulation-runners :refer :all]
            [clj-gatling.schema :as schema]
            [clj-gatling.simulation-util :refer [weighted-scenarios
                                                 choose-runner]]
            [schema.core :refer [check validate]]
            [clj-time.local :as local-time]
            [clojure.set :refer [rename-keys]]
            [clojure.core.async :as async :refer [go go-loop close! put! <!! alts! <! >!]]))

(defn- now [] (System/currentTimeMillis))

(defn asynchronize [f ctx]
  (go
    (try
      (let [result (f ctx)]
        (if (instance? clojure.core.async.impl.channels.ManyToManyChannel result)
          (let [[success? new-ctx] (<! result)]
            [success? (now) new-ctx])
          ;TODO Warning if result is not a vector with size 2
          [(first result) (now) (second result)]))
      (catch Exception _
        [false (now) ctx]))))

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

(defn async-function-with-timeout [request timeout sent-requests user-id context]
  (swap! sent-requests inc)
  (let [start (now)
        response (asynchronize (:action request) (assoc context :user-id user-id))]
    (go
      (let [[[result end new-ctx] c] (alts! [response (async/timeout timeout)])]
        (if (= c response)
          [{:name (:name request)
            :id user-id
            :start start
            :end end
            :result result} new-ctx]
          [{:name (:name request)
            :id user-id
            :start start
            :end (now)
            :result false} context])))))

(defn async-function-with-timeout-old [request timeout sent-requests user-id context]
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
        (swap! sent-requests inc)
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
        sent-requests (:sent-requests options)
        result-channel (async/chan)
        skip-next-after-failure? (if (nil? (:skip-next-after-failure? scenario))
                                    true
                                    (:skip-next-after-failure? scenario))
        request-failed? #(not (:result %))]
    (go-loop [r (:steps scenario)
              context (or (:context options) {})
              results []]
             (let [[result new-ctx] (<! (async-function-with-timeout (first r)
                                                                     timeout
                                                                     sent-requests
                                                                     user-id
                                                                     context))]
               (if (or (empty? (rest r))
                       (and skip-next-after-failure?
                           (request-failed? result)))
                 (>! result-channel (conj results result))
                 (recur (rest r) new-ctx (conj results result)))))
    result-channel))

(defn- run-scenario-constantly [options scenario user-id]
  (let [c (async/chan)
        runner (:runner options)
        simulation-start (:simulation-start options)
        sent-requests (:sent-requests options)]
    (go-loop []
             (let [result (<! (run-scenario-once options scenario user-id))]
               (>! c result)
               (if (continue-run? runner @sent-requests simulation-start)
                 (recur)
                 (close! c))))
    c))

(defn- print-scenario-info [scenario]
  (println "Running scenario" (:name scenario)
           "with concurrency" (count (:users scenario))))

(defn legacy-request-fn->action [request]
  (let [f (if-let [url (:http request)]
            (partial http/async-http-request url)
            (:fn request))
        c (async/chan)]
    (fn [ctx]
      (f (fn [result & [new-ctx]]
           (if new-ctx
             (put! c [result new-ctx])
             (put! c [result ctx])))
         ctx)
      c)))

(defn- convert-from-legacy [scenarios]
  ;Skip conversion if it matches new schema already
  (if-not (check [schema/RunnableScenario] scenarios)
    scenarios
    (let [request->step (fn [request]
                          (-> request
                              (assoc :action (legacy-request-fn->action request))
                              (dissoc :fn :http)))]
      (validate [schema/RunnableScenario]
                (map (fn [scenario]
                       (-> scenario
                           (update :requests #(map request->step %))
                           (rename-keys {:requests :steps})
                           (dissoc :concurrency :weight)))
                     scenarios)))))

(defn- run-scenario [options scenario]
  (print-scenario-info scenario)
  (let [responses (async/merge (map #(run-scenario-constantly options scenario %)
                                    (:users scenario)))
        results (async/chan)]
    (go-loop []
             (if-let [result (<! responses)]
               (do
                 (>! results (response->result scenario result))
                 (recur))
               (close! results)))
    results))

(defn run-scenarios [options scenarios]
  (println "Running simulation with" (runner-info (:runner options)))
  (let [simulation-start (local-time/local-now)
        sent-requests (atom 0)
        run-scenario-with-opts (partial run-scenario
                                        (assoc options
                                               :context (:context options)
                                               :simulation-start simulation-start
                                               :sent-requests sent-requests))
        responses (async/merge (map run-scenario-with-opts (convert-from-legacy scenarios)))
        results (async/chan)]
    (go-loop []
             (if-let [result (<! responses)]
               (do
                 (>! results result)
                 (recur))
               (close! results)))
    results))

(defn run [{:keys [scenarios] :as simulation}
           {:keys [concurrency] :as options}]
  (validate schema/Simulation simulation)
  (run-scenarios (assoc options :runner (choose-runner scenarios
                                                       concurrency
                                                       options))
                 (weighted-scenarios (range concurrency) scenarios)))



(ns clj-gatling.simulation
  (:use [clojure.set :only [rename-keys]])
  (:require [org.httpkit.client :as http]
            [clojure.core.async :as async :refer [go <! >!]]))

(defn- collect-result [cs]
  (let [[result c] (async/alts!! cs)]
    @result))

(defn- run-parallel-and-collect-results [function times]
  (let [cs (repeatedly times async/chan)
        ps (map vector (iterate inc 0) cs)]
    (doseq [[i c] ps] (go (>! c (function i))))
    (let [results (repeatedly times (partial collect-result cs))]
      (dorun results) ;Lazy results must be evaluated before channels are closed
      (doseq [c cs] (async/close! c))
      results)))

(defn- collect-result-and-run-next [cs run]
  (let [[result c] (async/alts!! cs)
        ret @result]
    (go (>! c (run)))
    ret))

(defn- run-scenarios-in-parallel [scenario-runners parallel-count]
  (let [cs (repeatedly parallel-count async/chan)
        ps (map vector (iterate inc 0) cs)]
    (doseq [[i c] ps] (go (>! c ((nth scenario-runners i)))))
    (let [results-with-new-run (map (partial collect-result-and-run-next cs) (drop parallel-count scenario-runners))
          results-rest (repeatedly parallel-count (partial collect-result cs))]
      (dorun results-with-new-run) ;Lazy results must be evaluated before channels are closed
      (dorun results-rest) ;Lazy results must be evaluated before channels are closed
      (doseq [c cs] (async/close! c))
      (concat results-with-new-run results-rest))))

(defn- now [] (System/currentTimeMillis))

(defn- async-http-request [url user-id callback]
  (let [check-status (fn [{:keys [status]}] (callback (= 200 status)))]
    (http/get url {} check-status)))

(defn run-scenario-async [scenario id]
  (fn [] (let [scenario-start (now)
        end-result (promise)
        run-requests-fn (fn run-requests [requests result]
          (if (empty? requests)
            (deliver end-result {:id id :name (:name scenario) :start scenario-start :end (now) :requests result})
            (let [request (first requests)
                  req-fn (if-let [url (:http request)]
                            (partial async-http-request url)
                            (:fn request))
                  start (now)]
              (req-fn id (fn [success]
                           (run-requests (rest requests) (conj result {:id id :name (:name request) :result success :start start :end (now)})))))))]
    (run-requests-fn (:requests scenario) [])
    end-result)))

(defn- run-nth-scenario-with-multiple-users [scenarios users rounds i]
  (let [result (promise)
        scenario (nth scenarios i)
        scenario-runs (map (partial run-scenario-async scenario) (range (* users rounds)))]
     (println (str "Running scenario " (:name scenario) " with " users " users and " rounds " rounds."))
     (deliver result (run-scenarios-in-parallel scenario-runs users))
    result))

(defn run-simulation [scenarios users & [options]]
  (let [rounds (or (:rounds options) 1)
        scenario-runner (partial run-nth-scenario-with-multiple-users scenarios users rounds) 
        results (run-parallel-and-collect-results scenario-runner (count scenarios))]
    (flatten results)))

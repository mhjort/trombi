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

(defn- now [] (System/currentTimeMillis))

(defn- wrap-with-callback [func]
  (fn [id callback]
    (let [result (func id)]
      (callback result))))

(defn- async-http-request [url user-id callback]
  (let [check-status (fn [{:keys [status]}] (callback (= 200 status)))]
    (http/get url {} check-status)))

(defn run-scenario-async [scenario id]
  (let [scenario-start (now)
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
    (run-requests-fn (map #(assoc % :fn (wrap-with-callback (:fn %))) (:requests scenario)) [])
    end-result))

(defn- run-nth-scenario-with-multiple-users [scenarios users i]
  (let [result (promise)
        scenario (nth scenarios i)]
     (println (str "Running scenario " (:name scenario) " with " users " users."))
     (deliver result (run-parallel-and-collect-results (partial run-scenario-async scenario) users))
    result))

(defn run-simulation [scenarios users]
  (let [function (partial run-nth-scenario-with-multiple-users scenarios users) 
        results (run-parallel-and-collect-results function (count scenarios))]
    (flatten results)))

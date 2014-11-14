(ns clj-gatling.simulation
  (:require [org.httpkit.client :as http]
            [clj-time.core :as time]
            [clj-time.local :as local-time]
            [clojure.core.async :as async :refer [go <! >!]]))

(defn- collect-result [cs]
  (let [[result c] (async/alts!! cs)]
    @result))

(defmacro with-channels [binding & body]
  `(let [~(first binding) (repeatedly ~(second binding) async/chan)
         ~'result (do ~@body)]
    (dorun ~'result) ;Lazy results must be evaluated before channels are closed
    (doseq [~'c ~(first binding)] (async/close! ~'c))
    ~'result))

(defn- run-parallel-and-collect-results [function times]
  (with-channels [cs times]
    (let [ps (map vector (iterate inc 0) cs)]
      (doseq [[i c] ps]
        (go (>! c (function i))))
      (repeatedly times (partial collect-result cs)))))

(defn- collect-result-and-run-next [cs run]
  (let [[result c] (async/alts!! cs)
        ret @result]
    (go (>! c (run)))
    ret))

(defprotocol RunnerProtocol
  (continue-run? [runner current-time])
  (run-range [runner number-of-users])
  (runner-info [runner]))

(deftype DurationRunner [duration]
  RunnerProtocol
  (continue-run? [_ start] (time/before? (local-time/local-now) (time/plus start duration)))
  (run-range [_ _] (range))
  (runner-info [_] (str "duration " duration)))

(deftype RoundsRunner [number-of-rounds]
  RunnerProtocol
  (continue-run? [_ _] true)
  (run-range [_ number-of-users] (range (* number-of-users number-of-rounds)))
  (runner-info [_] (str "rounds " number-of-rounds)))

(defn- run-scenarios-in-parallel [scenario-runners parallel-count runner]
  (with-channels [cs parallel-count]
    (let [scenario-start (local-time/local-now)
          ps (map vector (iterate inc 0) cs)]
      (doseq [[i c] ps]
        (go (>! c ((nth scenario-runners i)))))
      (let [results-with-new-run (take-while (fn [_] (continue-run? runner scenario-start)) 
                                             (map (partial collect-result-and-run-next cs) (drop parallel-count scenario-runners)))
            results-rest (repeatedly parallel-count (partial collect-result cs))]
        (concat results-with-new-run results-rest)))))

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

(defn- callback->chan [step-fn id]
  (let [c (async/chan)]
    (step-fn id (fn [success] (async/put! c success)))
    c))

(defn- run-scenario-async [scenario step-timeout id]
  (fn [] (let [scenario-start (now)
        end-result (promise)
        run-requests-fn (fn run-requests [requests result]
          (if (empty? requests)
            (deliver end-result {:id id :name (:name scenario) :start scenario-start :end (now) :requests result})
            (let [request (first requests)
                  start (now)]
              (go
                (let [response (callback->chan (request-fn request) id)
                      timeout  (async/timeout step-timeout)
                      [s channel] (async/alts! [response timeout])
                      success (if (= channel timeout)
                                false
                                s)]
                  (run-requests (rest requests) (conj result (request-result id (:name request) success start))))))))]
    (run-requests-fn (:requests scenario) [])
    end-result)))

(defn- run-nth-scenario-with-multiple-users [scenarios users step-timeout rounds duration i]
  (let [
        scenario-start (local-time/local-now)
        result (promise)
        scenario (nth scenarios i)
        runner (if (nil? duration)
                 (RoundsRunner. rounds)
                 (DurationRunner. duration))
        scenario-runs (map (partial run-scenario-async scenario step-timeout) (run-range runner users))]
     (println (str "Running scenario " (:name scenario) " with " users " users and " (runner-info runner) "."))
     (deliver result (run-scenarios-in-parallel scenario-runs users runner))
    result))

(defn run-simulation [scenarios users & [options]]
  (let [rounds (or (:rounds options) 1)
        duration (:duration options)
        step-timeout (or (:timeout-in-ms options) 5000)
        scenario-runner (partial run-nth-scenario-with-multiple-users scenarios users step-timeout rounds duration)
        results (run-parallel-and-collect-results scenario-runner (count scenarios))]
    (flatten results)))

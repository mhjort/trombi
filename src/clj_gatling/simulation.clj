(ns clj-gatling.simulation
  (:require [org.httpkit.client :as http]
            [clj-time.core :as time]
            [clj-time.local :as local-time]
            [clojure.core.async :as async :refer [go go-loop put! <!! alts! <! >!]]))

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

(defn- run-scenarios-in-parallel [scenario-runners parallel-count runner]
    (let [cs (repeatedly parallel-count async/chan)
          scenario-start (local-time/local-now)
          ps (map vector (iterate inc 0) cs)
          results (async/chan)]
      (doseq [[i c] ps]
        (go (>! c ((nth scenario-runners i)))))
      (let [count-c (go-loop [i 0]
                      (let [[result c] (async/alts! cs)]
                        (async/put! c ((nth scenario-runners (+ i parallel-count))))
                        (async/put! results result)
                      (if (continue-run? runner scenario-start i)
                        (recur (inc i))
                        i)))]
        (repeatedly (async/<!! count-c) (fn []  @(async/<!! results))))))

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

(defn- run-nth-scenario-with-multiple-users [scenarios users step-timeout requests duration i]
  (let [
        scenario-start (local-time/local-now)
        result (promise)
        scenario (nth scenarios i)
        runner (if (nil? duration)
                 (FixedRequestNumberRunner. requests)
                 (DurationRunner. duration))
        scenario-runs (map (partial run-scenario-async scenario step-timeout) (range))]
     (println (str "Running scenario " (:name scenario) " with " users " users and " (runner-info runner) "."))
     (deliver result (run-scenarios-in-parallel scenario-runs users runner))
    result))

(defn async-function-with-timeout [request timeout user-id result-channel]
  (let [now      #(System/currentTimeMillis)
        start    (now)
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

(defn run-scenario-version2 [concurrency number-of-requests timeout scenario]
  (let [cs       (repeatedly concurrency async/chan)
        ps       (map vector (iterate inc 0) cs)
        results  (async/chan)
        requests (-> scenario :requests)]
    (doseq [[user-id c] ps]
      (run-requests requests timeout user-id c))
    (go-loop [^long i 0]
      (let [[result c] (alts! cs)]
        (when (< i (- number-of-requests concurrency))
          (run-requests requests timeout (+ i concurrency) c))
        (>! results {:name (:name scenario)
                     :id (:id result)
                     :start (:start (first result))
                     :end (:end (last result))
                     :requests result})
        (when (<= i number-of-requests)
          (recur (inc i)))))
    (repeatedly number-of-requests #(<!! results))))


(defn run-simulation [scenarios users & [options]]
  (let [requests (or (:requests options) users)
        duration (:duration options)
        step-timeout (or (:timeout-in-ms options) 5000)]
        ; TODO The new implementation utilizes core.async better and it can generate
        ;      more load. However, it currently supports only simple one scenario case
        (if (and (nil? duration) (= 1 (count scenarios)))
          (run-scenario-version2 users requests step-timeout (first scenarios))
          (let [scenario-runner (partial run-nth-scenario-with-multiple-users scenarios users step-timeout requests duration)
                results (run-parallel-and-collect-results scenario-runner (count scenarios))]
                (flatten results)))))

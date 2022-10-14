(ns metrics-simulation.simulations
  (:require [clojure.core.async :refer [chan go >!]]
            [org.httpkit.client :as http]))

(def base-url "http://localhost:8888")

(defn- http-get [url _]
  (let [response (chan)
        check-status (fn [{:keys [status]}]
                       (go (>! response (= 200 status))))]
    (http/get (str base-url url) {} check-status)
    response))

(defn- http-get-with-ids [url ids {:keys [user-id]}]
  (let [response (chan)
        check-status (fn [{:keys [status]}]
                       (go (>! response (= 200 status))))
        id (nth ids user-id)]
    (http/get (str base-url url id) {} check-status)
    response))

(def ping
  (partial http-get "/ping"))

(def ping-simulation
  {:name "Ping simulation"
   :scenarios [{:name "Ping scenario"
                :steps [{:name "Ping Endpoint" :request ping}]}]})

(def article-read
  (partial http-get-with-ids "/metrics/article/read/" (cycle (range 100 200))))

(def program-start
  (partial http-get-with-ids "/metrics/program/start/" (cycle (range 200 400))))

(def metrics-simulation
  {:name "Metrics simulation"
   :pre-hook (fn [_] (println "pre-hook"))
   :post-hook (fn [_] (println "post-hook"))
   :scenarios [{:name "Article read scenario"
                :weight 2
                :steps [{:name "Article read request"
                         :request article-read}]}
               {:name "Program start scenario"
                :weight 1
                :steps [{:name "Program start request"
                         :request program-start}]}]})

(def simulations
  {:ping ping-simulation
   :metrics metrics-simulation})

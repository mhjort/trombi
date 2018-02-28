(ns clj-gatling.legacy-util
  (:require [clj-gatling.httpkit :as http]
            [clojure.set :refer [rename-keys]]
            [clojure.core.async :as async :refer [put!]]))

(set! *warn-on-reflection* true)

(defn- convert-legacy-fn [request]
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

(defn legacy-scenarios->scenarios [scenarios]
  (let [request->step (fn [request]
                        (-> request
                            (assoc :request (convert-legacy-fn request))
                            (dissoc :fn :http)))]
    (map (fn [scenario]
           (-> scenario
               (update :requests #(map request->step %))
               (rename-keys {:requests :steps})
               (dissoc :concurrency :weight)))
         scenarios)))

(defn legacy-reporter->reporter [reporter-key reporter simulation]
  (-> reporter
      (assoc :reporter-key reporter-key)
      (assoc :init (constantly nil))
      (assoc :combiner concat)
      (update :generator #(fn [summary] (% simulation)))
      (rename-keys {:writer :parser})
      (update :parser #(fn [simulation {:keys [batch-id batch]}]
                         (% simulation batch-id batch)))))

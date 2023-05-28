(ns syklotroni.legacy-util
  (:require [syklotroni.httpkit :as http]
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

(defn legacy-reporter->reporter [reporter-key {:keys [writer generator]} simulation]
 {:reporter-key reporter-key
  :collector (fn [_]
               {:collect (fn [simulation {:keys [batch-id batch]}]
                           (writer simulation batch-id batch))
                :combine concat})
  :generator (fn [_]
               {:generate (fn [_]
                           (generator simulation))
                :as-str str})})

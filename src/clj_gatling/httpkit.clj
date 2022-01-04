(ns clj-gatling.httpkit
  (:require [org.httpkit.client :as http])
  (:import  [org.httpkit PrefixThreadFactory]
            [org.httpkit.client HttpClient]
            [java.util.concurrent ThreadPoolExecutor LinkedBlockingQueue TimeUnit]))

(defonce cache-dns-forever (java.security.Security/setProperty "networkaddress.cache.ttl" "-1"))

(defonce pool-size (* 2 (.availableProcessors (Runtime/getRuntime))))

(defonce httpkit-pool (repeatedly pool-size #(HttpClient.)))

(defn- get-httpkit-client [^long idx]
  (nth httpkit-pool (mod idx pool-size)))

(defonce httpkit-callback-pool (let [pool-size (.availableProcessors (Runtime/getRuntime))
                                     queue (LinkedBlockingQueue.)
                                     factory (PrefixThreadFactory. "httpkit-callback-worker-")]
                                 (ThreadPoolExecutor. pool-size pool-size 60 TimeUnit/SECONDS queue factory)))

(defn async-http-request [url callback {:keys [user-id]}]
  (let [check-status (fn [{:keys [status]}]
                        (callback (= 200 status)))]
    (http/get url {:worker-pool httpkit-callback-pool :client (get-httpkit-client user-id)} check-status)))

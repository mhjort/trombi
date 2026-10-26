(require '[trombi.core :as trombi]
         '[org.httpkit.client :as http]
         '[org.httpkit.server :as server])

;; Fake system under test: responds in 5-50 ms, ~5% of requests fail
(server/run-server (fn [_]
                     (Thread/sleep (+ 5 (rand-int 45)))
                     {:status (if (< (rand) 0.05) 500 200)})
                   {:port 8080 :thread 200})

(defn get-page [path]
  (fn [_] (= 200 (:status @(http/get (str "http://localhost:8080" path))))))

(prn (trombi/run {:name "Demo"
                  :scenarios [{:name "Browse"
                               :steps [{:name "Login" :request (get-page "/login")}
                                       {:name "Front page" :request (get-page "/")}]}]}
                 {:concurrency 100
                  :duration (java.time.Duration/ofSeconds 8)}))

(System/exit 0)

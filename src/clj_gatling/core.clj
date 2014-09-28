(ns clj-gatling.core
  (:import (org.joda.time LocalDateTime))
  (:use [org.httpkit.server :only [run-server]])
  (:require [clojure-csv.core :as csv]
            [cheshire.core :as json]
            [compojure.core :refer [defroutes GET POST]]
            [compojure.handler :refer [site]]
            [compojure.route :as route]
            [ring.util.json-response :refer [json-response]]
            [clj-gatling.chart :as chart]
            [clj-gatling.report :as report]
            [clj-gatling.simulation :as simulation]))

(defn create-dir [dir]
  (.mkdirs (java.io.File. dir)))

(defn run-simulation [scenario users & [options]]
 (let [start-time (LocalDateTime.)
       results-dir (if (nil? (:root options))
                      "target/results"
                      (:root options))
       result (simulation/run-simulation scenario users options)
       csv (csv/write-csv (report/create-result-lines start-time result) :delimiter "\t" :end-of-line "\n")]
   (create-dir (str results-dir "/input"))
   (spit (str results-dir "/input/simulation.log") csv)
   (chart/create-chart results-dir)
   (println (str "Open " results-dir "/index.html"))))

(defroutes all-routes
  (GET "/ping" [] "pong")
  (route/files "/results/" {:root "target/results/output"})
  (POST "/api/runSimulation/" {body :body}
    (let [simulation (json/parse-string (slurp body) true)]
      (run-simulation (:scenarios simulation) (read-string (:users simulation)))
      (json-response {:results "http://localhost:3000/results/"})))
  )

(defn -main [& args]
  (let [handler (site all-routes)]
    (run-server handler {:port 3000}))
  (println "Server started at port 3000"))

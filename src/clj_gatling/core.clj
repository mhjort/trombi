(ns clj-gatling.core
  (:import (org.joda.time LocalDateTime))
  (:require [clojure-csv.core :as csv]
            [clojure.core.typed :as t]
            [clj-gatling.chart :as chart]
            [clj-gatling.report :as report]
            [clj-gatling.simulation :as simulation]))

(t/ann create-dir (t/IFn [String -> t/Any]))
(defn- create-dir [^String dir]
  (.mkdirs (java.io.File. dir)))

(t/ann ^:no-check clj-gatling.simulation/run-simulation (t/IFn [t/Any Long t/Any * -> t/Any]))
(t/ann ^:no-check clj-gatling.report/create-result-lines (t/IFn [LocalDateTime t/Any -> t/Any]))
(t/ann ^:no-check clj-gatling.chart/create-chart (t/IFn [t/Any -> t/Any]))
(t/ann ^:no-check clojure.core/spit (t/IFn [t/Any * -> t/Any]))
(t/ann ^:no-check clojure-csv.core/write-csv (t/IFn [t/Any * -> String]))

(t/ann run-simulation (t/IFn [t/Any Long t/Any * -> t/Any]))
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

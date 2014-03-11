(ns clj-gatling.chart-test
  (:use [clojure.test])
  (:require [clojure.java.io :as io]
            [clj-gatling.chart :as chart]))
 
(defn create-dir [dir]
  (.mkdirs (java.io.File. dir)))

(defn copy-file [source-path dest-path]
  (io/copy (io/file source-path) (io/file dest-path)))

(deftest creates-chart-from-simulation-file
  (create-dir "target/test-results/input")
  (copy-file "test/simulation.log" "target/test-results/input/simulation.log")
  (io/delete-file "target/test-results/output/index.html")
  (chart/create-chart "target/test-results")
  (is (.exists (io/as-file "target/test-results/output/index.html"))))

 


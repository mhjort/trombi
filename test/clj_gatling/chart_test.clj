(ns clj-gatling.chart-test
  (:use [clojure.test])
  (:require [clojure.java.io :as io]
            [clj-gatling.chart :as chart]
            [clj-gatling.simulation-util :as util]))

(defn copy-file [source-path dest-path]
  (io/copy (io/file source-path) (io/file dest-path)))

(defn delete-file-if-exists [path]
  (when (.exists (io/as-file path))
    (io/delete-file path)))

(deftest creates-chart-from-simulation-file
  (util/create-dir "target/test-results/input")
  (copy-file "test/simulation.log" "target/test-results/input/simulation.log")
  (delete-file-if-exists "target/test-results/index.html")
  (chart/create-chart "target/test-results")
  (is (.exists (io/as-file "target/test-results/index.html"))))

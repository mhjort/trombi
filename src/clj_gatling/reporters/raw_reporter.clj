(ns clj-gatling.reporters.raw-reporter
  (:require [clojure.java.io :as io])
  (:import (java.io BufferedWriter File FileWriter)))

(def collector
  (fn [_]
    {:collect (fn [_ {:keys [batch]}]
                batch)
     :combine concat}))

(def generator
  (fn [_]
    {:generate identity
     :as-str #(let [all-requests (mapcat :requests %)]
                (str "Finished " (count all-requests) " requests."))}))

(def in-memory-reporter
  {:reporter-key :raw
   :collector 'clj-gatling.reporters.raw-reporter/collector
   :generator 'clj-gatling.reporters.raw-reporter/generator})

(defn- write-ends-as-lines [file-name lines]
  (with-open [wtr (BufferedWriter. (FileWriter. file-name))]
    (loop [lines-left lines]
      (let [line (pr-str (first lines-left))]
        (.write  wtr line)
        (when (seq (rest lines-left))
          (.newLine wtr)
          (recur (rest lines-left)))))))

(defn- append-edns-as-lines [writer ends]
  (doseq [edn ends]
    (.write writer (pr-str edn))
    (.newLine writer)))

(defn- file->lines-seq [file-name]
  (let [lines (io/reader file-name)
        cleanup-fn (fn []
                     (.close lines)
                     (.delete (File. file-name)))]
    [(remove nil? (map read-string (line-seq lines))) cleanup-fn]))

(defn- raw-file-name [base-path]
  (str base-path "/raw.log"))

(def file-collector
  (fn [{:keys [results-dir]}]
    (let [writer (BufferedWriter. (FileWriter. (raw-file-name results-dir)))]
      {:collect (fn [_ {:keys [batch node-id batch-id]}]
                  (let [file-name (str results-dir "/batch-" node-id "-" batch-id ".log")]
                    (write-ends-as-lines file-name batch)
                    [writer file-name]))
       :combine (fn [param1 param2]
                  (doseq [[writer file-name] [param1 param2]]
                    (when-not (= file-name (raw-file-name results-dir))
                      (let [[lines-seq cleanup-fn] (file->lines-seq file-name)]
                        (append-edns-as-lines writer lines-seq)
                        (cleanup-fn))))
                  [writer (raw-file-name results-dir)])})))

(def file-generator
  (fn [{:keys [results-dir]}]
    {:generate (fn [[writer file-name]]
                 ;; In here we are sure that new results are not generated anymore
                 ;; and log collector writer can be closed
                 (.close writer)
                 ;; Note! Returned reader is never closed. For now this is by design
                 (first (file->lines-seq file-name)))
     :as-str (constantly (str "Generated raw report to " (raw-file-name results-dir)))}))

(def file-reporter
  {:reporter-key :raw
   :collector 'clj-gatling.reporters.raw-reporter/file-collector
   :generator 'clj-gatling.reporters.raw-reporter/file-generator})

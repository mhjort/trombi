(ns clj-gatling.reporters.short-summary
  (:require [clojure.set :refer [rename-keys]]))

(def generator
  (fn [_]
    {:generate identity
     :as-str (fn [{:keys [ok ko]
                   :or {ko 0 ok 0}}]
               (let [total (+ ok ko)]
                 (str "Total number of requests: " total
                      ", successful: " ok
                      ", failed: " ko ".")))}))

(def collector
  (fn [_]
    {:collect (fn [_ {:keys [batch]}]
                (let [freqs (frequencies (mapcat #(map :result (:requests %)) batch))]
                  ;;TODO Simulation should not return nil (it should return false instead)
                  (rename-keys freqs {true :ok false :ko nil :ko})))
     :combine #(merge-with + %1 %2)}))

(def reporter
  {:reporter-key :short
   :collector 'clj-gatling.reporters.short-summary/collector
   :generator 'clj-gatling.reporters.short-summary/generator})

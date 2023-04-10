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

(defn- mean [data]
  (Math/round (double (/ (reduce + data) (count data)))))

(defn- weighted-mean [value1 sample-size1 value2 sample-size2]
  ;Return weighted mean based on sample sizes
  (Math/round (double (/ (+ (* value1 sample-size1) (* value2 sample-size2))
                         (+ sample-size1 sample-size2)))))

(defn- combine [data1 data2]
  (let [sample-size1 (+ (:ok data1) (:ko data1))
        sample-size2 (+ (:ok data2) (:ko data2))]
    (-> data1
        (update :ok + (:ok data2))
        (update :ko + (:ko data2))
        (update-in [:response-time :global :min] min (-> data2
                                                         :response-time
                                                         :global
                                                         :min))
        (update-in [:response-time :global :max] max (-> data2
                                                         :response-time
                                                         :global
                                                         :max))
        ;To get accurate mean we should use all the data points for the calculation
        ;However, that would require either storing all the data points to memory
        ;or then writing intermediate results to disk
        ;short-summary tries to be fast and memory efficient.
        ;So we use weighted mean based on sample sizes which is accurate enough
        (update-in [:response-time :global :mean] weighted-mean
                   sample-size1
                   (-> data2
                       :response-time
                       :global
                       :mean)
                   sample-size2))))

(defn- collect [_ {:keys [batch]}]
  (let [request-results (mapcat #(map :result (:requests %)) batch)

        request-times (mapcat #(map (fn [{:keys [start end]}]
                                      (- end start)) (:requests %)) batch)
        freqs (frequencies request-results)
        ;;TODO Simulation should not return nil (it should return false instead)
        req-counts (merge-with + (rename-keys freqs {true :ok
                                                     false :ko
                                                     nil :ko})
                               {:ok 0 :ko 0})]
    (assoc req-counts :response-time {:global {:min (apply min request-times)
                                               :max (apply max request-times)
                                               :mean (mean request-times)}})))

(def collector
  (fn [_]
    {:collect collect
     :combine combine}))

(def reporter
  {:reporter-key :short
   :collector 'clj-gatling.reporters.short-summary/collector
   :generator 'clj-gatling.reporters.short-summary/generator})

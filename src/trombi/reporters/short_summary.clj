(ns trombi.reporters.short-summary)

(def generator
  (fn [_]
    {:generate identity
     :as-str (fn [{:keys [ok ko response-time]
                   :or {ko 0 ok 0}}]
               (let [total (+ ok ko)]
                 (str "Total number of requests: " total
                      ", successful: " ok
                      ", failed: " ko
                      " with mean " (-> response-time :global :mean) " ms.")))}))

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
  ;;Calculated in one pass because this is called for every batch of results
  (let [[ok ko min-time max-time sum-time]
        (reduce (fn [acc {:keys [requests]}]
                  (reduce (fn [[ok ko min-time max-time sum-time] {:keys [result start end]}]
                            (let [t (- end start)]
                              [(if (true? result) (inc ok) ok)
                               ;;TODO Simulation should not return nil (it should return false instead)
                               (if (true? result) ko (inc ko))
                               (if min-time (min min-time t) t)
                               (if max-time (max max-time t) t)
                               (+ sum-time t)]))
                          acc
                          requests))
                [0 0 nil nil 0]
                batch)]
    {:ok ok
     :ko ko
     :response-time {:global {:min min-time
                              :max max-time
                              :mean (Math/round (double (/ sum-time (+ ok ko))))}}}))

(def collector
  (fn [_]
    {:collect collect
     :combine combine}))

(def reporter
  {:reporter-key :short
   :collector 'trombi.reporters.short-summary/collector
   :generator 'trombi.reporters.short-summary/generator})

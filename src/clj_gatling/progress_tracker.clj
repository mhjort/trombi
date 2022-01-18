(ns clj-gatling.progress-tracker
  (:require [clj-gatling.simulation-runners :as runners])
  (:import  (java.time LocalDateTime)
            (java.util.concurrent Executors TimeUnit)))

(defn create-console-progress-tracker []
  (let [finished? (atom false)]
    (fn [{:keys [progress sent-requests total-concurrency]}]
      (when-not @finished?
        (let [progress-percent (int (* 100 progress))]
          (print (str "Progress: " progress-percent "%, concurrency: " total-concurrency " , sent requests: " sent-requests "\r"))
          (when (= 100 progress-percent)
            (reset! finished? true)
            (println ""))
          ;;Flush is required for forcing writing to console in every round
          (flush))))))

(defn start [{:keys [progress-tracker runner sent-requests start-time scenario-concurrency-trackers]}]
  (let [executor (Executors/newSingleThreadScheduledExecutor)
        stop-fn (fn []
                  (.shutdownNow executor))
        runnable (fn []
                   (try
                     (let [[progress _] (runners/calculate-progress runner @sent-requests start-time (LocalDateTime/now))
                           total-concurrency (reduce + (map deref (vals scenario-concurrency-trackers)))]
                       (progress-tracker {:progress progress
                                          :sent-requests @sent-requests
                                          :total-concurrency total-concurrency}))
                     (catch Exception e
                       (println "Failed to run progress tracker" progress-tracker "with exception" e))))]
    (.scheduleAtFixedRate executor runnable 10 200 TimeUnit/MILLISECONDS)
    stop-fn))

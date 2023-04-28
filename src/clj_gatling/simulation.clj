(ns clj-gatling.simulation
  (:require [clj-gatling.simulation-runners :as runners]
            [clj-gatling.schema :as schema]
            [clj-gatling.progress-tracker :as progress-tracker]
            [clj-gatling.simulation-util :refer [arg-count
                                                 choose-runner
                                                 clean-result
                                                 log-exception
                                                 weighted-scenarios]]
            [schema.core :refer [validate]]
            [clj-gatling.timers :as timers]
            [clojure.core.async :as async :refer [go go-loop close! alts! <! >! poll!]])
  (:import (java.time Duration LocalDateTime)))

(set! *warn-on-reflection* true)

(defn- now [] (System/currentTimeMillis))

(defn asynchronize [f ctx]
  (let [parse-result   (fn [result]
                         (if (instance? Throwable result)
                           {:result false :exception result}
                           {:result result}))
        parse-response (fn [response]
                         (if (vector? response)
                           (let [[result updated-context should-continue-scenario-or-nil?] response
                                 should-continue-scenario? (or (nil? should-continue-scenario-or-nil?)
                                                               should-continue-scenario-or-nil?)]
                             (assoc (parse-result result)
                                    :end-time (now)
                                    :context updated-context
                                    :should-continue-scenario? should-continue-scenario?))
                           (assoc (parse-result response)
                                  :end-time (now)
                                  :context ctx
                                  :should-continue-scenario? true)))]
    (go
      (try
        (let [response (f ctx)]
          (if (instance? clojure.core.async.impl.channels.ManyToManyChannel response)
            (parse-response (<! response))
            (parse-response response)))
        (catch Exception e
          {:result false :end-time (now) :context ctx :exception e})
        (catch AssertionError e
          {:result false :end-time (now) :context ctx :exception e})))))

(defn async-function-with-timeout [step timeout simulation-requests scenario-requests original-context]
  (swap! scenario-requests inc)
  (swap! simulation-requests (fn [r] (-> r (update :sent inc) (update :pending dec))))
  (go
    (when-let [sleep-before (:sleep-before step)]
      (<! (timers/timeout (sleep-before original-context))))
    (let [return {:name (:name step)
                  :id (:user-id original-context)
                  :start (now)
                  :context-before original-context}
          response (asynchronize (:request step) original-context)
          [{:keys [result end-time context exception should-continue-scenario?]} c] (alts! [response (timers/timeout timeout)])]
      (if (= c response)
        [(assoc return :end end-time
                       :exception exception
                       :result result
                       :context-after context)
         context
         should-continue-scenario?]
        [(assoc return :end (now)
                       :exception (ex-info "clj-gatling: request timed out" {:timeout-in-ms timeout})
                       :result false
                       :context-after original-context)
         original-context]))))

(defn- response->result [scenario result]
  {:name (:name scenario)
   :id (:id (first result))
   :start (:start (first result))
   :end (:end (last result))
   :requests result})

(defn- next-step [[steps step-fn] context]
  (cond
    (seq steps)
    [(first steps) context [(rest steps) nil]]

    (ifn? step-fn)
    (let [result (step-fn context)
          ret (if (vector? result)
                result
                [result context])]
      (conj ret [nil step-fn]))

    :else
    [nil context [nil nil]]))

(defn- continue-fn [{:keys [runner simulation-start force-stop]}]
  (fn [sent-requests next-run]
    (if @force-stop
      false
      (do (swap! simulation-start #(or % (LocalDateTime/now)))
          (runners/continue-run? runner sent-requests @simulation-start next-run)))))

(defn- scenario-context [options {:keys [pre-hook] :as scenario} user-id]
  (let [merged-context (merge (:context options) (:context scenario) {:user-id user-id})]
    (if pre-hook
      (pre-hook merged-context)
      merged-context)))

(defn- requests-per-scenario-run
  "Calculates the number of requests per run of a scenario. When explicit :steps
  have been provided, this is completely accurate. Where a :step-fn has been
  provided, we can only approximate based on the number of requests and runs the
  scenario has done so far"
  [scenario sent-requests runs]
  (if (:steps scenario)
    (count (:steps scenario))
    (if (or (= 0 sent-requests) (= 0 runs))
      1 ;;All we can do with no more info
      (int (Math/ceil (/ sent-requests runs))))))

(defn- run-scenario-once
  [{:keys [force-stop runner scenario-requests simulation-requests simulation-start] :as options}
   scenario
   context]
  (let [timeout (:timeout-in-ms options)
        result-channel (async/chan)
        skip-next-after-failure? (or (nil? (:skip-next-after-failure? scenario))
                                     (:skip-next-after-failure? scenario))
        request-failed? #(not (:result %))
        continue?         (continue-fn {:runner runner
                                        :force-stop force-stop
                                        :simulation-start simulation-start})
        should-terminate? #(let [requests @simulation-requests]
                             (and (:allow-early-termination? scenario)
                                  (not (continue? (+ (:sent requests) (:pending requests)) (LocalDateTime/now)))))
        step-ctx [(:steps scenario) (:step-fn scenario)]]
    (go-loop [[step context step-ctx] (next-step step-ctx context)
              results []]
      (let [[result new-ctx should-continue-scenario?] (<! (async-function-with-timeout step
                                                                                        timeout
                                                                                        simulation-requests
                                                                                        scenario-requests
                                                                                        context))
            [step' _ _ :as next-steps] (next-step step-ctx new-ctx)]
        (when-let [e (:exception result)]
          (log-exception (:error-file options) e))
        (if (or (should-terminate?)
                (nil? step')
                (and skip-next-after-failure?
                     (request-failed? result))
                (not should-continue-scenario?))
          (>! result-channel [(conj results (clean-result result)) context])
          (recur next-steps (conj results (clean-result result))))))
    result-channel))

(defn- prepare-run [scenario simulation-requests scenario-requests scenario-runs]
  (let [additional (requests-per-scenario-run scenario @scenario-requests @scenario-runs)
        [old _]    (swap-vals! simulation-requests #(update % :pending + additional))]
    [(+ (:sent old) (:pending old)) additional]))

(defn- complete-run [simulation-requests scenario-runs expected-requests actual-requests]
  (let [diff (- expected-requests actual-requests)]
    (swap! simulation-requests #(update % :pending - diff)))
  (swap! scenario-runs inc))

(defn- atomic-concurrency-check
  "Atomically check the concurrency and increment it if we should run now, then
  return whether we should run."
  [target-concurrency concurrent-scenarios]
  (let [[old new] (swap-vals! concurrent-scenarios #(if (> target-concurrency %) (inc %) %))]
    (> new old)))

(defn- run-concurrent-scenario-constantly
  [{:keys [concurrent-scenarios
           concurrency
           concurrency-distribution
           context
           force-stop
           init-sync
           runner
           scenario-requests
           scenario-runs
           simulation-requests
           simulation-start] :as options}
   {:keys [post-hook] :as scenario}
   user-id]
  (let [c                (async/chan)
        continue?        (continue-fn {:runner runner
                                       :simulation-start simulation-start
                                       :force-stop force-stop})
        should-run-now?  (if concurrency-distribution
                           (let [modifier-fn (if (= 2 (arg-count concurrency-distribution))
                                               (fn [{:keys [progress context]}]
                                                 (concurrency-distribution progress context))
                                               concurrency-distribution)]
                             #(let [[progress duration] (runners/calculate-progress runner
                                                                                    (:sent @simulation-requests)
                                                                                    @simulation-start
                                                                                    (LocalDateTime/now))
                                    target-concurrency (* concurrency (modifier-fn {:progress progress
                                                                                    :duration duration
                                                                                    :context context}))]
                                (atomic-concurrency-check target-concurrency concurrent-scenarios)))
                           #(atomic-concurrency-check concurrency concurrent-scenarios))
        starting-context (scenario-context options scenario user-id)]
    (swap! init-sync dec)
    (go-loop [context starting-context]
      (while (> @init-sync 0)
        (<! (timers/timeout 100)))
      (let [[old-total expected] (prepare-run scenario simulation-requests scenario-requests scenario-runs)]
        (while (and (continue? old-total (LocalDateTime/now)) (not (should-run-now?)))
          (<! (timers/timeout (rand-int 20))))
        (if (continue? (:sent @simulation-requests) (LocalDateTime/now))
          (do
            (let [[result final-context] (<! (run-scenario-once options scenario context))]
              (swap! concurrent-scenarios dec)
              (complete-run simulation-requests scenario-runs expected (count result))
              (>! c result)
              (when post-hook
                (post-hook final-context))
              (recur (scenario-context options scenario user-id))))
          (do
            (swap! concurrent-scenarios dec)
            (close! c)))))
    c))

(defn- run-at
  "Tells the thread when it should next trigger"
  [run-tracker interval-ns]
  ;;Randomise the run times by +- 0.25*interval, to prevent sync-ups when distributed
  (let [jitter (int (- (rand-int (/ interval-ns 2)) (/ interval-ns 4)))]
    (swap! run-tracker (fn [^LocalDateTime previous]
                         (if (nil? previous)
                           (LocalDateTime/now)
                           (.plusNanos previous (+ interval-ns jitter)))))))

(defn- rate->interval-ns
  "Converts a rate-per-second of requests to a nanosecond interval between requests"
  [rate]
  (->> rate
       (/ 1000)
       (* 1000000)))

(defn- run-rate-scenario-constantly
  [{:keys [concurrent-scenarios
           context
           force-stop
           init-sync
           rate-run-tracker
           runner
           rate-distribution
           scenario-requests
           scenario-runs
           simulation-requests
           simulation-start] :as options}
   {:keys [post-hook] :as scenario}
   user-id]
  (let [c            (async/chan)
        continue?    (continue-fn {:runner runner
                                   :simulation-start simulation-start
                                   :force-stop force-stop})
        rate         (:rate scenario)
        interval-ns  (if rate-distribution
                       (let [modifier-fn (if (= 2 (arg-count rate-distribution))
                                           (fn [{:keys [progress context]}]
                                             (rate-distribution progress context))
                                           rate-distribution)]
                         #(let [[progress duration] (runners/calculate-progress runner
                                                                                (:sent @simulation-requests)
                                                                                @simulation-start
                                                                                @rate-run-tracker)
                                target-rate (* rate (modifier-fn {:progress progress
                                                                  :duration duration
                                                                  :context context}))]
                            (rate->interval-ns target-rate)))
                       (constantly (rate->interval-ns rate)))
        starting-context (scenario-context options scenario user-id)]
    (swap! init-sync dec)
    (go-loop [context starting-context]
      (while (> @init-sync 0)
        (<! (timers/timeout 100)))
      (let [next-run (run-at rate-run-tracker (interval-ns))
            [old-total expected] (prepare-run scenario simulation-requests scenario-requests scenario-runs)]
        ;;This means we only wait if there are not already enough waiting
        ;;requests to complete the scenario
        (if (continue? old-total next-run)
          (let [t (LocalDateTime/now)]
            (when (.isBefore t next-run)
              (<! (timers/timeout (.toMillis (Duration/between t next-run)))))
            (swap! concurrent-scenarios inc)
            (let [[result final-context] (<! (run-scenario-once options scenario context))]
              (swap! concurrent-scenarios dec)
              (complete-run simulation-requests scenario-runs expected (count result))
              (>! c result)
              (when post-hook
                (post-hook final-context)))
            (recur (scenario-context options scenario user-id)))
          (close! c))))
    c))

(defn- print-scenario-info [scenario]
  (let [rate (:rate scenario)]
    (println "Running scenario" (:name scenario)
             (if rate
               (str "with rate " rate " users/sec")
               (str "with concurrency " (:concurrency scenario))))))

(defn- run-scenario [run-constantly-fn options scenario]
  (print-scenario-info scenario)
  (let [responses (async/merge (pmap #(run-constantly-fn options scenario %) (:users scenario)))
        results (async/chan)]
    (go-loop []
      (if-let [result (<! responses)]
        (do
          (>! results (response->result scenario result))
          (recur))
        (close! results)))
    results))

(defn scenario-trackers [scenarios initial-value]
  (reduce (fn [m k] (assoc m k (atom initial-value))) {} (map :name scenarios)))

(defn run-scenarios [{:keys [post-hook
                             context
                             runner
                             concurrency
                             concurrency-distribution
                             rate
                             rate-distribution
                             progress-tracker
                             default-progress-tracker] :as options}
                     scenarios]
  (println "Running simulation with"
           (runners/runner-info runner)
           (if rate
             (if rate-distribution
               (str "adding " rate " users/sec, with rate distribution function")
               (str "adding " rate " users/sec"))
             (if concurrency-distribution
               (str "using request concurrency " concurrency " with concurrency distribution function")
               (str "using request concurrency " concurrency))))
  (validate [schema/RunnableScenario] scenarios)
  (let [simulation-start (atom nil)
        init-sync (atom (reduce (fn [acc s] (+ acc (count (:users s)))) 0 scenarios))
        force-stop (atom false)
        force-stop-fn (fn []
                        (println "Force stop requested. Not starting new scenarios anymore")
                        (reset! force-stop true))
        scenario-request-trackers (scenario-trackers scenarios 0)
        scenario-run-trackers (scenario-trackers scenarios 0)
        scenario-concurrency-trackers (scenario-trackers scenarios 0)
        rate-run-trackers (when rate (scenario-trackers scenarios nil))
        simulation-requests (atom {:sent 0 :pending 0})
        ;;TODO Maybe use try-finally for stopping
        stop-progress-tracker (progress-tracker/start {:runner runner
                                                       :force-stop-fn force-stop-fn
                                                       :simulation-requests simulation-requests
                                                       :start-time simulation-start
                                                       :scenario-concurrency-trackers scenario-concurrency-trackers
                                                       :default-progress-tracker default-progress-tracker
                                                       :progress-tracker progress-tracker})
        run-scenario-with-opts (fn [{:keys [name] :as scenario}]
                                 (run-scenario (if rate
                                                 run-rate-scenario-constantly
                                                 run-concurrent-scenario-constantly)
                                               (assoc options
                                                 :concurrent-scenarios (get scenario-concurrency-trackers name)
                                                 :scenario-requests (get scenario-request-trackers name)
                                                 :scenario-runs (get scenario-run-trackers name)
                                                 :rate-run-tracker (get rate-run-trackers name)
                                                 :force-stop force-stop
                                                 :simulation-start simulation-start
                                                 :init-sync init-sync
                                                 :simulation-requests simulation-requests)
                                               scenario))
        responses (async/merge (map run-scenario-with-opts scenarios))
        results (async/chan)]
    (go-loop []
      (if-let [result (<! responses)]
        (do
          (>! results result)
          (recur))
        (do
          (close! results)
          (stop-progress-tracker)
          (when post-hook (post-hook context)))))
    {:results results :force-stop-fn force-stop-fn}))

(defn run [{:keys [scenarios pre-hook post-hook] :as simulation}
           {:keys [rate users context] :as options}]
  (validate schema/Simulation simulation)
  (let [final-ctx (merge context (when pre-hook (pre-hook context)))]
    (run-scenarios (assoc options
                          :context final-ctx
                          :post-hook post-hook
                          :runner (choose-runner scenarios
                                                 (count users)
                                                 options))
                   (weighted-scenarios users rate scenarios))))

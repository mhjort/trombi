(ns trombi.simulation
  (:require [trombi.simulation-runners :as runners]
            [trombi.schema :as schema]
            [trombi.progress-tracker :as progress-tracker]
            [trombi.simulation-util :refer [arg-count
                                                 choose-runner
                                                 clean-result
                                                 log-exception
                                                 weighted-scenarios]]
            [schema.core :refer [validate]]
            [clojure.core.async :as async :refer [close! <!! >!!]])
  (:import (java.time Duration LocalDateTime)
           (java.util.concurrent Callable ExecutionException ExecutorService Executors Future)))

(set! *warn-on-reflection* true)

(defn- now [] (System/currentTimeMillis))

;;Every virtual user and every request runs in its own virtual thread.
;;Blocking is cheap there, so the runner uses plain blocking code and does not
;;use go blocks (which run in core.async's shared dispatch pool).
(defonce ^:private ^ExecutorService virtual-threads
  (Executors/newVirtualThreadPerTaskExecutor))

(defn- submit ^Future [f]
  (.submit virtual-threads ^Callable f))

(defn- channel? [x]
  (instance? clojure.core.async.impl.channels.ManyToManyChannel x))

(defn- call-request [f ctx]
  (let [parse-result   (fn [result]
                         (if (instance? Throwable result)
                           {:result false :exception result}
                           {:result result}))
        parse-response (fn [response]
                         (if (vector? response)
                           (assoc (parse-result (first response)) :end-time (now) :context (second response))
                           (assoc (parse-result response) :end-time (now) :context ctx)))]
    (try
      (let [response (f ctx)]
        (parse-response (if (channel? response)
                          (<!! response)
                          response)))
      (catch Exception e
        {:result false :end-time (now) :context ctx :exception e})
      (catch AssertionError e
        {:result false :end-time (now) :context ctx :exception e}))))

(defn- run-step [step timeout simulation-requests scenario-requests original-context]
  (swap! scenario-requests inc)
  (swap! simulation-requests (fn [r] (-> r (update :sent inc) (update :pending dec))))
  (when-let [sleep-before (:sleep-before step)]
    (Thread/sleep (long (sleep-before original-context))))
  (let [return {:name (:name step)
                :id (:user-id original-context)
                :start (now)
                :context-before original-context}
        response (promise)
        ;;Request runs in a separate thread so that timeout works also for blocking requests
        _ (submit #(deliver response (call-request (:request step) original-context)))
        {:keys [result end-time context exception] :as r} (deref response timeout ::timeout)]
    (if (= ::timeout r)
      [(assoc return :end (now)
                     :exception (ex-info "trombi: request timed out" {:timeout-in-ms timeout})
                     :result false
                     :context-after original-context)
       original-context]
      [(assoc return :end end-time
                     :exception exception
                     :result result
                     :context-after context)
       context])))

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
      (do (when (nil? @simulation-start)
            ;;Avoid writing to the atom on every call. It is shared by all the users
            (compare-and-set! simulation-start nil (LocalDateTime/now)))
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
    (loop [[step context step-ctx] (next-step step-ctx context)
           results []]
      (let [[result new-ctx] (run-step step
                                       timeout
                                       simulation-requests
                                       scenario-requests
                                       context)
            [step' _ _ :as next-steps] (next-step step-ctx new-ctx)]
        (when-let [e (:exception result)]
          (log-exception (:error-file options) e))
        (if (or (should-terminate?)
                (nil? step')
                (and skip-next-after-failure?
                     (request-failed? result)))
          [(conj results (clean-result result)) context]
          (recur next-steps (conj results (clean-result result))))))))

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

;;Runs on the virtual thread of one user until the simulation ends
(defn- run-concurrent-scenario-constantly
  [{:keys [concurrent-scenarios
           concurrency
           concurrency-distribution
           context
           force-stop
           runner
           scenario-requests
           scenario-runs
           simulation-requests
           simulation-start] :as options}
   {:keys [post-hook] :as scenario}
   user-id
   starting-context
   emit]
  (let [continue?        (continue-fn {:runner runner
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
                           #(atomic-concurrency-check concurrency concurrent-scenarios))]
    (loop [context starting-context]
      (let [[old-total expected] (prepare-run scenario simulation-requests scenario-requests scenario-runs)
            ;;should-run-now? increments concurrency only when it returns true
            acquired? (loop []
                        (cond
                          (not (continue? old-total (LocalDateTime/now))) false
                          (should-run-now?) true
                          :else (do (Thread/sleep (long (rand-int 20)))
                                    (recur))))]
        (if (continue? (:sent @simulation-requests) (LocalDateTime/now))
          (do
            ;;Scenario can still run without acquiring concurrency when there
            ;;are pending requests. Track it so that the dec below stays balanced
            (when-not acquired?
              (swap! concurrent-scenarios inc))
            (let [[result final-context] (run-scenario-once options scenario context)]
              (swap! concurrent-scenarios dec)
              (complete-run simulation-requests scenario-runs expected (count result))
              (emit (response->result scenario result))
              (when post-hook
                (post-hook final-context))
              (recur (scenario-context options scenario user-id))))
          (when acquired?
            (swap! concurrent-scenarios dec)))))))

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

;;Runs on the virtual thread of one user until the simulation ends
(defn- run-rate-scenario-constantly
  [{:keys [concurrent-scenarios
           context
           force-stop
           rate-run-tracker
           runner
           rate-distribution
           scenario-requests
           scenario-runs
           simulation-requests
           simulation-start] :as options}
   {:keys [post-hook] :as scenario}
   user-id
   starting-context
   emit]
  (let [continue?    (continue-fn {:runner runner
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
                       (constantly (rate->interval-ns rate)))]
    (loop [context starting-context]
      (let [next-run (run-at rate-run-tracker (interval-ns))
            [old-total expected] (prepare-run scenario simulation-requests scenario-requests scenario-runs)]
        ;;This means we only wait if there are not already enough waiting
        ;;requests to complete the scenario
        (when (continue? old-total next-run)
          (let [t (LocalDateTime/now)]
            (when (.isBefore t next-run)
              (Thread/sleep (.toMillis (Duration/between t next-run))))
            (swap! concurrent-scenarios inc)
            (let [[result final-context] (run-scenario-once options scenario context)]
              (swap! concurrent-scenarios dec)
              (complete-run simulation-requests scenario-runs expected (count result))
              (emit (response->result scenario result))
              (when post-hook
                (post-hook final-context)))
            (recur (scenario-context options scenario user-id))))))))

(defn- print-scenario-info [{:keys [rate users] :as scenario}]
  (let [with-info (if rate
                    (str "with rate " rate " users/sec")
                    (str "with concurrency " (count users)))]
    (println "Running scenario" (:name scenario) with-info)))

(defn- await-all [futures]
  (mapv (fn [^Future f]
          (try
            (.get f)
            (catch ExecutionException e
              (throw (.getCause e)))))
        futures))

;;Users do not put results directly to the shared results channel. Putting from
;;thousands of threads to a single channel is slow because of contention.
;;Instead every user collects results to a local buffer and puts them as chunks.
(def ^:private result-chunk-size 100)

(defn- result-emitter [chunks]
  (let [buffer (volatile! [])]
    {:emit (fn [result]
             (when (>= (count (vswap! buffer conj result)) result-chunk-size)
               (>!! chunks @buffer)
               (vreset! buffer [])))
     :flush (fn []
              (when (seq @buffer)
                (>!! chunks @buffer)
                (vreset! buffer [])))}))

(defn- prepare-users
  "Creates starting contexts (runs scenario pre-hooks) for all the users of the
  scenario in parallel."
  [options scenario]
  (print-scenario-info scenario)
  (let [users (:users scenario)
        contexts (await-all (mapv (fn [user-id] (submit #(scenario-context options scenario user-id))) users))]
    (map (fn [user-id context] [options scenario user-id context]) users contexts)))

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
        force-stop (atom false)
        force-stop-fn (fn []
                        (println "Force stop requested. Not starting new scenarios anymore")
                        (reset! force-stop true))
        scenario-request-trackers (scenario-trackers scenarios 0)
        scenario-run-trackers (scenario-trackers scenarios 0)
        scenario-concurrency-trackers (scenario-trackers scenarios 0)
        rate-run-trackers (when rate (scenario-trackers scenarios nil))
        simulation-requests (atom {:sent 0 :pending 0})
        chunks (async/chan 1024)
        results (async/chan 1024)
        scenario-options (fn [{:keys [name]}]
                           (assoc options
                                  :concurrent-scenarios (get scenario-concurrency-trackers name)
                                  :scenario-requests (get scenario-request-trackers name)
                                  :scenario-runs (get scenario-run-trackers name)
                                  :rate-run-tracker (get rate-run-trackers name)
                                  :force-stop force-stop
                                  :simulation-start simulation-start
                                  :simulation-requests simulation-requests))
        run-constantly-fn (if rate
                            run-rate-scenario-constantly
                            run-concurrent-scenario-constantly)
        ;;Note! All the user contexts are created before starting any of the users
        ;;so that slow pre-hooks are not part of the simulation
        prepared-users (doall (mapcat #(prepare-users (scenario-options %) %) scenarios))
        ;;TODO Maybe use try-finally for stopping
        stop-progress-tracker (progress-tracker/start {:runner runner
                                                       :force-stop-fn force-stop-fn
                                                       :simulation-requests simulation-requests
                                                       :start-time simulation-start
                                                       :scenario-concurrency-trackers scenario-concurrency-trackers
                                                       :default-progress-tracker default-progress-tracker
                                                       :progress-tracker progress-tracker})
        users (mapv (fn [[user-options scenario user-id context]]
                      (submit #(let [{:keys [emit flush]} (result-emitter chunks)]
                                 (try
                                   (run-constantly-fn user-options scenario user-id context emit)
                                   (finally
                                     (flush))))))
                    prepared-users)
        ;;Single thread moves results from chunks to results channel
        forwarder (submit #(loop []
                             (when-some [chunk (<!! chunks)]
                               (doseq [result chunk]
                                 (>!! results result))
                               (recur))))]
    (submit (fn []
              (try
                ;;Wait for every user even if some of them fail
                (doseq [^Future user users]
                  (try
                    (.get user)
                    (catch ExecutionException e
                      (let [cause (.getCause e)]
                        (log-exception (:error-file options) cause)
                        (binding [*out* *err*]
                          (println "Unexpected error when running scenario:" cause))))))
                (close! chunks)
                (.get forwarder)
                (finally
                  (close! results)
                  (stop-progress-tracker)
                  (when post-hook (post-hook context))))))
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

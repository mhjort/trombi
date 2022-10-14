(ns clj-gatling.simulation-util
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.io File]
           [java.io StringWriter PrintWriter]
           [java.time Duration LocalDateTime]
           [java.time.format DateTimeFormatter]
           [clj_gatling.simulation_runners FixedRequestNumberRunner DurationRunner]))

(defn create-dir [^String dir]
  (.mkdirs (File. dir)))

(defn append-file
  "Append `contents` to file at `path`. The file is created
  if it doesn't already exist."
  [^String path contents]
  (with-open [w (io/writer path :append true)]
    (.write w contents)))

(defn path-join [& paths]
  (.getCanonicalPath (apply io/file paths)))

(defn exception->str
  "Convert an exception object to a string representation."
  [^Exception e]
  (let [sw (StringWriter.)
        pw (PrintWriter. sw)]
    (.printStackTrace e pw)
    (.toString sw)))

(defn log-exception
  "Log exception `e` to file at `path`."
  [path e]
  (append-file path (exception->str e)))

(defn- distinct-request-count [scenarios]
  (reduce +
          (map #(max (count (:steps %))
                     (count (:requests %))) ;For legacy support
               scenarios)))

(defn- split-by-weight [total weights]
  (let [sum-weights (reduce + weights)
        ;;We first try to split mathematically with rounding error
        ;;And then later adding reminder to first parts
        ;;This means we get a bit different results depending on the order of weights
        ;;(split-by-weight 10 [1 1 2]) -> [3 2 5]
        ;;(split-by-weight 10 [2 1 1]) -> [6 2 2]
        ;;In this tool this is not an issue
        xs (map #(max 1 (int (* total (/ % sum-weights)))) weights)
        mismatch (- total (reduce + xs))]
    (if (nat-int? mismatch)
      (map #(+ %1 %2) xs (concat (repeat mismatch 1) (repeat total 0)))
      (throw (ex-info "Negative remainder found when splitting by weight. Increase total or reduce weight variance"
                      {:total total :weights weights})))))

(defn split-to-buckets [ids bucket-sizes]
  (loop [start-idx 0
         result []
         sizes bucket-sizes]
    (if (empty? sizes)
      result
      (recur (+ start-idx (first sizes))
             (conj result (subvec ids start-idx (+ start-idx (first sizes))))
             (drop 1 sizes)))))

;;https://stackoverflow.com/questions/10969708/parallel-doseq-for-clojure
(defn split-equally
  "Split a collection into a vector of (as close as possible) equally sized parts"
  [size coll]
  (loop [size size
         parts []
         coll coll
         c (count coll)]
    (if (<= size 0)
      parts
      (let [t (quot (+ c size -1) size)]
        (recur (dec size) (conj parts (take t coll)) (drop t coll) (- c t))))))

(defn split-number-equally
  "Split a number into a vector of (as close as possible) equally sized numbers"
  [size number]
  (loop [size size
         parts []
         number number]
    (if (<= size 0)
      parts
      (let [t (quot (+ number size -1) size)]
        (recur (dec size) (conj parts t) (- number t))))))

(defn weighted-scenarios
  ([users rate scenarios]
   {:pre [(>= (count users) (count scenarios))]}
   (let [weights        (map #(or (:weight %) 1) scenarios)
         weighted-users (split-to-buckets (vec users)
                                          (split-by-weight (count users) weights))
         weighted-rates (when rate (split-by-weight rate weights))]
     (if weighted-rates
       (map #(assoc (dissoc %1 :weight) :users %2 :rate %3)
            scenarios
            weighted-users
            weighted-rates)
       (map #(assoc (dissoc %1 :weight) :users %2)
            scenarios
            weighted-users))))
  ([users scenarios]
   (weighted-scenarios users nil scenarios)))

(defn- convert-joda-duration-to-java-duration [duration]
  (-> duration
      (.toStandardDuration)
      (.getMillis)
      (Duration/ofMillis)))

(defn- create-duration-runner [duration]
  (if (instance? Duration duration)
    (DurationRunner. duration)
    (let [converted (convert-joda-duration-to-java-duration duration)]
      (println "Deprecated Joda Time duration" duration "was converted to" converted)
      (DurationRunner. converted))))

(defn choose-runner [scenarios concurrency options]
  (let [duration (:duration options)
        requests (or (:requests options) (* concurrency (distinct-request-count scenarios)))]
    (if (nil? duration)
      (FixedRequestNumberRunner. requests)
      (create-duration-runner duration))))

(defn timestamp-str []
  (let [custom-formatter (DateTimeFormatter/ofPattern "yyyyMMddHHmmssSSS")]
    (.format custom-formatter (LocalDateTime/now))))

(defn create-report-name
  "Create a gatling compatible filename for report output: 'SimulationName-Timestamp'"
  [simulation-name]
  (let [sanitized-prefix (str/replace (or simulation-name "empty_name") #"[^a-zA-Z0-9_]" "")]
    (str sanitized-prefix "-" (timestamp-str))))

(defn symbol-namespace [^clojure.lang.Symbol simulation]
  (str "/"
       (clojure.string/join "/"
                            (-> simulation
                                (str)
                                (clojure.string/replace #"\." "/")
                                (clojure.string/replace #"-" "_")
                                (clojure.string/split #"/")
                                (drop-last)))))

(defn load-namespace [^clojure.lang.Symbol simulation]
  (let [loadable-ns (symbol-namespace simulation)]
    (when (not (= "/" loadable-ns)) ;No need to load if namespace is current ns
      (load loadable-ns))))

(defn eval-if-needed [instance-or-symbol]
  (if (symbol? instance-or-symbol)
    (do
      (load-namespace instance-or-symbol)
      (eval instance-or-symbol))
    instance-or-symbol))

(defn arg-count
  "Determines the number of arguments accepted by the provided function. Avoids reflection, and
  works with anonymous functions."
  [f]
  {:pre [(instance? clojure.lang.AFunction f)]}
  (->> f
       class
       .getDeclaredMethods
       (filter #(= "invoke" (.getName ^java.lang.reflect.Method %)))
       first
       ((fn [^java.lang.reflect.Method x] (.getParameterTypes x)))
       java.lang.reflect.Array/getLength))

(defn failure-message
  "Generates a Gatling-suitable failure message for returned errors. Drops the
  data from ExceptionInfo structures, as it would be too complex. Drops the
  class from AssertionError, Exception, ExceptionInfo, and Throwable, as it is
  not useful. Stringifies the error in all other cases."
  [ex]
  (if (or (some #{(class ex)} [AssertionError Exception Throwable])
          (instance? clojure.lang.ExceptionInfo ex))
    (ex-message ex)
    (str ex)))

(defn clean-result [result]
  (if (:exception result)
    (update result :exception failure-message)
    result))

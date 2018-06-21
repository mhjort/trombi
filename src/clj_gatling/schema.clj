(ns clj-gatling.schema
  (:require [schema.core :as s]))

(def Step
  {:name s/Str
   (s/optional-key :sleep-before) (s/make-fn-schema s/Int [[{}]])
   :request (s/make-fn-schema s/Any [[{:user-id s/Int}]])})

(def RunnableScenario
  {:name s/Str
   :users [s/Int]
   (s/optional-key :context) (s/pred map?)
   (s/optional-key :skip-next-after-failure?) Boolean
   (s/optional-key :allow-early-termination?) Boolean
   (s/optional-key :pre-hook) s/Any
   (s/optional-key :post-hook) s/Any
   (s/optional-key :steps) [Step]
   (s/optional-key :step-fn) (s/pred ifn?)})

(def Scenario
  {:name s/Str
   (s/optional-key :context) (s/pred map?)
   (s/optional-key :weight) s/Int
   (s/optional-key :skip-next-after-failure?) Boolean
   (s/optional-key :allow-early-termination?) Boolean
   (s/optional-key :pre-hook) s/Any
   (s/optional-key :post-hook) s/Any
   (s/optional-key :steps) [Step]
   (s/optional-key :step-fn) (s/pred ifn?)})

(def Simulation
  {:name s/Str
   (s/optional-key :pre-hook) s/Any
   (s/optional-key :post-hook) s/Any
   :scenarios [Scenario]})

(def CollectorInput
  {:context {}
   :results-dir s/Str})

(def Collector
  (s/make-fn-schema {:collect s/Any
                     :combine s/Any}
                    [[CollectorInput]]))

(def GeneratorInput
  {:context {}
   :results-dir s/Str})

(def Generator
  (s/make-fn-schema {:generate s/Any
                     :as-str s/Any}
                    [[GeneratorInput]]))

(def Reporter
  {:reporter-key s/Keyword
   :collector (s/either s/Symbol Collector)
   :generator (s/either s/Symbol s/Any)})

(def Executor
  (s/make-fn-schema {} [[s/Int Simulation {}]]))

(def Options
  {(s/optional-key :concurrency) s/Int
   (s/optional-key :root) s/Str
   (s/optional-key :executor) Executor
   (s/optional-key :nodes) s/Int
   (s/optional-key :timeout-in-ms) s/Int
   (s/optional-key :context) (s/pred map?)
   (s/optional-key :requests) s/Int
   (s/optional-key :duration) org.joda.time.ReadablePeriod
   (s/optional-key :concurrency-distribution) (s/make-fn-schema
                                                float
                                                [[float {}]])
   (s/optional-key :error-file) s/Str
   (s/optional-key :reporter) s/Any ;Legacy fn
   (s/optional-key :reporters) [Reporter]
   })

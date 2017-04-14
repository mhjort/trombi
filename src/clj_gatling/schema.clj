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
   :steps [Step]})

(def Scenario
  {:name s/Str
   (s/optional-key :context) (s/pred map?)
   (s/optional-key :weight) s/Int
   (s/optional-key :skip-next-after-failure?) Boolean
   (s/optional-key :allow-early-termination?) Boolean
   :steps [Step]})

(def Simulation
  {:name s/Str
   (s/optional-key :pre-hook) s/Any
   (s/optional-key :post-hook) s/Any
   :scenarios [Scenario]})

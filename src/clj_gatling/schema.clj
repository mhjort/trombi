(ns clj-gatling.schema
  (:require [schema.core :as s]))

(def Step
  {:name s/Str
   :action (s/make-fn-schema s/Any [[{:user-id s/Int}]])})

(def RunnableScenario
  {:name s/Str
   :users [s/Int]
   (s/optional-key :skip-next-after-failure?) Boolean
   :steps [Step]})


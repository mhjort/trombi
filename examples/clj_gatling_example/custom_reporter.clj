 (ns clj-gatling-example.custom-reporter
  (:require [clj-gatling.core :as gatling]))

(defn- random-failing-request [context]
  (let [result (rand-nth ["success" "error-1" "error-2"])]
    (if (= "success" result)
      [true context]
      [false (assoc context :error result)])))

(def simulation
  {:name "Randomly failing simulation"
   :scenarios [{:name "Scenario"
                :steps [{:name "Randomly failing" :request random-failing-request}]}]})


(defn run [concurrency]
  (let [error-frequencies (atom {})]
    (gatling/run simulation
                 {:concurrency concurrency
                  :reporter {:writer (fn [simulation idx results]
                                       (let [errors (->> (map :requests results)
                                                         (flatten)
                                                         (map :context-after)
                                                         (map :error)
                                                         (remove nil?))]
                                         (swap! error-frequencies (partial merge-with +) (frequencies errors))))
                             :generator (fn [simulation]
                                          (println "Error frequencies:" @error-frequencies))}})))

;(run 10)




(ns clj-gatling.example)

(def test-simu
  {:name "Test simulation"
   :scenarios [{:name "Test scenario"
                :steps [{:name "Step1" :request (fn [_]
                                                  (Thread/sleep 10)
                                                  true)}
                        {:name "Step2" :request (fn [_]
                                                  (Thread/sleep 10)
                                                  false)}]}]})

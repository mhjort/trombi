(ns clj-gatling.reporters.raw-reporter)

(def collector
  (fn [_]
    {:collect (fn [_ {:keys [batch]}]
                batch)
     :combine concat}))

(def generator
  (fn [_]
    {:generate identity
     :as-str #(let [all-requests (mapcat :requests %)]
                (str "Finished " (count all-requests) " requests."))}))

(def in-memory-reporter
  {:reporter-key :raw
   :collector 'clj-gatling.reporters.raw-reporter/collector
   :generator 'clj-gatling.reporters.raw-reporter/generator})

;TODO Implement file-reporter that writes results to file in collect phase and
;returns lazy sequence reader to that file. Idea is to not consume so much memory
;and make it possible to run simulations with millions of requests

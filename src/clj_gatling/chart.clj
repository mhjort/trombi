(ns clj-gatling.chart
  (:import (scala.collection.mutable HashMap)
           (io.gatling.charts.report ReportsGenerator)
           (io.gatling.charts.result.reader FileDataReader)
           (io.gatling.core.config GatlingConfiguration)
           (org.joda.time LocalDateTime)))

(defn create-chart [results-dir]
  (let [conf (HashMap.)]
    (.put conf "gatling.core.directory.results" results-dir)
    (GatlingConfiguration/setUp conf)
    (ReportsGenerator/generateFor "output" (FileDataReader. "input"))))

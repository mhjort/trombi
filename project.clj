(defproject clj-gatling "0.5.0"
  :description ""
  :url "http://github.com/mhjort/clj-gatling"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [clojure-csv/clojure-csv "2.0.1"]
                 [http-kit "2.1.18"]
                 [clj-time "0.8.0"]
                 [io.gatling/gatling-charts "2.0.3"]
                 [io.gatling.highcharts/gatling-charts-highcharts "2.0.3"]]
  :repositories { "excilys" "http://repository.excilys.com/content/groups/public" }
  :profiles {:dev {:dependencies [[clj-containment-matchers "0.9.3"]] }})

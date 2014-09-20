(defproject clj-gatling "0.0.5"
  :description ""
  :url "http://github.com/mhjort/clj-gatling"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [clj-containment-matchers "0.9.1"]
                 [org.clojure/core.async "0.1.267.0-0d7780-alpha"]
                 [clojure-csv/clojure-csv "2.0.1"]
                 [cheshire "5.3.1"]
                 [compojure "1.1.5"]
                 [ring/ring-devel "1.1.8"]
                 [ring/ring-core "1.1.8"]
                 [ring-json-response "0.2.0"]
                 [http-kit "2.1.16"]
                 [clj-time "0.6.0"]
                 [io.gatling/gatling-charts "2.0.0-RC5"]
                 [io.gatling.highcharts/gatling-charts-highcharts "2.0.0-RC5"]]
  :repositories { "excilys" "http://repository.excilys.com/content/groups/public" }
  :main clj-gatling.core)

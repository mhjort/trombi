(defproject clj-gatling "0.8.6"
  :description "Clojure library for load testing"
  :url "http://github.com/mhjort/clj-gatling"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/core.async "0.2.374"]
                 [clojure-csv/clojure-csv "2.0.2"]
                 [http-kit "2.2.0"]
                 [clj-time "0.13.0"]
                 [prismatic/schema "1.1.3"]
                 [io.gatling/gatling-charts "2.0.3"
                   :exclusions [com.typesafe.akka/akka-actor_2.10
                                org.jodd/jodd-lagarto
                                com.fasterxml.jackson.core/jackson-databind
                                net.sf.saxon/Saxon-HE]]
                 [io.gatling.highcharts/gatling-charts-highcharts "2.0.3"
                   :exclusions [io.gatling/gatling-app io.gatling/gatling-recorder]]]
  :repositories { "excilys" "http://repository.excilys.com/content/groups/public" }
  :profiles {:dev {:global-vars {*warn-on-reflection* false}
                   :source-paths ["examples"]
                   :dependencies [[clj-async-test "0.0.5"]
                                  [clj-containment-matchers "1.0.1"]] }}
  :aot [clj-gatling.simulation-runners])

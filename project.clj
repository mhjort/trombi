(defproject clj-gatling "0.9.0"
  :description "Clojure library for load testing"
  :url "http://github.com/mhjort/clj-gatling"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/core.async "0.2.374"]
                 [http-kit "2.2.0"]
                 [clj-time "0.13.0"]
                 [prismatic/schema "1.1.5"]
                 [clojider-gatling-highcharts-reporter "0.1.1"]]
  :profiles {:dev {:global-vars {*warn-on-reflection* false}
                   :source-paths ["examples"]
                   :dependencies [[clj-async-test "0.0.5"]
                                  [clj-containment-matchers "1.0.1"]] }}
  :aot [clj-gatling.simulation-runners])

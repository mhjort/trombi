(defproject clj-gatling "0.15.0"
  :description "Clojure library for load testing"
  :url "http://github.com/mhjort/clj-gatling"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/core.async "1.2.603"]
                 [http-kit "2.3.0"]
                 [clj-time "0.15.2"]
                 [prismatic/schema "1.1.12"]
                 [clojider-gatling-highcharts-reporter "0.2.2"]]
  :profiles {:dev {:global-vars {*warn-on-reflection* false}
                   :source-paths ["examples"]
                   :dependencies [[clj-async-test "0.0.5"]
                                  [org.clojure/test.check "1.0.0"]
                                  [clj-containment-matchers "1.0.1"]] }}
  :aot [clj-time.core clj-gatling.simulation-runners])

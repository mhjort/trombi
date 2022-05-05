(defproject clj-gatling "0.17.3"
  :description "Clojure library for load testing"
  :url "http://github.com/mhjort/clj-gatling"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [org.clojure/core.async "1.5.648"]
                 [http-kit "2.5.3"]
                 [prismatic/schema "1.2.0"]
                 [clojider-gatling-highcharts-reporter "0.4.0"]]
  :profiles {:dev {:global-vars {*warn-on-reflection* false}
                   :source-paths ["examples"]
                   :dependencies [[clj-time "0.15.2"]
                                  [clj-async-test "0.0.5"]
                                  [org.clojure/test.check "1.1.1"]
                                  [clj-containment-matchers "1.0.1"]]}}
  :aot [clj-gatling.simulation-runners])

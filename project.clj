(defproject com.github.mhjort/trombi "1.0.0-beta2"
  :description "Clojure library for load testing stateful apps"
  :url "http://github.com/mhjort/trombi"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.clojure/core.async "1.6.673"]
                 [prismatic/schema "1.4.1"]]
  :profiles {:dev {:global-vars {*warn-on-reflection* false}
                   :source-paths ["examples"]
                   :jvm-opts ["-Dclojure.tools.logging.factory=clojure.tools.logging.impl/log4j2-factory"]
                   :dependencies [[com.github.mhjort/trombi-gatling-highcharts-reporter "1.0.0"]
                                  [http-kit "2.6.0"]
                                  [clj-time "0.15.2"]
                                  [clj-async-test "0.0.5"]
                                  [org.clojure/test.check "1.1.1"]
                                  [clj-containment-matchers "1.0.1"]
                                  [org.clojure/tools.logging "1.2.4"]
                                  [org.apache.logging.log4j/log4j-api "2.20.0"]
                                  [org.apache.logging.log4j/log4j-core "2.20.0"]]}}
  :aot [trombi.simulation-runners])

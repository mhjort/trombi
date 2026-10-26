(defproject com.github.mhjort/trombi "2.0.1"
  :description "Clojure library for load testing stateful apps"
  :url "http://github.com/mhjort/trombi"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.12.6"]
                 [org.clojure/core.async "1.9.865"]
                 [prismatic/schema "1.4.2"]]
  :profiles {:dev {:global-vars {*warn-on-reflection* false}
                   :source-paths ["examples"]
                   :jvm-opts ["-Dclojure.tools.logging.factory=clojure.tools.logging.impl/log4j2-factory"]
                   :dependencies [[com.github.mhjort/trombi-gatling-highcharts-reporter "1.0.0"]
                                  [http-kit "2.8.1"]
                                  [clj-time "0.15.2"]
                                  [clj-async-test "0.0.5"]
                                  [org.clojure/test.check "1.1.3"]
                                  [clj-containment-matchers "1.0.1"]
                                  [org.clojure/tools.logging "1.3.1"]
                                  [org.apache.logging.log4j/log4j-api "2.26.1"]
                                  [org.apache.logging.log4j/log4j-core "2.26.1"]]}}
  :aot [trombi.simulation-runners])

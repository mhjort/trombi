(defproject metrics-simulation "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [com.github.mhjort/trombi "1.0.0-beta2"]
                 [ring/ring-core "1.9.6"]
                 [javax.servlet/servlet-api "2.5"]
                 [compojure "1.7.0"]]
  :aliases {"run-test-server" ["run" "-m" "metrics-simulation.test-server/run"]}
  :main metrics-simulation.core)

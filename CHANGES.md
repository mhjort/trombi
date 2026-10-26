# Changes

## 2.0.0

* Breaking: Java 21 or later is required
* Run virtual users and requests in virtual threads instead of core.async go blocks.
  Request functions that block (e.g. `clj-http`, JDBC) no longer need a tuned thread pool.
  In benchmarks throughput was 11x higher for blocking request functions, 3x higher for
  callback based request functions (e.g. `http-kit`) and the maximum throughput of the runner itself was ~10x higher
* Bump core.async to 1.9.865. The `clojure.core.async.pool-size` system property is not supported anymore.
  See README section Tuning parallelism
* Fix short summary reporter undercounting failed requests when a batch had both `false` and `nil` results
* Remove `trombi.simulation/asynchronize` and `trombi.simulation/async-function-with-timeout`, which were internal functions
* Bump dependencies (Clojure 1.12.6, Prismatic Schema 1.4.2)

## 1.0.2

* Validate simulation before starting the run. Previously an invalid simulation made `run` hang instead of throwing an error (#99)
* Fix file based raw reporter leaving `raw.log` empty when simulation produces fewer than 20000 results (#80)
* Fix progress reporter showing negative concurrency values in the end of the simulation (#89)

## 1.0.1

* Fix division by zero bug in experimental stats

## 1.0.0

* Project renamed to Trombi
* Gatling Highcharts and http-kit dependencies are not included by default
* Only short summary reporter is used when reporters config option is omitted
* Bump dependencies

## 0.18.0

* Fix a bug in concurrency weight between multiple scenarios
* Fix a race condition bug in force stop
* Fix a bug in reporter console output showing values too early
* Add global min, max and mean measurements to short summary

## 0.17.6

* Fix a bug in console progress tracker.
* Start runners in parallel to improve simulation results.
* Bump dependendencies & Clojure

## 0.17.5

* Add experimental support for tracking active thread count in test client

## 0.17.4

* Fix progress calculation. Progress should never exceed 1.0

## 0.17.3

* Add support for force stopping simulation from the progress tracker
* Add possibility to run simulation in background with new 'run-async` function in core

## 0.17.2

* Add support for latest version of Gatling Highcharts reports with percentiles
* Catch also AssertionError and categorise it as a step failure
* Bump dependencies

## 0.17.1

* Add support for stringified exceptions in reports

## 0.17.0

* Add support for simulations defined by rate (new requests/sec) rather than concurrency

## 0.16.1

* Add raw reporters
* Fix highcharts reporter simulation timestamp on report frontpage
* Update dependencies

## 0.16.0

* Add support for customizable progress tracker
* Replace deprecated clj-time with Java 8 time objects
* Upgrade Clojure from 1.10.1 to 1.10.3
* Update dependencies

## 0.15.1

* Fix: Verify that at least one user is running every scenario

## 0.15.0

* Use own implementation of timeout to avoid reusing same timeout. That was an issue with high concurrency
* Upgrade core.async

## 0.14.0

* Upgrade Clojure from 1.8.0 to 1.10.1
* Update dependencies

## 0.13.0

* Add support for dynamic scenarios
* Update dependencies.

## 0.12.0

* Internal refactoring. Possibility to add custom remote reporters and executors.
* Update dependencies. JDK9 support

## 0.11.0

* Add scenario hooks
* Update dependencies

## 0.10.2

* Fix weighted scenarios concurrency distribution algorithm

## 0.10.1

* Use Jenkins Gatling plugin compatible folder name for the results

## 0.10.0

* Support for concurrency distribution

## 0.9.3

* Bump dependencies

## 0.9.2

* Fix a NPE with running legacy simulation (pre 0.8)

## 0.9.1

* Move gatling-highcharts-reporter to own repository

## 0.9.0

* Add pre-hook and post-hook for simulation

## 0.8.6

* Add error (stacktrace) logging

## 0.8.5

* Add option for scenario specific context.

## 0.8.4

* Fix StackOverFlow error when running with large number of users (> 2500)

## 0.8.3

* Pass `:context-before` and `:context-after` to reporter.

## 0.8.2

* Add scenario option for allowing early termination.

## 0.8.1

* Add option for specifying your own custom reporter.

## 0.8.0

* Upgraded to Clojure 1.8.
* New format for specifying the simulation and options (with schema validation).
  Note! Backwards compatibility is kept for now.
* Support for sleeping before starting a request

## 0.7.x

* Buffer results to file system while simulation is running.
  This makes it possible to run clj-gatling for long period of time without
  running out of memory.

## 0.6.x

* Upgraded to Clojure 1.7.
* Changed format of request functions.
  Callback is the first parameter and user-id is not separate parameter anymore.
  You can get user-id from context via :user-id key.

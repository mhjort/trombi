# Changes

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

# Changes

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

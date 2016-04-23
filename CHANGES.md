# Changes

## 0.8.0

* Upgraded to Clojure 1.8.
* New format for specifying the simulation + schema validation.
  Note! Backwards compatibility is kept for now.
* Support for delay before starting request

## 0.7.x

* Buffer results to file system while simulation is running.
  This makes it possible to run clj-gatling for long period of time without
  running out of memory.

## 0.6.x

* Upgraded to Clojure 1.7.
* Changed format of request functions.
  Callback is the first parameter and user-id is not separate parameter anymore.
  You can get user-id from context via :user-id key.

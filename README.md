# clj-gatling [![Build Status](https://travis-ci.org/mhjort/clj-gatling.png?branch=master)](https://travis-ci.org/mhjort/clj-gatling)

Create and run performance tests using Clojure (and get fancy reports).
For reporting clj-gatling uses Gatling under the hood.

## Installation

Add the following to your `project.clj` `:dependencies`:

```clojure
[clj-gatling "0.4.0"]
```

## Usage

### Custom functions

Custom functions are the most flexible way for implementing tests.
Functions will get a user id and a callback as a parameter
and they must call the callback when they have done their job.

Ideally your testing functions should be asynchronous and non-blocking
to make sure the performance testing client machine can generate as much
as possible load.

By default clj-gatling uses timeout for 5000 ms for all functions.
You can override that behaviour by setting option :timeout-in-ms


```clojure

(use 'clj-gatling.core)

(defn example-request [user-id context callback]
  (future (println (str "Simulating request for user #" user-id))
          (Thread/sleep (rand 1000))
          (callback true)))

(run-simulation
  [{:name "Test-scenario"
   :requests [{:name "Example-request" :fn example-request}]}] 2)
```

You can run same scenario multiple times to generate constant load
within a longer time period by specifying option :requests.
Default number of requests is same as number of users (which means
run only once)


```clojure
(run-simulation [test-scenario] 10 {:requests 500})

```

You can run same scenario multiple times within given time period
to generate constant load by specifying option :duration.

```clojure
(require '[clj-time.core :as t])

(run-simulation [test-scenario] 10 {:duration (t/minutes 2)})

```

### Non-blocking HTTP

This method uses asynchronous http-kit under the hood. 
Get request succeeds if it returns http status code 200.

```clojure

(use 'clj-gatling.core)

(run-simulation
  [{:name "Test-scenario"
   :requests [{:name "Localhost request" :http "http://localhost"}]}] 100)
```

## License

Copyright (C) 2014 Markus Hjort

Distributed under the Eclipse Public License, the same as Clojure.

# clj-gatling

[![Build Status](https://travis-ci.org/mhjort/clj-gatling.png?branch=master)](https://travis-ci.org/mhjort/clj-gatling)
[![Dependencies Status](http://jarkeeper.com/mhjort/clj-gatling/status.png)](http://jarkeeper.com/mhjort/clj-gatling)

Create and run performance tests using Clojure (and get fancy reports).
For reporting clj-gatling uses Gatling under the hood.

## Notes for changes in 0.7.x

* Buffer results to file system while simulation is running.
  This makes it possible to run clj-gatling for long period of time without
  running out of memory.

## Notes for changes in 0.6.x

* Upgraded to Clojure 1.7.
* Changed format of request functions.
  Callback is the first parameter and user-id is not separate parameter anymore.
  You can get user-id from context via :user-id key.

## Installation

Add the following to your `project.clj` `:dependencies`:

```clojure
[clj-gatling "0.7.9"]
```

## Usage

### Simple example

This will make 100 simultaneous http get requests to localhost.
Single request is considered to be ok if it returns http status code 200.

```clojure

(use 'clj-gatling.core)

(run-simulation
  [{:name "Localhost test scenario"
   :requests [{:name "Root request" :http "http://localhost"}]}] 100)
```

Simulation run shows some important statistics in console and also
generates exactly the same kind of a html report that Gatling does.
(clj-gatling uses Gatling internally to do this)

### Defining test scenarios

clj-gatling runs scenarios concurrently. Scenario consists of one or
more steps called requests. Scenario is defined as a Clojure map.

```clojure

{:name "Order book scenario"
 :requests [{:name "Open frontpage" :fn open-frontpage}
            {:name "Select book"    :fn select-book
             :name "Pay order"      :fn pay-order}]}
```

Scenario above consists of three requests. Requests are defined as
a Clojure maps. In request you can specify actual action either
by giving keyword :http which will do http get request or by
giving :fn keyword which lets you to specify your own function.
The latter option is a preferred way in clj-gatling.

Your own functions should look like this:


```clojure

(defn open-frontpage [callback context]
  (let [was-call-succesful? (do-your-call-here)
    (callback was-call-succesful? context)))

```

Ideally your calls should be asynchronous and non-blocking.
That's why in function signature clj-gatling uses callback instead
of function return value. When calling callback function the first
parameter is boolean which tells clj-gatling whether call was
succesful.

The second parameter to callback is context that is passed through
requests within same scenario and virtual user. You can utilize it
in a following way:

```clojure

;In "Select book" request
(callback true (assoc context :book-id 1}))

;And then in next requst
(defn pay-order [callback context]
  (pay-order-call-with-book-id (:book-id context))
    ...)

```

### Multiple scenarios

If you want to run multiple scenarios in same simulation you
can specify how requests are divided between scenarios by giving
specifying :weight. If you run example below with concurrency
10 it will run "Order Book" with concurrency 8 and other scenario
with concurrency 2. If you do not specify weight it is always 1
which balances concurrency evenly between scenarios.

```clojure

[{:name "Order book"
  :weight 4
  :requests [{:name "Open frontpage" :fn open-frontpage}
             {:name "Select book"    :fn select-book
              :name "Pay order"      :fn pay-order}]}
 {:name "Add new book"
  :weight 1 [{:name "Add book"       :fn add-book}]}]

```

### Scenario options

#### Skipping requests after failure

By default clj-gatling will skip further requests in scenario if
previous request fails. You can turn this feature of in scenario
by specifying option :skip-next-after-failure?

```clojure

{:name "Scenario"
  :skip-next-after-failure? false
  :requests [{:name "Failing request" :fn fail}
             {:name "Next request"    :fn success}]}
```

### Global Options

#### Request timeout

By default clj-gatling uses timeout for 5000 ms for all functions.
You can override that behaviour by setting option :timeout-in-ms

#### Constant load

You can run same scenario multiple times to generate constant load
within a longer time period by specifying option :requests.
Default number of requests is same as number of users (which means
run only once). Note! The given number is minimum number of requests
clj-gatling will make. Due to design choice number of requests actually
made can go bit over that (e.g. 1001 instead of 1000).


```clojure
(run-simulation [test-scenario] 10 {:requests 500})

```

You can run same scenario multiple times within given time period
to generate constant load by specifying option :duration.

```clojure
(require '[clj-time.core :as t])

(run-simulation [test-scenario] 10 {:duration (t/minutes 2)})

```

### Examples

See example project a here: [clj-gatling-example](https://github.com/mhjort/clj-gatling-example)

## Why

AFAIK there are no other performance testing tool where you can specify
your tests in Clojure. In my opinion Clojure syntax is very good for
this purpose.


## Design Philosophy

### Real life scenarios

clj-gatling has same kind of an approach that Gatling has in this sense.
Idea is to simulate a situation where multiple users use your application
concurrently. Users do actions and based on the results do next actions etc.
After the simulation you comprehensive results (nice graphs etc.)

If you want to just execute single request with high level of concurrency
simpler tools like Apache Benchmark can do that. Of course, clj-gatling
can do that also but it might be an bit of an overkill for that job.

### No DSL

I am not a fan of complex DSLs. clj-gatling tries to avoid DSL approach.
Idea is that you should write just ordinary Clojure code to specify the
actions and the actual scenario definition is an map.

Note! This also means clj-gatling does not have full API for simulating
http requests. There is :http keyword for executing simple http get.
But anything more complex than that (http post, checking special
response codes etc.) you should do yourself. I recommend using http-kit
for that.

### Distributed load testing

[Clojider](http://clojider.io) is a tool that uses clj-gatling scenarios 
and AWS Lambda technology for running distributed load tests in the cloud.

## Contribute

Use [GitHub issues](https://github.com/mhjort/clj-gatling/issues) and [Pull Requests](https://github.com/mhjort/clj-gatling/pulls).

## License

Copyright (C) 2014 Markus Hjort

Distributed under the Eclipse Public License, the same as Clojure.

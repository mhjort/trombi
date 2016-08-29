# clj-gatling

[![Build Status](https://travis-ci.org/mhjort/clj-gatling.png?branch=master)](https://travis-ci.org/mhjort/clj-gatling)
[![Dependencies Status](http://jarkeeper.com/mhjort/clj-gatling/status.png)](http://jarkeeper.com/mhjort/clj-gatling)

Create and run load tests using Clojure (and get fancy reports).

Note! Version 0.8.0 includes few changes on how simulations are defined.
See more details in [change history](CHANGES.md). All changes are backwards
compatible. The old way of defining the simulation is still supported but
maybe deprecated in future. You can see documentation for old versions [here](README-old.md).

## Installation

Add the following to your `project.clj` `:dependencies`:

```clojure
[clj-gatling "0.8.2"]
```

## Usage

### Basic example

This will make 100 simultaneous http get requests (using http-kit library)
to localhost server. Single request is considered to be ok if the response
http status code is 200.

```clojure
(require '[clj-gatling.core :as clj-gatling])
(require '[org.httpkit.client :as http])

(defn localhost-request [_]
  (let [{:keys [status]} @(http/get "http://localhost")]
    (= status 200)))

(clj-gatling/run
  {:name "Simulation"
   :scenarios [{:name "Localhost test scenario"
                :steps [{:name "Root"
                         :request localhost-request}]}]}
  {:concurrency 100})
```

Running the simulation shows statistics in console and
generates a detailed html report.
(clj-gatling uses gatling-highcharts for reporting)

### Concepts

clj-gatling runs simulations to simulate load. Simulation consists of one or multiple
scenarios that will be run in parallel. One scenario contain one or multiple steps
that are run sequentially. One simulation is configured to run with a given number
of virtual users. As a result the tool returns response times (min, max, average, percentiles)
and requests per second.

Simulation is specified as a Clojure map like this:

```clojure
{:name "Simulation"
 :scenarios [{:name "Scenario1"
              :weight 2 ;Optional (default 1)
              :skip-next-after-failure? false ;Optional (default true)
              :allow-early-termination? true ;Optional (default false)
              :steps [{:name "Step 1"
                       :request step1-fn}
                      {:name "Step 2"
                       :sleep-before (constantly 500) ;Optional
                       :request step2-fn}]}
             {:name "Scenario2"
              :weight 1
              :steps [{:name "Another step"
                       :request another-step-fn}]}]}
```

#### Scenarios

You can define one or multiple scenarios. Scenarios are always run in parallel.
Concurrent users are divided between the scenarios based on their weights.
For example:

* Simulation concurrency: 100
* Scenario1 with weight 5 => concurrency 80
* Scenario2 with weight 1 => concurrency 20

Scenario weight is optional key with default value 1. In that case the users
are split evenly between the scenarios

#### Scenario steps

Each scenario consists of one or multiple steps. Steps are run always in sequence.
Step has a name and user specified function (request) which is supposed to call
the system under test (SUT). Function takes in a scenario context as a map and
has to return either directly as a boolean or then with core.async channel with
a message of type boolean.

```clojure
;Returning boolean directly
(defn request-returning-boolean [context]
  ;Call the system under test here
  true) ;Return true/false based on the result of the call
```

```clojure
;Returning core.async channel
(defn request-returning-channel [context]
  (go
     ;Call the system under test here using non-blocking call
     true)) ;Return true/false based on the result of the non-blocking call
```

The latter is the recommended approach. When you use that it makes clj-gatling able
to utilize (=share) threads and makes it possible to generate more load with one machine.
However, the former is probably easier to use at the beginning and is therefore a good
starting point when writing your first clj-gatling tests.

If the function returns a false clj-gatling counts the step as a failed. If a function
throws an exception it will be considered as a failure too. clj-gatling also provides a
global timeout (see Options) for a step. If a request function takes more time it will be
cancelled and step is again considered as a failure.

Note! clj-gatling reports only step failures and successes. At the moment there is no support for
different kinds of errors in reporting level. If you have a lot of errors in your test runs
the recommended practice is to have a exception catching logic inside of your request function
and log it there.

Context map contains all values that you specified as a simulation context when calling
`run` method (See options) and clj-gatling provided `user-id` value. The purpose of user-id
is to have a way to specify different functionality for different users. For example:

```clojure
(defn request-fn [{:keys [user-id]}]
  (go
    (if (odd? user-id)
      (open-document-a)
      (open-document-b-))))
```

If your scenario contains multiple steps you can also pass values from a step to next a step
inside an scenario instance (same user) by returning a tuple instead of a boolean.

```clojure
;step 1
(defn login [context]
  (go
    (let [user-name (login-to-system)]
      [true (assoc context :user-name user-name)])))

;step 2
(defn open-frontpage [context]
  (go
    (open-page-with-name (:user-name context))))
```


If you want a step to not launch immediately after previous step you can specify a step key `sleep-before`.
The value for that key is a user defined function that takes in the scenario context and has to return
number of milliseconds to wait before starting request function for that step.

By default clj-gatling won't call the next step in scenario if a previous step fails (returns false).
You can override this behaviour by setting `skip-next-after-failure?` to false at scenario level.

When clj-gatling terminates the simulation (either after the given duration or given requests) all running
scenarios will still finish. If scenario has multiple steps and takes long to run the simulation may take
some time to fully terminate. If you want to disable that feature in scenario level you can set
`allow-early-termination?` to true.

### Options

Second parameter to `clj-gatling.core/run` function is options map. Options map contains following keys:

```clojure
{:context {:environment "test"} ;Context that is passed to user defined functions. Defaults to empty map
 :timeout-in-ms 3000 ;Timeout for a request function. Defaults to 5000.
 :root "/tmp" ;Directory where cl-gatling temporary files and final results are written. Defaults to "target/results"
 :concurrency 100 ;Number of concurrent users clj-gatling tries to use. Default to 1.
 :requests 10000 ;Total number of requests to run before ending the simulation. Defaults to the number of steps in simulation
 :duration (clj-time.core/minutes 5)} ;The time to run the simulation. Note! If duration is given, requests value will be ignored
```

### Examples

See example project a here: [clj-gatling-example](https://github.com/mhjort/clj-gatling-example)

## Customization

If you don't want to use built-in reporting by gatling-highchars you can customize the reporting
by implementing it yourself and using clj-gatling for load generation. You can do that by passing
in `:reporter` map as an option. For example if you want to skip reporting totally pass this reporter:

```clojure
{:writer (fn [simulation idx results]
            ;Do nothing
          )
 :generator (fn [simulation]
              ;Do nothing
            )}
```

clj-gatling calls the writer function periodically (currently after each 20000 requests) and then
generator once when the simulation is over.

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

### Distributed load testing

[Clojider](http://clojider.io) is a tool that can run clj-gatling in a distributed manner.
It uses AWS Lambda technology for running distributed load tests in the cloud.

## Contribute

Use [GitHub issues](https://github.com/mhjort/clj-gatling/issues) and [Pull Requests](https://github.com/mhjort/clj-gatling/pulls).

## License

Copyright (C) 2014-2016 Markus Hjort

Distributed under the Eclipse Public License, the same as Clojure.

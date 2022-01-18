# clj-gatling

[![CircleCI](https://circleci.com/gh/mhjort/clj-gatling/tree/master.svg?style=svg)](https://circleci.com/gh/mhjort/clj-gatling/tree/master)

Create and run load tests using Clojure (and get fancy reports).

## Installation

Add the following to your `project.clj` `:dependencies`:

[![Clojars Project](https://img.shields.io/clojars/v/clj-gatling.svg)](https://clojars.org/clj-gatling)

> Note that `clj-time` dependency is not included anymore because from Java SE 8 onward, users are asked to migrate to java.time (JSR-310)
However, `clj-gatling` is backwards compatible and if you still want to use clj-time please add `[clj-time "0.15.2"]` as a dependency.

## Usage

### Basic example

This will make 100 simultaneous http get requests (using http-kit library)
to localhost server. A single request is considered to be ok if the response
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

clj-gatling runs simulations to simulate load. A simulation consists of one or multiple
scenarios that will be run in parallel. One scenario contains one or multiple steps
that are run sequentially. One simulation is configured to run with a given number
of virtual users. As a result the tool returns response times (min, max, average, percentiles)
and requests per second. Internally millisecond is used as a precision for benchmarks. Therefore
this is not suited for testing systems with less than one millisecond response times.

A simulation is specified as a Clojure map like this:

```clojure
{:name "Simulation"
 :pre-hook (fn [ctx] (do-some-setup) (assoc ctx :new-value value)) ;Optional
 :post-hook (fn [ctx] (do-some-teardown)) ;Optional
 :scenarios [{:name "Scenario1"
              :context ;Optional (default {})
              :weight 2 ;Optional (default 1)
              :skip-next-after-failure? false ;Optional (default true)
              :allow-early-termination? true ;Optional (default false)
              :pre-hook (fn [ctx] (scenario-setup) (assoc ctx :new-val value)) ;Optional
              :post-hook (fn [ctx] (scenario-teardown)) ;Optional
              :step-fn ;Optional. Can be used instead of list of steps
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

#### Global simulation Hooks

You can define a `pre-hook` function that is executed once before running the simulation.
Function takes in the `context` map. You can change the context (e.g. by adding new keys)
by returning new map. Also you can define a `post-hook` function which is called after
running the simulation.

#### Scenarios

You can define one or multiple scenarios. Scenarios are always run in parallel.
Concurrent users are divided between the scenarios based on their weights.
For example:

* Simulation concurrency: 100
* Scenario1 with weight 5 => concurrency 80
* Scenario2 with weight 1 => concurrency 20

Scenario weight is optional key with default value 1. In that case the users
are split evenly between the scenarios.

Scenarios are also able to specific their own additional context via the
optional `:context` key.

#### Scenario steps

Each scenario consists of one or multiple steps. Steps are run always in sequence.
Step has a name and user specified function (request) which is supposed to call
the system under test (SUT). Function takes in a scenario context as a map and
has to return either directly as a boolean or then with core.async channel with
a message of type boolean.

```clojure
;;Returning boolean directly
(defn request-returning-boolean [context]
  ;;Call the system under test here
  true) ;Return true/false based on the result of the call
```

```clojure
;;Returning core.async channel
(defn request-returning-channel [context]
  (go
     ;;Call the system under test here using non-blocking call
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
different kinds of errors in reporting level. All errors are logged to `target/results/<sim-name>/errors.log`.

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
;;step 1
(defn login [context]
  (go
    (let [user-name (login-to-system)]
      [true (assoc context :user-name user-name)])))

;;step 2
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

#### Scenario hooks

Scenario 'pre-hook' function is executed before running a scenario with single virtual user.
Scenario 'post-hook' function is executed after the scenario with user has finished. Post-hook will always be
executed (even when previous step fails).

#### Dynamic scenarios

Sometimes a pre-determined sequence of steps does not provide enough
flexibility to express the test scenario. In such a case, you may provide the
key `step-fn` instead with a function taking the current `context` and
returning a tuple specifying a `step` and a modified `context`. Returning a
`nil` step marks the end of the scenario.

Note! If `step-fn` never returns nil the simulation will run endlessly. To prevent that you can use
option `:allow-early-termination?`

### Options

Second parameter to `clj-gatling.core/run` function is options map. Options map contains following keys:

```clojure
{:context {:environment "test"} ;Context that is passed to user defined functions. Defaults to empty map.
 :timeout-in-ms 3000 ;Timeout for a request function. Defaults to 5000.
 :root "/tmp" ;Directory where cl-gatling temporary files and final results are written. Defaults to "target/results".
 :concurrency 100 ;Number of concurrent users clj-gatling tries to use. Default to 1.
 :concurrency-distribution ;Function for defining how the concurrent users are distributed during the simulation. Optional.
 :rate 100 ;Number of new requests to add per second. Note! If rate is given, concurrency value will be ignored.
 :rate-distribution ;Function for defining how the rate is adjusted during the simulation. Optional.
 :progress-tracker ;Function used for tracking simulation progress. Optional.
 :reporters ;List of reporters to use. Optional. If omitted short summary reporter and highchart reporter are used.
 :requests 10000 ;Total number of requests to run before ending the simulation. Defaults to the number of steps in simulation.
 :duration (java.time.Duration/ofMinutes 5) ;The time to run the simulation. Note! If duration is given, requests value will be ignored.
 :error-file "/tmp/error.log"} ;The file to log errors to. Defaults to "target/results/<sim-name>/error.log".
```

#### Ramp-up

If you only set the `concurrency` the clj-gatling will use same concurrency from the beginning till end.
If you want to have more control for that (for example ramp-up period) you can specify your own
concurrency distribution function. The concurrency and rate distribution functions both have a legacy (version < 0.17.0)
and new possible format.

In legacy mode, when the user-provided function is binary (2-arity), your function will be called with:
- The progress through the simulation (as defined by either `duration` or `requests`), as a floating point number that
  goes from 0.0 to 1.0.
- The scenario-level context.

e.g.

```clojure
(fn [progress context]
   (if (< progress 0.1)
      0.1
      1.0))
```

In new mode, the user-provided function should be unary (1-arity). The single argument provided will be a map, which can
be destructured at will, and allows extensibility for new arguments without breaking backwards compatability. Currently,
the provided keys are:
- `progress`: The percentage progress through the simulation (as defined by either `duration` or `requests`), as a
  floating point number that goes from 0.0 to 1.0.
- `duration`: The elapsed time the simulation has been running for.
- `context`: The scenario-level context.

e.g.

```clojure
(fn [{:keys [progress duration context]}]
   (if (< (.toSeconds duration) 10)
      0.1
      1.0))
```

Your distribution function should return a floating point number from 0.0 to 1.0. The concurrency/rate at that point in
time will be the requested `concurrency`/`rate` multiplied by the returned number.

#### Progress tracker

By default, clj-gatling will write the progress periodically (every 200 milliseconds) to console output.

If you want to disable this functionality you can specify option `:progress-tracker (fn [_])`.

If you want to define your own progress tracker function you can specify your own like this one:

```clojure
(fn [{:keys [progress sent-requests total-concurrency]}]
  (println "Progress:" progress ", sent requests:" sent-requests ", total concurrency:" total-concurrency))
```

Progress is floating point number that goes from 0.0 to 1.0 during the simulation.

#### Tuning parallelism

Internally clj-gatling uses `core.async`, which has a fixed size thread pool. For load test scripts that use a high performance, asynchronous, non-blocking I/O (e.g. `http-kit`) library this is not a big issue. However, for libraries that require a thread per get request (e.g. `clj-http`) this is a real limitation.

The latest version of `core.async` supports setting the thread pool size using system property `clojure.core.async.pool-size`. With that the thread pool could be set to match the concurrency used in the simulation.

###  Reporters

By default, clj-gatling generates two reports: Gatling Highcharts Report and a short summary report. Reporters generate report to stdout and some reporters can even generate results to file. When you call the `simulation/run` function it will return all the reports with reporter keys (e.g `:short` for the short summary reporter). If you don't want to use the default reports you can specify a list of reporters with the `:reporters` key in the options. Available reporters are following:

* `clj-gatling.reporters.short-summary/reporter` This reporter returns a summary with number of successful and failed requests.
* `clj-gatling.reporters.raw-reporter/in-memory-reporter` This reporter returns all the raw results (scenarios & requests with their start and end times). It stores results in memory. 
* `clj-gatling.reporters.raw-reporter/file-reporter` This reporter returns all the raw results (scenarios & requests with their start and end times). It stores results to file.
* `clojider-gatling-highcharts-reporter.core/gatling-highcharts-reporter` Generates a Gatling Highchart html report.

You can also specify your own custom reporter. Check https://github.com/mhjort/clj-gatling/blob/master/src/clj_gatling/reporters/raw_reporter.clj as an example.

### Examples

See example project here: [metrics-simulation](https://github.com/mhjort/clj-gatling/tree/master/examples/metrics-simulation)

## Change History

Note! Version 0.8.0 includes a few changes on how simulations are defined.
See more details in [change history](CHANGES.md). All changes are backwards
compatible. The old way of defining the simulation is still supported but
may be deprecated in future. You can see documentation for old versions [here](README-old.md).

## Jenkins

This is compatible with Jenkins Gatling Plugin. https://wiki.jenkins.io/display/JENKINS/Gatling+Plugin#GatlingPlugin-Configuration

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

### Development

By default Leiningen is used for development. There is also experimental support for deps.edn (See Makefile).

## License

Copyright (C) 2014-2021 Markus Hjort

Distributed under the Eclipse Public License, the same as Clojure.

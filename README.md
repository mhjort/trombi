# trombi

[![CI](https://github.com/mhjort/trombi/actions/workflows/ci.yml/badge.svg?branch=master)](https://github.com/mhjort/trombi/actions/workflows/ci.yml)

Conduct load tests with Clojure, obtaining results as Clojure data or graphical charts. This tool targets stateful applications needing scripted logic in tests.

![trombi running a simulation against a local test server](doc/demo/demo.gif)

## Installation

trombi requires Java 21 or later.

[![Clojars Project](https://img.shields.io/clojars/v/com.github.mhjort/trombi.svg)](https://clojars.org/com.github.mhjort/trombi)

Leiningen (`project.clj`):

```clojure
[com.github.mhjort/trombi "2.0.1"]
```

deps.edn:

```clojure
com.github.mhjort/trombi {:mvn/version "2.0.1"}
```

## What's new in 2.0.0

trombi 2.0.0 runs every virtual user and every request in its own
[virtual thread](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html)
instead of core.async go blocks. Request functions that block no longer need a tuned thread pool,
and one machine can generate much more load. Compared to 1.0.2 in benchmarks
(16 cores, 10ms responses):

| Request function style                     | 1.0.2         | 2.0.0         |
|--------------------------------------------|---------------|---------------|
| Blocking (e.g. `clj-http`, JDBC)           | 664 req/s     | 7,665 req/s   |
| Callback based (e.g. `http-kit`)           | 58k req/s     | 178k req/s    |
| Runner overhead (function returns `true`)  | 106k req/s    | 1.02M req/s   |

Measured response times stay accurate: the mean reported for 10ms responses was 11–12ms.

### Upgrading from 1.x

Most simulations work without code changes. Check the following:

* **Java 21 or later is required.**
* **Remove `-Dclojure.core.async.pool-size`** if you used it. core.async 1.8+ ignores it and
  blocking request functions do not need it anymore.
* **Request functions written as `go` blocks** run in core.async's own thread pool, which is
  unbounded since core.async 1.8. They still work, but can be slower and report higher response
  times under heavy load. Either rewrite them as plain blocking code or callbacks (see
  [Scenario steps](#scenario-steps)), or switch core.async to a fixed size pool as described in
  [Tuning parallelism](#tuning-parallelism).
* **Scenario `pre-hook` functions run in parallel** for all users before the simulation starts,
  so they must be safe to call concurrently.
* **Failed request counts may be higher** than with 1.x in the short summary if your request
  functions return `nil`. 1.x undercounted failures when a batch had both `false` and `nil` results.
* `trombi.simulation/asynchronize` and `trombi.simulation/async-function-with-timeout`
  have been removed. They were internal functions.

See [CHANGES.md](CHANGES.md) for the full list of changes.

## Usage

### Trivial example

This will make 100 simultaneous http get requests (using http-kit library)
to localhost server. A single request is considered to be ok if the response
http status code is 200.

```clojure
(require '[trombi.core :as trombi])
(require '[org.httpkit.client :as http])

(defn localhost-request [_]
  (let [{:keys [status]} @(http/get "http://localhost")]
    (= status 200)))

(trombi/run
  {:name "Simulation"
   :scenarios [{:name "Localhost test scenario"
                :steps [{:name "Root"
                         :request localhost-request}]}]}
  {:concurrency 100})
```

Running the simulation prints some statistics to stdout and returns a result like this:

```
{:ok 90 :ko 10 :response-time {:global {:min 20
                                        :max 500
                                        :mean 154}}}
```

Where `ok` means number of successful requests and `ko` number of failed ones. Response times are in milliseconds.

If you want to see a graphical report instead you can call `trombi` with additional options and add additional deps.
(See sections Reporters and Additional Dependencies for more details)

```clojure
(require '[trombi-gatling-highcharts-reporter.core])
(trombi/run your-simulation {:concurrency 100 :reporters [trombi-gatling-highcharts-reporter.core/reporter]})
```

This call will use Gatling Highcharts reporter and will generate a graphical report. Location of the report is returned and can also be found from stdout output.

Calling `run` function will block while simulation is running. If you want more control you can also call `run-async` function. It takes the same parameters as the synchronous call. However, it returns immediately and returns a map with the following keys:

- `results`: A promise that is delivered once the simulation finishes.
- `force-stop-fn`: Function that stops the execution of the simulation. Function does not take any parameters. Stopping does not kill scenarios/requests that are in progress. They will be finished before the exit.
  When simulation is force stopped `trombi` does not guarantee that results are very reliable. So it is better to ignore the results when you finish the simulation with force stop.

### Concepts

trombi runs simulations to simulate load. A simulation consists of one or multiple
scenarios that will be run in parallel. One scenario contains one or multiple steps
that are run sequentially. One simulation is configured to run with a given number
of virtual users or with given rate of new virtual users per second.
As a result the tool returns the number of successful and failed requests and response times
(min, max and mean). The Gatling Highcharts reporter (see [Reporters](#reporters)) additionally shows
percentiles and requests per second. Internally millisecond is used as a precision for benchmarks. Therefore
this is not suited for testing systems with less than one millisecond response times.

A simulation is specified as a Clojure map like this:

```clojure
{:name "Simulation"
 :pre-hook (fn [ctx] (do-some-setup) (assoc ctx :new-value value)) ;Optional
 :post-hook (fn [ctx] (do-some-teardown)) ;Optional
 :scenarios [{:name "Scenario1"
              :context {:locale "en"} ;Optional (default {})
              :weight 2 ;Optional (default 1)
              :skip-next-after-failure? false ;Optional (default true)
              :allow-early-termination? true ;Optional (default false)
              :pre-hook (fn [ctx] (scenario-setup) (assoc ctx :new-val value)) ;Optional
              :post-hook (fn [ctx] (scenario-teardown)) ;Optional
              ;Instead of :steps you can give :step-fn (see Dynamic scenarios)
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

#### Global simulation hooks

You can define a `pre-hook` function that is executed once before running the simulation.
Function takes in the `context` map. You can change the context (e.g. by adding new keys)
by returning new map. Also you can define a `post-hook` function which is called after
running the simulation.

#### Scenarios

You can define one or multiple scenarios. Scenarios are always run in parallel.
Concurrent users are divided between the scenarios based on their weights.
For example:

* Simulation concurrency: 100
* Scenario1 with weight 4 => concurrency 80
* Scenario2 with weight 1 => concurrency 20

Scenario weight is optional key with default value 1. In that case the users
are split evenly between the scenarios.

Scenarios are also able to specify their own additional context via the
optional `:context` key.

#### Scenario steps

Each scenario consists of one or multiple steps. Steps are run always in sequence.
Step has a name and user specified function (request) which is supposed to call
the system under test (SUT). Function takes in a scenario context as a map and
has to return either directly a boolean or a core.async channel that delivers a boolean.

```clojure
;;Returning boolean directly
(defn request-returning-boolean [context]
  ;;Call the system under test here
  true) ;Return true/false based on the result of the call
```

trombi runs every virtual user and every request in its own
[virtual thread](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html),
so a request function that blocks (e.g. using `clj-http` or JDBC) does not block other users.
Returning a boolean directly is therefore the easiest way to get started.

If you use an asynchronous client library (e.g. `http-kit`), the most efficient way is to
return a channel that the client's callback delivers the result to:

```clojure
;;Returning core.async channel
(defn request-with-callback [context]
  (let [result (promise-chan)]
    (http/get "http://localhost" {} (fn [{:keys [status]}] (put! result (= status 200))))
    result))
```

Note! Code inside `go` blocks runs in core.async's own thread pool. If your request functions
are mostly `go` blocks see [Tuning parallelism](#tuning-parallelism).

If the function returns false trombi counts the step as failed. If a function
throws an exception it will be considered as a failure too. trombi also provides a
global timeout (see Options) for a step. If a request function takes more time, trombi
stops waiting for it and the step is again considered as a failure. Note that the
request function itself is not interrupted.

Note! trombi reports only step failures and successes. At the moment there is no support for
different kinds of errors in reporting level. All errors are logged to
`target/results/<SimulationName>-<timestamp>/error.log` (see `:error-file` option).

Context map contains all values that you specified as a simulation context when calling
`run` method (See options) and trombi provided `user-id` value. The purpose of user-id
is to have a way to specify different functionality for different users. For example:

```clojure
(defn request-fn [{:keys [user-id]}]
  (if (odd? user-id)
    (open-document-a)
    (open-document-b)))
```

If your scenario contains multiple steps you can also pass values from a step to the next step
inside a scenario instance (same user) by returning a tuple instead of a boolean.

```clojure
;;step 1
(defn login [context]
  (let [user-name (login-to-system)]
    [true (assoc context :user-name user-name)]))

;;step 2
(defn open-frontpage [context]
  (open-page-with-name (:user-name context)))
```


If you want a step to not launch immediately after previous step you can specify a step key `sleep-before`.
The value for that key is a user defined function that takes in the scenario context and has to return
number of milliseconds to wait before starting request function for that step.

By default trombi won't call the next step in scenario if a previous step fails (returns false).
You can override this behaviour by setting `skip-next-after-failure?` to false at scenario level.

When trombi terminates the simulation (either after the given duration or given requests) all running
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

Second parameter to `trombi.core/run` function is options map. Options map contains following keys:

```clojure
{:context {:environment "test"} ;Context that is passed to user defined functions. Defaults to empty map.
 :timeout-in-ms 3000 ;Timeout for a request function. Defaults to 5000.
 :root "/tmp" ;Directory where trombi temporary files and final results are written. Defaults to "target/results".
 :concurrency 100 ;Number of concurrent users trombi tries to use. Defaults to 1.
 :concurrency-distribution ;Function for defining how the concurrent users are distributed during the simulation. Optional.
 :rate 100 ;Number of new scenario runs (virtual users) to start per second. Note! If rate is given, concurrency value will be ignored.
 :rate-distribution ;Function for defining how the rate is adjusted during the simulation. Optional.
 :progress-tracker ;Function used for tracking simulation progress. Optional.
 :reporters ;List of reporters to use. Optional. If omitted only short summary reporter is used.
 :requests 10000 ;Total number of requests to run before ending the simulation. Defaults to concurrency multiplied by the number of steps in simulation.
 :duration (java.time.Duration/ofMinutes 5) ;The time to run the simulation. Note! If duration is given, requests value will be ignored.
 :error-file "/tmp/error.log" ;The file to log errors to. Defaults to "target/results/<SimulationName>-<timestamp>/error.log".
 :experimental-test-runner-stats? true} ;Print thread statistics of the test runner. Defaults to false. See Tuning the test runner.
```

#### Ramp-up

If you only set the `concurrency` trombi will use same concurrency from the beginning till end.
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
be destructured at will, and allows extensibility for new arguments without breaking backwards compatibility. Currently,
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

By default, trombi will write the progress periodically (every 200 milliseconds) to console output.

If you want to disable this functionality you can specify option `:progress-tracker (fn [_])`.

Following keys are passed to progress tracker function:
- `progress`: Progress as a floating point number between 0.0 and 1.0.
- `sent-requests`: Number of requests sent so far
- `total-concurrency`: How many scenario runs (virtual users) are in progress at the moment
- `default-progress-tracker`: Function for default behaviour. This can be used to also call the default tracker from user-provided progress tracker
- `force-stop-fn`: Function that stops the execution of the simulation. Function does not take any parameters. Stopping does not kill scenarios/requests that are in progress. They will be finished before the exit.

e.g.

```clojure
(fn [{:keys [progress sent-requests total-concurrency default-progress-tracker force-stop-fn] :as params}]
  (println "Progress:" progress ", sent requests:" sent-requests ", total concurrency:" total-concurrency)
  (default-progress-tracker params) ;Call default behaviour
)
```

#### Tuning parallelism

trombi runs virtual users and requests in virtual threads, so there is no thread pool to tune
for request functions that block or that return a channel delivered by a callback.

`go` blocks in your own request functions are different: they run in core.async's dispatch thread pool.
Since core.async 1.8 that pool is an unbounded cached thread pool. When thousands of `go` blocks are
runnable at the same time it can create a lot of threads, and the test runner itself may become the
bottleneck. If your request functions are mostly `go` blocks you can switch core.async back to a
fixed size pool with the system property `clojure.core.async.executor-factory`:

```clojure
(ns my-simulation.executors
  (:import (java.util.concurrent Executors)))

(defn fixed-pool [workload]
  (when (= workload :core-async-dispatch)
    (Executors/newFixedThreadPool 8)))
```

```
-Dclojure.core.async.executor-factory=my-simulation.executors/fixed-pool
```

Note! The older system property `clojure.core.async.pool-size` is not supported anymore since core.async 1.8.

### Reporters

By default, trombi generates one report which is a short summary report.

When you call the `trombi/run` function without the `:reporters` option it returns the short summary directly
(see Trivial example). When you give the `:reporters` option it returns a map with all the reports under their
reporter keys (e.g. `:short` for the short summary reporter and `:raw` for the raw reporters).
Reporters also generate report to stdout and some reporters may generate results to file.

If you don't want to use the default reports you can specify a list of reporters with the `:reporters` key in the options. Available reporters are following:

* `trombi.reporters.short-summary/reporter` This reporter returns a summary with number of successful and failed requests. In addition to that global min, max and mean is reported.
* `trombi.reporters.raw-reporter/in-memory-reporter` This reporter returns all the raw results (scenarios & requests with their start and end times). It stores results in memory.
* `trombi.reporters.raw-reporter/file-reporter` This reporter returns all the raw results (scenarios & requests with their start and end times). It stores results to file.
* `trombi-gatling-highcharts-reporter.core/reporter` Generates a Gatling Highchart html report (See Additional Dependencies).

You can also specify your own custom reporter. Check https://github.com/mhjort/trombi/blob/master/src/trombi/reporters/raw_reporter.clj as an example.

### Additional dependencies

`trombi` by default uses only few libraries. However, it supports some features that might need additional dependencies.

If you want to use Gatling Highcharts reporter you have to include the dependency `[com.github.mhjort/trombi-gatling-highcharts-reporter "1.0.0"]`.

If you want to use http-kit as your http client library you have to include the dependency `[http-kit "2.8.1"]`.

Note that in `trombi` clj-time dependency is not included anymore because from Java SE 8 onward, users are asked to migrate to java.time (JSR-310).
However, `trombi` is backwards compatible and if you still want to use clj-time please add `[clj-time "0.15.2"]` as a dependency.


### Examples

See example project here: [metrics-simulation](https://github.com/mhjort/trombi/tree/master/examples/metrics-simulation)

[Here](https://youtube.com/watch?v=4yQw8aaA_DQ) is a presentation on how to test stateful applications with trombi.

### Tuning the test runner

In load testing the goal is to generate load to the system under the test. However, sometimes the test runner can be also a bottleneck.
trombi runs virtual users and requests in virtual threads, which are cheap. This way the test runner does not need to use
many platform threads and it is possible to generate a huge amount of requests from a single machine. To track this behaviour there is
an experimental support for tracking active platform thread count in test client. By setting `:experimental-test-runner-stats? true` you can get
statistics about thread count during the test simulation. In the end trombi will produce following output to console:

`Test runner statistics: {:active-thread-count {:average 30, :max 33}}`

Virtual threads are not included in these numbers. In general these numbers should be lower than the number of concurrency in the simulation.
And when increasing the concurrency these numbers should not increase accordingly. If they do, check whether your request functions
use `go` blocks (see [Tuning parallelism](#tuning-parallelism)).

## Change History

### From clj-gatling to trombi

This tool name used to be `clj-gatling`. The project started as a simple wrapper to Gatling.
However, today the library has evolved into a separate tool with its own design.
Gatling Highcharts is now only an optional dependency that is used for generating graphical results.
So the name was misleading and project was renamed to `trombi` (It is a Finnish word meaning Tornado).

Note for `clj-gatling` users! `trombi` is backwards compatible and you can still run the existing tests without any code change.
However, `trombi` does not include all the dependencies by default anymore. See section [Additional dependencies](#additional-dependencies).

### Older versions

Note! Version 0.8.0 includes a few changes on how simulations are defined.
See more details in [change history](CHANGES.md). All changes are backwards
compatible. The old way of defining the simulation is still supported but
may be deprecated in future. You can see documentation for old versions [here](README-old.md).

## Jenkins

This is compatible with [Jenkins Gatling Plugin](https://plugins.jenkins.io/gatling).

## Why

AFAIK there are no other load testing tools where you can specify your tests in Clojure and which is suitable for testing large stateful applications.
In my opinion Clojure syntax is very good for this purpose.

## Design Philosophy

### Real life scenarios

Idea is to test the system/application by simulating how multiple users access the application concurrently.
After the simulation you get comprehensive results: both as Clojure data and nice graphs if you want.

If you want to just test single web page with huge amounts of requests simpler tools like Apache Benchmark can do that.
Of course, trombi can do that also but it might be a bit of an overkill for that job.

### No DSL

I am not a fan of complex DSLs. trombi tries to avoid DSL approach.
Idea is that you should write just ordinary Clojure code to specify the
actions and the actual scenario definition is a map.

### Distributed load testing

[Clojider](https://github.com/mhjort/clojider) is an experimental tool that can run trombi in a distributed manner.
It uses AWS Lambda technology for running distributed load tests in the cloud.

## Contribute

Use [GitHub issues](https://github.com/mhjort/trombi/issues) and [Pull Requests](https://github.com/mhjort/trombi/pulls).

### Development

Tests and the REPL use deps.edn: run `make test` for tests and `make repl` for a REPL.
Leiningen (`project.clj`) is used for releasing to Clojars, so keep dependencies in sync in both files.

The animation at the top of this page is recorded with [VHS](https://github.com/charmbracelet/vhs).
To update it after output changes, run `vhs doc/demo/demo.tape` in the repository root.

## License

Copyright (C) 2014-2026 Markus Hjort

Distributed under the Eclipse Public License, the same as Clojure.

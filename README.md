# clj-gatling [![Build Status](https://travis-ci.org/mhjort/clj-gatling.png?branch=master)](https://travis-ci.org/mhjort/clj-gatling)

Create and run performance tests using Clojure. For reporting uses Gatling under the hood.

Note! Currently this is more of a proof-of-concept and lacks lot of features.
The integration to Gatling is also far from perfect.

## Installation

Add the following to your `project.clj` `:dependencies`:

```clojure
[clj-gatling "0.0.6"]
```

## Usage

### Non-blocking HTTP

This method uses asynchronous http-kit under the hood. Currently this is the best way to generate load.

```clojure

(use 'clj-gatling.core)

(run-simulation
  [{:name "Test-scenario"
   :requests [{:name "Localhost request" :http "http://localhost"}]}] 200)
```

### Blocking functions

Currently this is suitable only for a small number of parallel users (< 100).

```clojure

(use 'clj-gatling.core)

(defn example-request [user-id]
  (println (str "Simulating request for user #" user-id))
  (Thread/sleep (rand 1000))
  true)

(run-simulation
  [{:name "Test-scenario"
   :requests [{:name "Example-request" :fn example-request}]}] 2)
```

See example project from [here](https://github.com/mhjort/clj-gatling-example)

## License

Copyright (C) 2014 Markus Hjort

Distributed under the Eclipse Public License, the same as Clojure.

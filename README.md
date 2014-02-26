# clj-gatling

Create and run performance tests using Clojure. For reporting uses Gatling under the hood.

Note! Currently the version is at proof-of-concept stage and lacks lot of features.

## Installation

Add the following to your `project.clj` `:dependencies`:

```clojure
[clj-gatling "0.0.2"]
```

## Usage

```clojure

(use 'clj-gatling.core)

(defn example-request [user-id]
  (println (str "Simulating request for user #" user-id))
  (Thread/sleep (rand 1000))
  true)

(run-simulation
  {:name "Test-scenario"
   :requests [{:name "Example-request" :fn example-request}]} 2)
```

See example project from [here](https://github.com/mhjort/clj-gatling-example)

## License

Copyright (C) 2014 Markus Hjort

Distributed under the Eclipse Public License, the same as Clojure.

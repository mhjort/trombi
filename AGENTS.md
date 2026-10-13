# AGENTS.md

Trombi is a Clojure library for load testing stateful applications (formerly `clj-gatling`).
Simulations are plain Clojure data; results come back as Clojure data, with optional
graphical reports via the separate `trombi-gatling-highcharts-reporter` library.

## Commands

- Run tests: `make test` (Kaocha via `./bin/kaocha`, uses the `:dev` alias in `deps.edn`)
- Run a single namespace: `./bin/kaocha --focus trombi.core-test`
- CI tests (JUnit XML output): `make test-ci`
- REPL: `make repl`

The full suite takes a while because some tests run real simulations.

## Build configuration

Dependencies are declared in **both** `deps.edn` (used for tests and REPL) and
`project.clj` (used for releasing to Clojars with Leiningen). Keep them in sync
when adding or bumping dependencies. `project.clj` also AOT-compiles
`trombi.simulation-runners`.

## Layout

- `src/trombi/` – the library
  - `core.clj` – public API (`run`, `run-async`)
  - `schema.clj` – Prismatic Schema definitions for simulations and options
  - `simulation.clj`, `simulation_runners.clj`, `simulation_util.clj` – executing scenarios (duration / fixed request count runners, scenario weighting, rates)
  - `pipeline.clj` – splitting a simulation across executors/nodes
  - `report.clj`, `reporters/` – result collection and built-in reporters (short summary, raw)
  - `progress_tracker.clj`, `stats.clj`, `timers.clj` – runtime tracking
  - `legacy_util.clj`, `converter.clj` – compatibility with the old clj-gatling formats
- `src/clj_gatling/` – backwards-compatible `clj-gatling.*` namespaces; must keep working for existing users
- `test/` – mirrors `src/`; `test_helpers.clj` has shared fixtures
- `examples/` – example simulations (on the dev source path) and a standalone `metrics-simulation` project

## Conventions

- Backwards compatibility matters: don't break the public API in `trombi.core` or the legacy `clj-gatling.core` namespace without a deliberate major change.
- Gatling Highcharts and http-kit are dev/optional dependencies only; the core library must not require them.
- Add user-visible changes to `CHANGES.md` under the upcoming version.
- Update `README.md` when options or public behaviour change.

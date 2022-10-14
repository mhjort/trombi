# Metrics simulation

Example project on how to create load tests using [clj-gatling](https://github.com/mhjort/clj-gatling)

The tests are run against simple test server.

## Usage

  First start the test server with command:

  $ lein start-test-server [NUMBER_OF_THREADS_DEFAULT_IS_100_IF_THIS_PARAM_NOT_GIVEN]

  Then start the simulation:

  $ lein run [SIMULATION] [NUMBER_OF_SIMULTANEOUS_USERS] [NUMBER_OF_REQUESTS]

  # e.g. lein run metrics 50 100

  If you want to just generate load but skip reports you can run with option `--no-report`

  $ lein run metrics 50 100 --no-report

  If you want to ramp up slowly you can run with option `--ramp-up`

  $ lein run metrics 50 100 --ramp-up

  If you want to test raw reporter you can run with option `--raw-report`

  $ lein run metrics 50 100 --raw-report

  If you want to run tests with given rate of new users per second you can run with option `--with-rate`

  $ lein run metrics 50 100 --with-rate

## License

Copyright (C) 2014-2022 Markus Hjort

Distributed under the Eclipse Public License, the same as Clojure.

# Metrics simulation

Example project on how to create load tests using [clj-gatling](https://github.com/mhjort/clj-gatling)

The tests are run against simple demo server running in Heroku.
This server is only for demo purpose and cannot handle heavy load.
Please, use this only for testing with small number of parallel users-

## Usage

  $ lein run [SIMULATION] [NUMBER_OF_SIMULTANEOUS_USERS] [NUMBER_OF_REQUESTS]

  # e.g. lein run metrics 50 100

  If you want to just generate load but skip reports you can run with option `--no-report`

  $ lein run metrics 50 100 --no-report

  If you want to ramp up slowly you can run with option `--ramp-up`

  $ lein run metrics 50 100 --ramp-up

## License

Copyright (C) 2014-2020 Markus Hjort

Distributed under the Eclipse Public License, the same as Clojure.

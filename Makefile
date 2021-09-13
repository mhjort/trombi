repl:
	clj -A:dev

test:
	./bin/kaocha --reporter documentation

test-ci:
	./bin/kaocha --plugin kaocha.plugin/junit-xml --junit-xml-file test-results/kaocha/results.xml

.PHONY: repl test

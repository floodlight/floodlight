# Because I am old and crotchety and my fingers can't stop from running 
#	`make` commands

.PHONY: docs doc all test tests count install clean

all:
	ant

init:
	ant init

docs:
	ant javadoc

doc:
	ant javadoc

javadoc:
	ant javadoc

check: tests
test: tests

tests: all unit-tests

unit-tests:
	ant tests

regression-tests:
	make -C regress tests

count: 
	@find src -name \*.java | xargs wc -l | sort -n

clean:
	ant clean

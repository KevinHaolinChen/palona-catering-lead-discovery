.PHONY: run test package

run:
	mvn spring-boot:run

test:
	mvn test

package:
	mvn package

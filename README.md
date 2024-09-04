# Arazzo Specification Processing MVP

Overview
--
The repository is organized into separate modules for clarity:

* `application`: The main application module utilizing Spring Boot and Swagger in order to provide an OAS.
* `application-integration-test`: Module for integration tests utilizing the `ITarazzo` library.
* `library`: `ITarazzo` library containing parsing, validation, and execution logic.

Getting Started
--
* Clone the repository.
* start the `Application` from `application` module
* start the `ExampleIT` from `application-integration-test` module
* see console outputs of both
  
Acknowledgments
--
This project leverages the following technologies:
* SpringBoot
* JUnit
* OpenAPI Initiative (OAI) Arazzo
* OpenAPI Specification (OAS)
* Swagger
* Everit JSON Schema
* Jackson
* JSONPath
* XPath

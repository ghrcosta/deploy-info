# Deploy Info Portal

## Project overview

System backend, responsible for all communication with Google Cloud Platform (GCP) elements. Also serves the frontend
and implements the API responsible for the communication between client (collectors, frontend) and backend.

The backend is implemented using Kotlin and Spring Boot.
The frontend is implemented using Angular.

The system is to be deployed to GCP App Engine (GAE) Standard.

## Directory structure

Main directories are:
- `src/`: Backend implementation
- `ui/`: Frontend implementation

The backend follows a Clean Architecture folder structure:
- `domain/`:
  - Core definition of objects and their business rules.
  - Can only reference other elements in the `domain` layer or basic Kotlin classes.
- `application/`:
  - Orchestrates domain layer objects to execute application behaviors (use cases).
  - Can only reference elements from the `domain` or `application` layers.
  - Can access `infrastructure` elements via objects created from the interfaces it contains.
- `infrastructure/`:
  - Implements technical details as well as interactions with libraries and external services.
  - Implements the interfaces from `application` layer.
- `ui/`:
  - Defines the API (HTTP endpoints) for input and output.

## Instructions

### Development rules

- Follow Clean Code good practices: the code must be easy to read and to give maintenance.
  - Prefer to create multiple small functions instead of a single big one.
  - The name of each element (constants, variables, functions, files, etc.) must make it clear what its contents and/or
    purpose is.
  - Avoid creating lines of code with more than 120 characters. Split lines to respect this limit.
  - Only use fully qualified names when required to avoid conflicts. In every other case, add import statements.
  - Remove unused import statements.

#### Kotlin-specific rules

- Variables in String literals must always be surrounded by `{}`. Example: use `var=${myVar}` instead of `var=$myVar`.

### Verification / Unit test rules

- A change is only considered complete when:
  - New unit tests have been created or existing ones were updated to reflect the code changes.
  - All unit tests pass.
  - Code and documentation (if it exists) match.
  - No unused elements (constants, variables, functions, etc.) left behind in the code.
- When refactoring code, avoid leaving behind old functions that are only called by unit tests.
- NEVER rebuild / restart the Docker containers yourself.

### Documentation rules

- If a change adds a new feature or affects how an existing feature works, modify all documentation to include or
  update the feature description.
  - `README.md`: Has a summary of existing features, but focus on project setup and usage.
  - `documentation/` directory: contains the full documentation for all features, spread in multiple files. Each file
    focuses on a single feature.
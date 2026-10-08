# Deploy Info Collector -- Java Gradle Plugin

## Project overview

Gradle plugin, responsible for collecting project local state, uploading it to Google Cloud Storage, and communicating
when the portal to update the database.

It is implemented using Kotlin.

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
  - No unused import statements.
- When refactoring code, avoid leaving behind old functions that are only called by unit tests.
- NEVER rebuild / restart the Docker containers yourself.

### Documentation rules

- If a change adds a new feature or affects how an existing feature works, modify all documentation to include or
  update the feature description.
  - `README.md`: Has a summary of existing features, but focus on project setup and usage.
  - `documentation/` directory: contains the full documentation for all features, spread in multiple files. Each file
    focuses on a single feature.
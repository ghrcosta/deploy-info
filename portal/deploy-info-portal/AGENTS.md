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

### General rules

- Avoid creating lines with more than 120 characters. Split lines to respect this limit.

### Development rules

- Follow Clean Code good practices: the code must be easy to read and to give maintenance.
  - Prefer to create multiple small functions instead of a single big one.
  - The name of each element (constants, variables, functions, files, etc.) must make it clear what its contents and/or
    purpose is.
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

- Documentation is layered:
  - `README.md`: a short entry point for the project/module — what it is, how to set it up and how to
    use it. It must not duplicate detailed documentation; it links to it instead.
  - `documentation/`: the detailed documentation, one file per topic.
- Organize the `documentation/` directory by concern (e.g. feature reference, development how-tos,
  deployment/infrastructure), one topic per file, and apply the same structure to every module.
- Single source of truth: when information would otherwise appear in more than one file, keep it in
  exactly one file and link to it from the others. Do not restate contracts, parameters or procedures.
- If a change adds a new feature or affects how an existing feature works, update every document that
  describes it. Documentation must always describe the current, actual behavior — never a past or a
  planned state.
- When adding, moving, renaming or deleting documentation files, update every link to and from them
  (including links from other modules and from code comments), and verify that no relative links are
  broken. Use relative links, so documentation works from any viewer or hosting platform.
- While updating a document, also remove content that has become stale: references to deleted or
  renamed files, superseded designs, and planning artifacts (phases, TODOs, status notes).
- Keep volatile implementation details (source/class/test file names) out of overview and how-to
  documents; name concrete identifiers only where they genuinely help the reader of that specific
  topic, and expect them to be updated whenever the code changes.
- Keep the style consistent across all documentation:
  - Each file starts with a level-1 title and a one-paragraph statement of its purpose.
  - Use tables for structured data (parameters, fields, configuration, error codes) instead of prose.
  - Use the same terminology for the same concept in every file; never introduce a second name for an
    existing feature or element.
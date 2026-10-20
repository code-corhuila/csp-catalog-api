# Changelog

All notable changes to this repository are recorded here, one entry per change, in the pull request that
introduces it.

## [Unreleased]

### Added
- Movie domain of `catalog-core` (HU-CATALOG-001, ADR-037): the `Movie` aggregate and its `MovieDetails`
  with the invariants of the data model (title, duration of at least one minute, classification, at most 20
  genres and 50 cast members, http poster), the publication lifecycle (`DRAFT`, `PUBLISHED`, `ARCHIVED`, with
  `ARCHIVED` final and archiving that never removes the movie) and the typed domain errors.
- Movie ports and use cases of `catalog-core` (HU-CATALOG-001, ADR-037): the input port `MovieUseCases`, the
  output ports `MovieRepository` and `IdGenerator`, and `MovieService`, which replays a write by its
  `Idempotency-Key` before the rules of the current state are evaluated, so repeating an archive does not fail
  with an invalid transition.
- MongoDB persistence of the movies (HU-CATALOG-001, ADR-037): `MongoMovieRepository` writes the movie and its
  `idempotency_key` in one transaction, replays a concurrent write of the same key, lists the movies that are not
  archived newest first and guards the window of the list; `MovieDocumentMapper` writes the documents the validator
  of `csp-catalog-db` expects. The limits of the MongoDB client are declared in `application.yml`.
- HTTP error envelope of the catalog API (HU-CATALOG-001): `ErrorResponse` (`error`, `message`, `traceId`),
  `CorrelationIdFilter` (a UUID `X-Correlation-Id` is kept, anything else is replaced, and the id is in the log
  as `traceId`) and `ApiExceptionHandler`, the only place that turns a domain or infrastructure error into
  400, 404, 409, 422, 503 or 500. The service answers under the base path `/api/v1/catalog`.

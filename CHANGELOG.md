# Changelog

All notable changes to this repository are recorded here, one entry per change, in the pull request that
introduces it.

## [Unreleased]

### Added
- Movie domain of `catalog-core` (HU-CATALOG-001, ADR-037): the `Movie` aggregate and its `MovieDetails`
  with the invariants of the data model (title, duration of at least one minute, classification, at most 20
  genres and 50 cast members, http poster), the publication lifecycle (`DRAFT`, `PUBLISHED`, `ARCHIVED`, with
  `ARCHIVED` final and archiving that never removes the movie) and the typed domain errors.

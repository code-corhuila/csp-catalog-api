# Changelog

All notable changes to this repository are recorded here, one entry per change, in the pull request that
introduces it.

## [Unreleased]

### Added
- `GET /health` (liveness, no dependency) and `GET /health/ready` (readiness: a `ping` on MongoDB inside the limits of the client, `503` with the error
  envelope when it does not answer), both public and validated against `catalog-service.yaml` 1.10.0 (DEC-012, code-corhuila/csp-catalog-api#95).
  Until now both answered `404`.

### Fixed
- The token validation tolerates 30 seconds of clock skew on `exp` (Norma 5.3.7): a token that expired up to 30 s ago is accepted on every route,
  the availability of a showtime included, and one that expired earlier is still a 401. Four tests of `JwtAuthenticationFilterTest` cover the
  edges (code-corhuila/csp-catalog-api#91, part of the consumer guide code-corhuila/csp-docs#266).

### Changed
- `deploy/compose.yml` passes the variables the service reads, so the platform can run it: `MONGODB_URI` built from `CATALOG_APP_PASSWORD`
  (required), `MONGO_HOST` and `CATALOG_DB_NAME`, `JWT_PUBLIC_KEY` or `JWT_PUBLIC_KEY_FILE`, and `SERVICE_SUBJECTS` from
  `CATALOG_SERVICE_SUBJECTS` (default `booking-service`; the platform env files already use `SERVICE_SUBJECTS` for `csp-worker`).
  The README describes them.

### Changed
- README: describes the service as it is (HU-CATALOG-001 to HU-CATALOG-006) instead of the base scaffold: the routes and who may
  call each one, the configuration variables, the variables of the integration and contract tests, and `qa-promote/...`
  as the branch prefix of a promotion (ADR-021).

### Added
- CI that gives the integration and contract tests what they need (`.github/workflows/ci.yml`): MongoDB 7.0.14 with authentication, a key file
  and a replica set of one node, the logins of `csp-infra-mongo`, the structure of `csp-catalog-db` (at the branch the pull request targets)
  applied by its own executor as `catalog_migrator`, and the contract of `csp-docs`. The tests run as `catalog_app`, which holds only
  `catalog_writer` (ADR-049), so a privilege the API needs and lacks fails the build (TD-042). Before, `build-and-test` would fail
  with `CI` set because nothing provided the database or the contract.
- Seat layout in the availability (HU-CATALOG-005, ADR-042 amendment 1, DEC-010): `AvailabilityResponse` adds `seatLayout`,
  the seats of the room with `label`, `row`, `number` and `type` in stored order, so that Booking can serve the seat map;
  `row` and `number` are left out when a seat has none. `ShowtimeAvailability` carries the layout and derives `seats` from
  it, which is unchanged, like the security and the rules of ADR-042. Contract 1.9.0.
- Public movie search route (HU-CATALOG-006, ADR-044): `MovieSearchController` serves `GET /billboard/movies` with the
  `q`, `genre`, `durationMax`, `page` and `limit` of `catalog-service.yaml` and answers the public `BillboardMovie` with
  the `PaginatedMeta`; a malformed filter is a 400. The security filter opens only the exact GET of the route, like
  `GET /billboard`, whatever token it carries, and `GET /movies` keeps `catalog:write`. `UseCaseConfiguration` wires
  `MongoMovieSearchReader` and `MovieSearchService`; `MovieSearchRoutesContractTest` validates the route against the
  contract.
- MongoDB reader of the movie search (HU-CATALOG-006, ADR-044): `MongoMovieSearchReader` implements `MovieSearchReader` with one
  query on `movies`: published and not archived movies, a `$text` query that requires every word of the text as a quoted
  phrase of letters and digits (so a hyphen or a quote is never search syntax), the exact genre, the maximum duration,
  order by title and then id, and the total of the matches. `MongoBillboardReader` shares its public movie mapping.
  `MongoMovieSearchReaderTest` runs it against MongoDB with the schema of `csp-catalog-db`. It has no route yet.
- Movie search use case of `catalog-core` (HU-CATALOG-006, ADR-044): `MovieSearchQuery` validates and normalizes the filters
  (`q` trimmed, at most 100 characters and ignored when blank, `genre` not blank and at most 50 characters,
  `durationMax` from 1, `page` from 1 and `limit` from 1 to 100) and gives the words of the text, the input port
  `MovieSearchUseCases` and `MovieSearchService` answer a `MovieSearchPage` of the public `BillboardMovie` through the
  output port `MovieSearchReader`, whose javadoc states the selection and order of the reader that follows. It has no
  adapter and no route yet.
- Availability route with the service credential (HU-CATALOG-005, ADR-042): `AvailabilityController` serves
  `GET /showtimes/{showtimeId}/availability` with the `AvailabilityResponse` of `catalog-service.yaml`. The security
  filter opens it only to a valid token whose `sub` is in `SERVICE_SUBJECTS` (comma separated, default
  `booking-service`, empty allows nobody); any other valid token is a 403 and a service token opens no other route.
  `UseCaseConfiguration` wires `AvailabilityService`; `AvailabilityRoutesContractTest` validates the route against
  the contract.
- Availability use case of `catalog-core` (HU-CATALOG-005, ADR-042): the input port `AvailabilityUseCases` and
  `AvailabilityService` answer the `ShowtimeAvailability` read model of a scheduled showtime of a published movie in an
  active room, with the snapshots of the showtime and the seat labels of the current layout. Any other case is a
  `ShowtimeNotFoundException`; the use case only reads.
- Public billboard route (HU-CATALOG-004, ADR-041): `BillboardController` serves `GET /billboard` with the `date`,
  `movieId`, `page` and `limit` parameters and the `BillboardItem` DTOs of `catalog-service.yaml`, and a malformed
  parameter is a 400. The security filter opens only `GET /billboard` and ignores any token sent to it; every other
  method and route keeps today's rules. `UseCaseConfiguration` wires `MongoBillboardReader` and `BillboardService`;
  `BillboardRoutesContractTest` validates the route against the contract.
- MongoDB billboard reader (HU-CATALOG-004, ADR-041): `MongoBillboardReader` implements `BillboardReader` with one 
  aggregation on `showtimes` that keeps the scheduled, not yet ended showtimes of a published, not archived movie 
  in a not archived room, filters by UTC start day and movie, orders by start and id, and returns the window with 
  its total.
- Billboard use case of `catalog-core` (HU-CATALOG-004, ADR-041): the input port `BillboardUseCases` with 
  `BillboardQuery`, the output port `BillboardReader` with the read model `BillboardPage`, `BillboardEntry`, 
  `BillboardMovie`, `BillboardRoom` and `BillboardShowtime`, and `BillboardService`, which asks the reader for the 
  upcoming showtimes as of the clock instant in milliseconds.
- Showtime routes (HU-CATALOG-003, ADR-039): `ShowtimeController` serves `POST`, `GET`, `PATCH` and `DELETE` on
  `/showtimes` and `/showtimes/{showtimeId}` with the DTOs of `catalog-service.yaml`, the `Idempotency-Key` bounds and
  the errors of the contract (400, 404, the 409 of an overlap and the 422 of a movie or room that cannot be used).
  The security filter maps `/showtimes` to `showtime:write`, reads included (DEC-006), and keeps the availability of
  a showtime forbidden until HU-CATALOG-005. `UseCaseConfiguration` wires `MongoShowtimeRepository` and
  `ShowtimeService`; `ShowtimeRoutesContractTest` validates every route against the contract.
- MongoDB persistence of the showtimes (HU-CATALOG-003, ADR-039): `MongoShowtimeRepository` writes the showtime, its
  `idempotency_key` and, for a creation, the `ShowtimePublished` event in `outbox_event` in one transaction. The
  transaction first increments `schedule_version` of the room, so concurrent schedulers of one room conflict and the
  loser fails the `ShowtimeOverlapService` check against the showtime of the winner; `ShowtimeDocumentMapper` builds the
  documents that pass the validators of `csp-catalog-db`.
- Showtime ports and use cases of `catalog-core` (HU-CATALOG-003, ADR-039): the input port `ShowtimeUseCases` with its
  inputs, the output port `ShowtimeRepository` with `ShowtimeWrite` and `ShowtimePage`, and `ShowtimeService`, which
  replays by key first, validates the movie and the room, asks for the overlap check only when a scheduled showtime
  moves in time and carries the `ShowtimePublished` event only on creation.
- Showtime domain of `catalog-core` (HU-CATALOG-003, ADR-039): the `Showtime` aggregate with its half-open
  `TimeInterval`, the `SCHEDULED`, `CANCELLED` and `COMPLETED` lifecycle, snapshots of the movie title and the room
  name, scheduling only for a published movie and an active room, partial changes, cancellation, the
  `ShowtimePublished` event, the `ShowtimeOverlapService` rule and the typed domain errors.
- Room routes (HU-CATALOG-002, ADR-038): `RoomController` serves `POST`, `GET`, `PATCH` and `DELETE` on `/rooms`
  and `/rooms/{roomId}` with the DTOs of `catalog-service.yaml`, the `Idempotency-Key` bounds and the errors of the
  contract (400, 404 and the 409 of a name in use). The security filter now maps each route prefix to its permission:
  `/movies` needs `catalog:write` and `/rooms` needs `room:write`. `PageMeta` and `IdempotencyKeys` are shared with the
  movie routes, and `RoomRoutesContractTest` validates every room request and answer against the contract on top of
  the shared `ContractTest` base.
- MongoDB persistence of the rooms (HU-CATALOG-002, ADR-038): `MongoRoomRepository` writes the room and its
  `idempotency_key` in one transaction, answers a name already in use from the unique index `idx_rooms_name` with
  `RoomNameAlreadyInUseException` and writes nothing, lists the rooms that are not archived newest first and guards
  the window of the list; `RoomDocumentMapper` writes the documents the validator of `csp-catalog-db` expects. The
  key lookup, the key insert and the retry of a concurrent write of the same key are now shared with the movies in
  `IdempotentMongoWrites`.
- Room ports and use cases of `catalog-core` (HU-CATALOG-002, ADR-038): the input port `RoomUseCases`, the output 
  ports `RoomRepository` and `RoomNameAlreadyInUseException`, and `RoomService`, which replays a write by its 
  `Idempotency-Key` before the rules of the current state are evaluated, rejects a declared capacity that differs 
  from the seats and treats an archived room as not found. The SHA-256 request hash and the replay step are shared 
  with the movies in `IdempotentWrites`, and `MovieOperation` is now `WriteOperation`.
- Room domain of `catalog-core` (HU-CATALOG-002): the `Room` aggregate with its `RoomDetails` and `Seat` layout, 
  the invariants of the data model (name of at most 100 characters, 1 to 500 seats, unique seat labels, optional 
  row and number, `STANDARD`, `VIP` and `ACCESSIBLE` seat types), partial changes, archiving that never removes the 
  room and the typed domain errors.
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
- JWT authentication and permissions of the catalog API (HU-CATALOG-001): `JwtAuthenticationFilter` validates the
  bearer token by itself (RS256, `exp`, `sub` the id of a user, `permissions`), answers 401 or 403 with the error
  envelope, opens only `/health`, `/health/ready` and `/billboard`, requires `catalog:write` on every route under
  `/movies` and forbids any route without a rule. `JwtKeyConfiguration` reads the public key from `JWT_PUBLIC_KEY`
  or `JWT_PUBLIC_KEY_FILE`.
- Movie routes of the catalog API (HU-CATALOG-001, ADR-037, DEC-004): `POST /movies` (201 with `Location`),
  `GET /movies` (page, limit and the pagination metadata), `GET /movies/{movieId}`, `PATCH /movies/{movieId}` and
  `DELETE /movies/{movieId}` (archives), with the DTOs of the contract and the `Idempotency-Key` required on the
  writes. `UseCaseConfiguration` wires the use cases to the MongoDB repository. The routes are validated, request
  and answer, against `catalog-service.yaml` through the real stack, and the security filter reads the route from
  the servlet path and the path info.

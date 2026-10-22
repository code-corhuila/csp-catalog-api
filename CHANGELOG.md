# Changelog

All notable changes to this repository are recorded here, one entry per change, in the pull request that
introduces it.

## [Unreleased]

## [3.0.0] - 2026-10-21

HU-CATALOG-001, HU-CATALOG-002, HU-CATALOG-003, HU-CATALOG-004 and the base scaffold of the service promoted from `qa` to `main`.

### Added
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

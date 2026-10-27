# csp-catalog-api

> catalog bounded context: service API

Part of the **Cinesync Platform** distributed system — team `cinesync-platform`, Group 1.
Governance and documentation live in [`csp-docs`](https://github.com/code-corhuila/csp-docs).

## Purpose

`csp-catalog-api` is the service of the **catalog** domain: movies, rooms and showtimes. It is the owner of that
data and the publisher of catalog events, which it writes to an outbox in the same operation as the change.
Booking reads the availability of a showtime over REST with a service credential; Auth owns identity; Ticketing
and Concessions consume the events.

The service implements HU-CATALOG-001 to HU-CATALOG-006: the administration of movies, rooms and showtimes, the
availability for Booking, the public billboard and the public movie search. The contract is `catalog-service.yaml`
in `csp-docs`; the service is built in small steps and each step is a Pull Request that leaves the repository
coherent.

## Routes

Every route lives under the context path `/api/v1/catalog`, which the service owns, so the same process works with
and without the gateway. A missing or invalid token is a `401`; a valid token without the permission of the route is
a `403`; a route with no rule is forbidden. Both answer the error envelope of the contract. Tokens are RS256 and the
permission is read from the `permissions` claim (Norma 5.3.7); a token is still accepted up to 30 seconds after its `exp`, the clock skew the norm allows.

| Routes | Who may call | Record |
|---|---|---|
| `POST`, `GET`, `GET /{movieId}`, `PATCH /{movieId}` and `DELETE /{movieId}` of `/movies` | A token with `catalog:write` | DEC-004 |
| The same five routes of `/rooms` | A token with `room:write` | DEC-005 |
| The same five routes of `/showtimes` | A token with `showtime:write` | DEC-006 |
| `GET /showtimes/{showtimeId}/availability` | A token whose `sub` is in `SERVICE_SUBJECTS`; any other valid token, a user with `showtime:write` included, is a `403` | ADR-042, DEC-008 |
| `GET /billboard` | Public: no token is needed and a sent one is ignored | ADR-041, DEC-007 |
| `GET /billboard/movies` | Public, as the billboard | ADR-044, DEC-009 |
| `GET /health` | Public liveness: no dependency, `200` while the process runs | DEC-012 |
| `GET /health/ready` | Public readiness: pings MongoDB, `503` when it does not answer | DEC-012 |

`POST`, `PATCH` and `DELETE` of the three resources take the `Idempotency-Key` header (Norma 5.3.8). `DELETE`
archives a movie or a room and cancels a showtime.

The decisions are in `09-microservices/services/03-catalog-service/decisions.md` of `csp-docs`.

## Configuration

The variables are listed in `.env.example`; copy it to `.env` for local values and never commit `.env`.

| Variable | Meaning |
|---|---|
| `PORT` | Port of the service, `8082` by default |
| `MONGODB_URI` | The replica set of `csp-infra-mongo` and the login user of the catalog (Annex J.5) |
| `JWT_PUBLIC_KEY` or `JWT_PUBLIC_KEY_FILE` | Public key of the identity service that validates every token: the PEM itself or the path of the file that holds it; set one of the two |
| `SERVICE_SUBJECTS` | `sub` values of the service tokens allowed to read the availability, comma separated; absent: `booking-service`; present but empty: nobody |

The pool and the timeouts of the MongoDB client are declared in `catalog-app/src/main/resources/application.yml`
(Norma 5.3.10).

In the platform, `deploy/compose.yml` builds the values of the service from the variables of the platform env files: `MONGODB_URI` from
`CATALOG_APP_PASSWORD` (required: compose refuses to start without it; it travels inside the URI, so use characters that need
no escaping), `MONGO_HOST` and `CATALOG_DB_NAME`; `JWT_PUBLIC_KEY` or `JWT_PUBLIC_KEY_FILE`; and `SERVICE_SUBJECTS` from
`CATALOG_SERVICE_SUBJECTS` (default `booking-service`), because the platform already uses `SERVICE_SUBJECTS` for `csp-worker`.
The container has a healthcheck that calls `GET /health/ready`: it is healthy only while MongoDB answers the ping, unhealthy
when it does not and healthy again when it returns; compose reports it and does not restart the container for it (DEC-012).

## Stack

| Item | Decision | Record |
|---|---|---|
| Language and build | Java 21, Spring Boot 3.x, Maven, modules `catalog-core`, `catalog-adapters` and `catalog-app` | [ADR-012](https://github.com/code-corhuila/csp-docs/blob/main/05-architecture/decisions/records/ADR-012-booking-java-spring-boot-maven.md), [ADR-016](https://github.com/code-corhuila/csp-docs/blob/main/05-architecture/decisions/records/ADR-016-catalog-java-mongodb-liquibase.md) |
| Database | MongoDB, owned by the catalog domain | [ADR-016](https://github.com/code-corhuila/csp-docs/blob/main/05-architecture/decisions/records/ADR-016-catalog-java-mongodb-liquibase.md) |
| Events | The API writes the outbox; `csp-worker` relays it with read-only access | [ADR-019](https://github.com/code-corhuila/csp-docs/blob/main/05-architecture/decisions/records/ADR-019-outbox-relay-read-only-for-other-domains.md) |
| Availability | Read by Booking over REST with a service credential | [ADR-042](https://github.com/code-corhuila/csp-docs/blob/main/05-architecture/decisions/records/ADR-042-catalog-availability-rules-and-the-service-credential.md) |

## Rules of this repository

- **No migration here.** There is no migration or Mongock library in this repository, and nothing runs at startup
  to change the database structure (Norma 5.2.1). Structure and migrations live in `csp-catalog-db`.
- **The domain depends on nothing.** `catalog-core` does not declare Spring or a database driver, so a framework
  annotation in the domain does not compile.
- **Only the approved contract is implemented.** The contract is `catalog-service.yaml` in `csp-docs`; a route
  that is not in it is not added.
- **The API never connects to RabbitMQ.** `csp-worker` relays the outbox.

## Related repositories

| Repository | Relation |
|---|---|
| `csp-catalog-db` | Owns the catalog database structure and its migrations |
| `csp-catalog-portal` | Portal that consumes this API through the gateway |
| `csp-docs` | Governance, contracts, data model and ADRs |
| `csp-worker` | Relays the catalog outbox |
| `csp-infra-mongo` | Defines the MongoDB instance |

## Build, test and run

Requirements: Java 21 and Maven 3.9 (or Docker).

```bash
mvn -B verify                              # compile and run every test
mvn -B -pl catalog-core test               # only the domain tests (no Spring, no database)
mvn -B -pl catalog-app -am package -DskipTests
java -jar catalog-app/target/catalog-app-0.1.0.jar   # starts on PORT, 8082 by default
```

The integration tests need a disposable MongoDB replica set that already has the structure of `csp-catalog-db`, and
the contract tests need the official contract. Without the variables they are skipped; with `CI=true` they fail
instead of being skipped:

| Variable | Meaning |
|---|---|
| `TEST_MONGODB_URI` | URI of the disposable database the integration tests may write to |
| `TEST_CONTRACT_FILE` | Path of `catalog-service.yaml` of `csp-docs` that the route tests validate requests and answers against |

With Docker, from the root of the repository:

```bash
docker build -f deploy/Dockerfile -t csp-catalog-api .
docker run --rm -e PORT=8082 -e MONGODB_URI=<uri> -e JWT_PUBLIC_KEY_FILE=<path> -p 8082:8082 csp-catalog-api
```

The log ends with `Started CatalogApplication` when the service is up. In the platform, `csp-infra-mongo` includes
the executor of `csp-catalog-db` and the platform composition includes `deploy/compose.yml`, which exposes the port on
the `platform` network without publishing it: only the gateway reaches the service.

## Branching

Three permanent branches. **None of them accepts a direct commit** — you enter through a child
branch and leave through a Pull Request.

```
develop  <--PR--  feat/... fix/... chore/...
qa       <--PR--  qa-promote/...
main     <--PR--  release/...  hotfix/...
```

Promotion happens **by re-application** (`git cherry-pick -x`), never by merging one permanent
branch into another: `merge develop -> qa` and `merge qa -> main` do not exist in this model.

`main` requires **1 approval from `ariel5253`**. On `develop` and `qa` the team sets its own review
rule.

Full policy: `00-governance/branching-policy.md` in `csp-docs`.

## Pull Requests and commits

- Commits follow Conventional Commits: `<type>(<scope>): <description>`, in English, lowercase and imperative.
- A Pull Request has at most **400 changed lines** (additions plus deletions) and one logical goal.
- Every Pull Request targets the permanent branch that matches its prefix, according to the diagram above.

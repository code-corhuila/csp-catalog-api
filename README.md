# csp-catalog-api

> catalog bounded context: service API

Part of the **Cinesync Platform** distributed system — team `cinesync-platform`, Group 1.
Governance and documentation live in [`csp-docs`](https://github.com/code-corhuila/csp-docs).

## Purpose

`csp-catalog-api` is the service of the **catalog** domain: movies, rooms and showtimes. It is the owner of that
data and the publisher of catalog events, which it writes to an outbox in the same operation as the change.
Booking reads the catalog snapshot over REST; Auth owns identity; Ticketing and Concessions consume the events.

The repository currently holds the **base scaffold only**. The service has no route yet and is built in small
steps; each step is a Pull Request that leaves the repository coherent.

## Stack

| Item | Decision | Record |
|---|---|---|
| Language and build | Java 21, Spring Boot 3.x, Maven, modules `catalog-core`, `catalog-adapters` and `catalog-app` | [ADR-012](https://github.com/code-corhuila/csp-docs/blob/main/05-architecture/decisions/records/ADR-012-booking-java-spring-boot-maven.md), [ADR-016](https://github.com/code-corhuila/csp-docs/blob/main/05-architecture/decisions/records/ADR-016-catalog-java-mongodb-liquibase.md) |
| Database | MongoDB, owned by the catalog domain | [ADR-016](https://github.com/code-corhuila/csp-docs/blob/main/05-architecture/decisions/records/ADR-016-catalog-java-mongodb-liquibase.md) |
| Events | The API writes the outbox; `csp-worker` relays it with read-only access | [ADR-019](https://github.com/code-corhuila/csp-docs/blob/main/05-architecture/decisions/records/ADR-019-outbox-relay-read-only-for-other-domains.md) |
| Snapshot | Catalog snapshot endpoint read by Booking over REST | [ADR-020](https://github.com/code-corhuila/csp-docs/blob/main/05-architecture/decisions/records/ADR-020-booking-catalog-snapshot-and-service-credentials.md) |

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
| `csp-catalog-portal` | Portal that consumes this API |
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

With Docker, from the root of the repository:

```bash
docker build -f deploy/Dockerfile -t csp-catalog-api .
docker run --rm -e PORT=8082 -p 8082:8082 csp-catalog-api
```

The log ends with `Started CatalogApplication` when the service is up. The service has no route yet. In the platform,
`csp-infra` includes `deploy/compose.yml`, which exposes the port on the `platform` network without publishing it:
only the gateway reaches the service. Copy `.env.example` to `.env` for local values and never commit `.env`.

## Branching

Three permanent branches. **None of them accepts a direct commit** — you enter through a child
branch and leave through a Pull Request.

```
develop  <--PR--  feat/... fix/... chore/...
qa       <--PR--  qa/...
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

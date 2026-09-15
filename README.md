# Feature Flag Service

![CI](https://github.com/ayushimodi18/feature-flag-service/actions/workflows/ci.yml/badge.svg)

A production-style REST API to create feature flags, toggle them globally or per user,
and evaluate whether a feature is enabled for a given user, with caching on the hot read path.

**Architecture and request lifecycle diagrams:** [docs/architecture.md](docs/architecture.md)

## Tech stack
Java 21 · Spring Boot 3.5 · Spring Data JPA · H2 (file-based, persistent) / PostgreSQL ·
Caffeine cache · Bean Validation · JUnit 5 + MockMvc + Mockito · GitHub Actions · Docker

## Evaluation rule
1. If the user has an override for the flag, use it (`reason: USER_OVERRIDE`).
2. Otherwise use the flag's global state (`reason: GLOBAL`). The global state starts at `defaultEnabled`.

## Run locally
Requires Java 21 and Maven 3.9+.
```bash
mvn spring-boot:run          # starts on http://localhost:8080
```
Data is persisted to `./data/flags` (H2 file DB), so it survives restarts.

With Docker:
```bash
docker build -t feature-flag-service .
docker run -p 8080:8080 feature-flag-service
```

Use PostgreSQL by setting environment variables:
```bash
DB_URL=jdbc:postgresql://localhost:5432/flags DB_USER=postgres DB_PASSWORD=secret mvn spring-boot:run
```

## Test
```bash
mvn verify
```
17 tests: 13 MockMvc integration tests (status codes, validation, precedence,
cache invalidation) and 4 Mockito unit tests for the service logic.
CI runs these on every push, then builds the Docker image.

## API

Base path: `/api/v1`

| Method | Path | Body | Success | Errors |
|---|---|---|---|---|
| POST | `/flags` | `{name, description?, defaultEnabled}` | 201 + `Location` | 400 invalid, 409 duplicate |
| GET | `/flags` | – | 200 | – |
| GET | `/flags/{name}` | – | 200 | 404 |
| PUT | `/flags/{name}/global` | `{enabled}` | 200 | 400, 404, 409 concurrent |
| PUT | `/flags/{name}/users/{userId}` | `{enabled}` | 200 (upsert) | 400, 404 |
| DELETE | `/flags/{name}/users/{userId}` | – | 204 | 404 |
| DELETE | `/flags/{name}` | – | 204 | 404 |
| GET | `/flags/{name}/evaluate?userId=` | – | 200 | 400 missing userId, 404 |
| GET | `/users/{userId}/flags` | – | 200 (all flags for a user) | 400 |

Errors use RFC 9457 `application/problem+json`:
```json
{ "status": 400, "title": "Bad Request", "detail": "Validation failed",
  "errors": { "name": "must be lowercase letters, digits, '-' or '_' (max 64 chars)" } }
```

Validation: flag name must match `^[a-z0-9][a-z0-9_-]{0,63}$`, description can be at most 255 chars,
and `userId` must be non-blank with at most 128 chars.

### Example
```bash
curl -X POST localhost:8080/api/v1/flags -H 'Content-Type: application/json' \
  -d '{"name":"dark-mode","description":"Dark UI","defaultEnabled":false}'

curl -X PUT localhost:8080/api/v1/flags/dark-mode/users/u1 \
  -H 'Content-Type: application/json' -d '{"enabled":true}'

curl "localhost:8080/api/v1/flags/dark-mode/evaluate?userId=u1"
# {"flag":"dark-mode","userId":"u1","enabled":true,"reason":"USER_OVERRIDE"}

curl localhost:8080/api/v1/users/u1/flags
# {"userId":"u1","flags":{"dark-mode":true}}
```

Health and cache stats: `GET /actuator/health`, `GET /actuator/caches`

## Design decisions
- **Caching:** evaluation results are cached in Caffeine, keyed `flag:userId` (max 10k entries, 60s TTL).
- **Invalidation:** a user override change evicts one key; a global toggle or delete evicts all keys.
  Evictions run **after the DB commit** (`TransactionAwareCacheManagerProxy`), so a concurrent
  read cannot re-cache stale data.
- **Concurrency:** `@Version` optimistic locking plus a unique `(flag_id, user_id)` constraint.
  Race conditions return 409 instead of 500.
- **Layering:** Controller (HTTP and validation) → Service (rules and cache) → Repository (JPA).
  DTOs are separate from entities.
- **Bulk endpoint:** `/users/{userId}/flags` loads all flags in 2 queries (no N+1), which suits SDK startup.

## Project structure
```
src/main/java/com/example/featureflags
├── api/       controllers + DTOs
├── config/    cache configuration
├── domain/    JPA entities
├── error/     exceptions + global handler
├── repo/      Spring Data repositories
└── service/   business logic + caching
```

## Limitations and next steps
- **Multi-instance caching:** the local cache isn't shared. Use Redis, or pub/sub invalidation.
- **Authentication:** add API keys or OAuth, with separate admin and evaluation scopes.
- **Targeting:** percentage rollouts (hash of userId), user segments, and scheduled toggles.
- **Audit log:** record who changed what and when.
- **Schema migrations:** use Flyway instead of `ddl-auto`.
- **Pagination:** add it to `GET /flags`.
- **Operations:** rate limiting and Prometheus metrics.

# Architecture

## High-level component view

```mermaid
flowchart LR
    C[Client / SDK / Admin UI] -->|HTTP JSON| CT[Controllers<br/>FlagController<br/>UserFlagsController]
    CT -->|Bean Validation fails| EH[GlobalExceptionHandler<br/>RFC 9457 ProblemDetail]
    CT --> S[FlagService<br/>business rules]
    S -->|errors| EH
    S -->|evaluate: read-through| K[(Caffeine cache<br/>key = flag:userId<br/>max 10k, TTL 60s)]
    K -->|miss| R[Spring Data JPA<br/>Repositories]
    S -->|writes in DB transaction| R
    S -. evict AFTER commit .-> K
    R --> DB[(H2 file DB<br/>PostgreSQL in prod)]
```

## Evaluate request lifecycle

```mermaid
sequenceDiagram
    participant C as Client
    participant CT as FlagController
    participant S as FlagService
    participant K as Caffeine Cache
    participant DB as Database

    C->>CT: GET /api/v1/flags/{name}/evaluate?userId=u1
    CT->>CT: validate userId (400 if blank / too long)
    CT->>S: evaluate(name, u1)
    S->>K: get "name:u1"
    alt cache hit
        K-->>S: cached result (no DB access)
    else cache miss
        S->>DB: find flag by name
        alt flag missing
            DB-->>S: empty
            S-->>C: 404 ProblemDetail (not cached)
        end
        S->>DB: find override (flagId, u1)
        S->>S: override present ? override : global
        S->>K: put "name:u1"
    end
    S-->>C: 200 {flag, userId, enabled, reason}
```

## Write path and cache invalidation

```mermaid
sequenceDiagram
    participant A as Admin client
    participant S as FlagService
    participant DB as Database
    participant K as Caffeine Cache

    A->>S: PUT /flags/{name}/global  or  PUT /flags/{name}/users/{userId}
    S->>DB: BEGIN, update row, COMMIT
    Note over S,K: TransactionAwareCacheManagerProxy runs eviction only after commit
    alt global toggle or flag delete
        S->>K: evict ALL entries
    else user override set / removed
        S->>K: evict only "name:userId"
    end
    S-->>A: 200 / 204
```

## Key decisions

| Decision | Why | Trade-off |
|---|---|---|
| Precedence: user override > global | Simple, predictable rule for targeting | No segments / percentage rollout yet |
| Cache evaluation results, key `flag:userId` | Evaluation is the hot read path | Many keys per flag |
| Evict all on global change | Always correct, simple | Brief cache miss spike after global toggles |
| Evict after commit | Prevents concurrent reads re-caching stale data | Needs transaction-aware cache manager |
| 60s TTL | Safety net if an eviction is missed | Up to 60s staleness in worst case |
| Local Caffeine cache | Fast, zero infra | Not shared across instances; use Redis or pub/sub invalidation when scaling out |
| `@Version` optimistic locking | Concurrent updates return 409 instead of silently overwriting | Client must retry |
| Unique (flag_id, user_id) + index | One override per user, O(log n) lookup | None significant |

# Party Service

Multi-tenant identity & authorization microservice for the Routesphere ecosystem.

- **Master registry** (operators + tenants) + authoritative copy of all tenant-scoped party data.
- **Per-tenant DB projection** populated asynchronously by Temporal (Sync Model A).
- **3-instance active-active** deployment co-located with Temporal + HAProxy/Galera.

Full design: `../routesphere-architect/party/plan.md`.

---

## Build

```bash
mvn install -DskipITs
```

Tests use Quarkus Dev Services — a MariaDB container is auto-provisioned. Docker must be running.

## Run (dev mode, stand-alone)

```bash
cd party-api
mvn quarkus:dev
```

Dev Services provides the master DB. Service listens on `http://localhost:8080/api/v1`. Change `quarkus.http.port` to 18081 to match prod.

## Run (prod-like, against your own MariaDB)

```bash
export PARTY_DB_URL=jdbc:mariadb://127.0.0.1:3306/party_master
export PARTY_DB_USER=party
export PARTY_DB_PASS=changeit
export PARTY_JWT_SECRET=$(openssl rand -base64 32)

java -jar party-api/target/quarkus-app/quarkus-run.jar
```

Requires `party_master` DB to exist (empty is fine; Flyway will migrate). For multi-instance safe migration, Flyway's built-in `flyway_schema_history` advisory lock handles the race.

## Enable Temporal (prod)

```bash
mvn install -DskipITs -Dparty.temporal.enabled=true

export PARTY_TEMPORAL_TARGET=dns:///temporal-a:7233,temporal-b:7233,temporal-c:7233
export PARTY_TEMPORAL_NAMESPACE=default
```

With the flag set at build time, `PartyWorkerBootstrap` registers workers on
`party-sync-queue`, `party-bulk-queue`, and `party-critical-queue`.
`TemporalSyncDispatcher` replaces `NoopSyncDispatcher` automatically.

## Modules

| Module | Role |
|---|---|
| `party-domain` | Enums, DTOs, value objects (plain Java). |
| `party-master` | Master DB entities, repositories, services (BCrypt, JWT, sync enqueue). |
| `party-tenant-projection` | Per-tenant DataSource registry + projection writer + Flyway-based provisioner. |
| `party-workflows` | Temporal workflows + activity impls; `TemporalSyncDispatcher`. |
| `party-api` | REST resources, Quarkus runtime, Flyway master migrations. |
| `party-integration-test` | End-to-end tests (reserved; unit tests live in `party-api/src/test`). |
| `party-ops` | Docker, scripts (not a Maven module). |

## REST surface (v1)

```
POST  /api/v1/auth/login                          -- operator-user login
POST  /api/v1/auth/refresh                        -- refresh token

GET/POST   /api/v1/operators
GET/PATCH/DELETE  /api/v1/operators/{id}
GET/POST   /api/v1/operators/{opId}/tenants
GET/PATCH/DELETE  /api/v1/tenants/{id}

GET/POST/PATCH/DELETE  /api/v1/operator-users
POST  /api/v1/operator-users/{id}/password
POST  /api/v1/operator-users/{id}/status

GET/POST/PATCH/DELETE  /api/v1/tenants/{tId}/partners
GET/PUT    /api/v1/tenants/{tId}/partners/{pId}/extra

GET/PATCH/DELETE  /api/v1/tenants/{tId}/users
POST       /api/v1/tenants/{tId}/partners/{pId}/users
POST       /api/v1/tenants/{tId}/users/{id}/password
POST       /api/v1/tenants/{tId}/users/{id}/roles
GET/POST/DELETE  /api/v1/tenants/{tId}/users/{uId}/ip-rules
GET/PUT          /api/v1/tenants/{tId}/users/{uId}/menu-permissions

GET/POST/PATCH/DELETE  /api/v1/tenants/{tId}/roles
POST       /api/v1/tenants/{tId}/roles/{id}/permissions
GET/POST/DELETE  /api/v1/tenants/{tId}/permissions

GET        /api/v1/tenants/{tId}/sync-jobs[?status=]
GET        /api/v1/tenants/{tId}/sync-jobs/{id}

GET        /api/v1/ping              -- liveness echo
GET        /q/health                 -- SmallRye health
GET        /q/metrics                -- Prometheus
```

## Status

All phases 0–8 in the architect plan are implemented. Phase 9 (cross-service
integration: switching RTC-Manager + routesphere-core to read from Party)
is pending and lives in those repos.

**Known limitations, planned next:**

- `TenantProjectionWriter` implements PARTNER + AUTH_USER + AUTH_ROLE +
  AUTH_PERMISSION. The remaining entity types log-and-skip; fill in the
  SQL when the projection shape is needed.
- No integration test yet for the full provisioning workflow (needs a
  live Temporal + admin MariaDB credentials). The unit tests exercise
  the sync-job enqueue path only.
- `party-integration-test` module is reserved; real e2e tests land there
  once Temporal testcontainer or a docker-compose stack is wired.
- No Kubernetes manifests. `party-ops/docker/Dockerfile.jvm` provides
  the image; a 3-instance compose stack + Galera/Temporal are an ops task.
- `POST /auth/refresh` is declared in `AuthResource` comments but not yet
  implemented; add when client requires refresh flow.

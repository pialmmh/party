# Party Service

Multi-tenant identity & authorization microservice for the Routesphere ecosystem.

- **Master registry** (operators + tenants) + authoritative copy of all tenant-scoped party data
- **Per-tenant DB projection** synced via Temporal
- **3-instance active-active** deployment co-located with Temporal + HAProxy/Galera

**Design docs:** `../routesphere-architect/party/plan.md`

## Build

```bash
mvn package
```

## Run (dev mode)

```bash
cd party-api
mvn quarkus:dev
# health: http://localhost:18081/api/v1/ping
```

## Modules

| Module | Role |
|---|---|
| `party-domain` | Enums, DTOs, value objects (plain Java) |
| `party-master` | Master DB entities, repositories, transactional services |
| `party-tenant-projection` | Per-tenant DB entities and projection writers |
| `party-workflows` | Temporal workflows + activities |
| `party-api` | REST endpoints, JWT, Quarkus runtime |
| `party-integration-test` | End-to-end tests via testcontainers |
| `party-ops` | Docker, k8s, scripts (not a Maven module) |

## Status

**Phase 0 — scaffold.** Only `/api/v1/ping` is live. Schema, CRUD, workflows arrive in later phases per the plan.

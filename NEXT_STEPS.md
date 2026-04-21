# Next Steps — What's Left After This Implementation

**Date:** 2026-04-22.
**Status:** Phases 0–8 of `../routesphere-architect/party/plan.md` shipped. This file tracks the gaps.

---

## 1. Projection coverage (completes Phase 5 code)

`TenantProjectionWriter.apply(...)` handles: `PARTNER`, `AUTH_USER`, `AUTH_ROLE`, `AUTH_PERMISSION`.
Still need write paths for:

- `PARTNER_EXTRA` — upsert to `partner_extra`
- `AUTH_USER_ROLE` — replace-set against `auth_user_role` (delete + batch insert by user_id)
- `AUTH_ROLE_PERMISSION` — replace-set by role_id
- `IP_ACCESS_RULE` — single-row upsert/delete (`ip_access_rule`)
- `UI_MENU_PERMISSION` — upsert by (user_id, menu_key)

All follow the pattern already in `applyPartner()` / `applyAuthUser()`.

## 2. Full Temporal end-to-end test

Requires a live Temporal server. Two options:

- **Testcontainers:** `io.temporal:temporal-testcontainers` spins a dev server per test.
- **Docker compose:** add `party-ops/compose/temporal.yml`, point tests at it via property.

Once wired, add `party-integration-test/src/test/java/.../TemporalFlowIT.java` that:
1. Creates an operator + tenant via REST.
2. Watches `ProvisionTenantWorkflow` run.
3. Verifies tenant DB is created, `owndb` row inserted, `auth_role` seeds applied, and tenant status flips to ACTIVE.

## 3. Multi-instance HA compose

Per `~/Downloads/party-service-multi-instance-note.md`:

```
party-ops/compose/party-ha.yml
  ├── 3× galera nodes
  ├── 3× haproxy (one co-located per galera pair)
  ├── 3× temporal-server
  └── 3× party-service (active-active)
```

Exit gate: chaos-kill any one node, verify:
- REST traffic continues via remaining 2 instances
- In-flight workflow tasks get reassigned
- No duplicate master writes
- Sync lag recovers within 30 s

## 4. /auth/refresh endpoint

Add to `AuthResource`:

```java
public record RefreshRequest(String refreshToken) {}

@POST @Path("/refresh")
public Map<String, Object> refresh(RefreshRequest r) {
    Claims claims = tokens.parse(r.refreshToken());   // throws on invalid/expired
    if (!"refresh".equals(claims.get("type"))) throw new NotAuthorizedException();
    Long userId = Long.valueOf(claims.getSubject());
    OperatorUser u = operatorUsers.findById(userId);
    ...issue new access token...
}
```

Plus a `refresh_token_blacklist` table if we want logout/revocation.

## 5. @RolesAllowed enforcement

Resources are currently open. Add:
- JWT filter that extracts claims into `AuthContext`
- `@RolesAllowed("SYS_ADMIN")` on operator-mutating endpoints
- `TenantGuard` interceptor that enforces operatorId / tenantId scoping per request

Quarkus's `quarkus-smallrye-jwt` can be adopted, OR hand-rolled with a `ContainerRequestFilter`.

## 6. Integration with existing services (Phase 9)

**Other repos, not this one.** When Party is deployed:

- RTC-Manager `Security/` module: deprecate `Partner`, `AuthUser`, `AuthRole`, `AuthPermission` entities. Add a thin Party REST client. Run dual-read for one release cycle.
- RTC-Manager `FreeSwitchREST/`, `smsrest/`: same deprecation for their Partner entity copies.
- routesphere-core `apps/`: same.
- Write a one-time migration script: `tools/migrate-to-party.sh` that walks every tenant's current `partner` and `auth_*` tables and re-inserts through Party's REST API.

## 7. Observability

- Dashboards: `party_request_count{endpoint,status}`, `party_sync_job_duration_ms{entity,status}`, `party_temporal_workflow_active`, `party_db_pool_usage{tenant}` — partially exposed via Micrometer; wire in Grafana.
- Trace context propagation into Temporal workflows.
- Structured JSON logs with `traceId`, `tenantId`, `operatorId`, `userId`.

## 8. Password reset flow

- `POST /api/v1/auth/reset-initial-password` — consumes one-time token from startup logs, sets real password for the seeded `sysadmin@telcobright.local`.
- `POST /api/v1/operator-users/{id}/password-reset-request` — generates a reset token emailed (out of scope; hook into Notifier when available).

## 9. K8s manifests

`party-ops/k8s/`:
- `deployment.yaml` (3 replicas, affinity to spread across nodes)
- `service.yaml` (internal ClusterIP + external LoadBalancer/Ingress)
- `configmap.yaml` + `secret.yaml` (env vars)
- `hpa.yaml` (optional; note HA target is 3 fixed, not autoscaling)

## 10. Static role/permission templates

If the hardcoded defaults in `TenantProvisioner.seedDefaultRolesAndPermissions()` become unwieldy:

- Add `role_template` and `permission_template` tables in master.
- Provisioner reads templates instead of hardcoded SQL.
- CRUD REST: `/api/v1/templates/roles`, `/api/v1/templates/permissions`.

---

## How to pick up

1. Run `/ks` from this dir (once `party` is added to the architect `knowledge_summary.md` hierarchy — currently it lives only under `routesphere-architect/party/`).
2. Read `plan.md` in the architect repo.
3. Pick an item from this file, run `mvn install -DskipITs` to confirm green before starting, implement, run tests, commit.

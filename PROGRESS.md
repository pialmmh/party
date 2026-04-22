# Party Service — Progress Snapshot

**Date:** 2026-04-22
**Build:** `mvn install -DskipITs` → BUILD SUCCESS, 8 tests green.

---

## Backend — effectively done for v1 scope

- ✅ All 7 Maven modules build green (`party-domain`, `party-master`, `party-tenant-projection`, `party-workflows`, `party-api`, `party-keycloak-spi`, `party-integration-test`)
- ✅ 8 tests passing (full CRUD end-to-end: operator → tenant → partner → user → sync jobs → login)
- ✅ Master + per-tenant schemas (Flyway V1+V2 for master, V1 for tenant)
- ✅ 13 JPA entities + 11 services + full REST CRUD
- ✅ BCrypt + HS256 JWT (admin-UI only — services use Keycloak tokens)
- ✅ Temporal workflows (`SyncEntityWorkflow`, `ProvisionTenantWorkflow`) + activity impls + worker bootstrap (gated by `party.temporal.enabled=true`)
- ✅ **All 9 projection writers** — no more silent drops (PARTNER, PARTNER_EXTRA, AUTH_USER, AUTH_ROLE, AUTH_PERMISSION, AUTH_USER_ROLE, AUTH_ROLE_PERMISSION, IP_ACCESS_RULE, UI_MENU_PERMISSION)
- ✅ Keycloak integration:
  - `/internal/kc/*` endpoints with shared-secret auth (`by-username`, `by-id`, `search`, `validate-credentials`, `healthz`)
  - `party-keycloak-spi.jar` — deployable User Storage provider for Keycloak 24.0.5
  - `KeycloakRealmProvisioner` — creates realms + attaches SPI via admin REST (gated by `party.keycloak.admin.enabled=true`)

---

## Docs — comprehensive

- `AGENT_ONBOARDING.md` — onboarding for next Claude session
- `README.md` — user-facing quickstart + REST surface
- `NEXT_STEPS.md` — full backlog
- `docs/ui-requirements.md` — React UI build spec (~700 lines, for the UI-building agent)
- `docs/keycloak-integration.md` — pivot design; realm-per-tenant; credential flow
- `docs/freeswitchrest-party-flow.html` — visual data-flow diagram (open in browser)
- `../routesphere-architect/party/plan.md` — original executable spec

---

## What's left — ranked by effort

### Small, high-value (1–2 hr each)

1. **Hook `KeycloakRealmProvisioner` into `ProvisionTenantWorkflow`**
   The provisioner class is written but the workflow doesn't call it yet. Add a `KeycloakRealmActivity` that wraps it, register with the Temporal worker, add one `workflow.run` line inside `ProvisionTenantWorkflowImpl`.
2. **`/auth/refresh` endpoint**
   Only needed if the Party admin UI wants session continuity. `AuthResource.refresh(RefreshRequest)` → verify refresh-type claim → issue new access token.
3. **`@RolesAllowed` + `TenantGuard` interceptor**
   Resources are currently open post-login. Enforce scope (SYS_ADMIN vs OPERATOR_ADMIN vs tenant-bound) via a JAX-RS filter that reads `AuthContext`.

### Medium (half-day to day each)

4. **End-to-end Keycloak SPI test**
   Deploy `party-keycloak-spi.jar` to the local Keycloak (port 7104), create `tenant-btcl-btcl` realm, configure the SPI component, hit `/realms/.../token` with a Party-stored user, verify JWT claims (`operatorId`, `tenantId`, `partnerId`, `roles[]`) land correctly.
5. **Full Temporal e2e test** via testcontainer
   Spin Temporal + Keycloak + MariaDB containers; verify `SyncEntityWorkflow` actually writes rows to the tenant DB (not just marks job SUCCESS).
6. **Password-reset flow for seeded sysadmin**
   The V2 seed hash doesn't match any known password. Add a one-time reset-on-first-login token, or rotate the seed at deploy time.

### Bigger (days)

7. **React admin UI** at `/home/mustafa/telcobright-projects/party-ui/`
   Spec is complete in `docs/ui-requirements.md`. Not a single line of code written yet. 8 CRUD screens + Keycloak login + sync-job monitoring.
8. **3-instance HA compose stack**
   3× Party + 3× Temporal + 3× Galera + HAProxy. Chaos-kill validation per `party-service-multi-instance-note.md`.
9. **k8s manifests** (`party-ops/k8s/`)
   Deployment (3 replicas, spread affinity), Service, ConfigMap, Secret, Ingress. HPA not needed (HA target is 3 fixed).

### Cross-repo, weeks (NOT in this repo)

10. **Phase 9 integration**
    - RTC-Manager (`Security/`, `FreeSwitchREST/`, `smsrest/`) deprecates its duplicate `Partner` / `AuthUser` / `AuthRole` / `AuthPermission` entities.
    - Downstream services switch to reading their **local tenant DB** (populated by Party), and add Keycloak token verification for authentication.
    - One-time data migration script to seed Party from existing per-tenant DBs.
    - Dual-read window for one release cycle before cutting over fully.

---

## Honest take

The **write path is complete**. A fresh agent can start from `/party`, run `mvn install`, bring up a MariaDB, and exercise create/update/delete for every entity end-to-end.

The **Keycloak pivot is designed and mostly coded** — SPI jar builds, endpoints exist, realm provisioner is written — but has **not yet been wired to the running Keycloak end-to-end** (no integration test, SPI jar not yet deployed to `$KC_HOME/providers/`).

The **UI and cross-repo integration have not been touched** — those are the two biggest remaining chunks.

**Natural next step if continuing backend:** item #1 (hook realm provisioner into workflow).
**Biggest remaining mountains:** items #7 (UI) and #10 (cross-repo integration).

---

## Quick verification (for any agent picking up)

```bash
cd /home/mustafa/telcobright-projects/party
mvn install -DskipITs                  # → BUILD SUCCESS, 8 tests
ls party-keycloak-spi/target/*.jar     # → party-keycloak-spi.jar exists
git log --oneline | head -8            # see commit history
cat AGENT_ONBOARDING.md                # full context, section 4 is the file map
```

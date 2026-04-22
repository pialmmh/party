# Party Service — Agent Onboarding

**You are starting Claude inside `/home/mustafa/telcobright-projects/party/`.** Read this file first, then skim the files in §4 before touching code.

---

## 1. One-line pitch

A Quarkus (JDK 21) multi-tenant identity & authorization microservice. It owns the canonical graph of **operators → tenants → partners → users → roles → permissions**, projects each change to per-tenant MariaDB databases via Temporal, and exposes a Keycloak User Storage SPI so Keycloak can delegate login to it.

---

## 2. Status at a glance

| Area | State |
|---|---|
| Build | `mvn install -DskipITs` → BUILD SUCCESS, 8 tests |
| Master schema (Flyway V1+V2) | ✅ 13 tables; sys-admin seed row |
| Tenant schema (Flyway V1) | ✅ 10 tables; auto-applied on tenant create |
| REST CRUD | ✅ full hierarchy `/api/v1/operators → tenants → partners → users → roles → permissions → ip-rules → menu-perms → sync-jobs` |
| Temporal workflows | ✅ `SyncEntityWorkflow` + `ProvisionTenantWorkflow` (+ activities) — worker gated by `party.temporal.enabled=true` |
| Projection writers | ✅ all 9 entity types (PARTNER, PARTNER_EXTRA, AUTH_USER, AUTH_ROLE, AUTH_PERMISSION, AUTH_USER_ROLE, AUTH_ROLE_PERMISSION, IP_ACCESS_RULE, UI_MENU_PERMISSION) |
| Keycloak User Storage SPI | ✅ `party-keycloak-spi/target/party-keycloak-spi.jar` — deploy to `$KC_HOME/providers/` |
| Keycloak internal API `/internal/kc/*` | ✅ shared-secret auth, 4 endpoints |
| Keycloak realm provisioner | ✅ hooks into admin REST (gated by `party.keycloak.admin.enabled=true`) |
| HA — 3-instance active-active | ⚠ designed, not yet composed; Flyway lock OK |
| `/auth/refresh` | ❌ not implemented (write if admin UI needs it) |
| `@RolesAllowed` enforcement | ❌ resources are currently open post-login |
| End-to-end Temporal test | ❌ needs Keycloak + Temporal testcontainers |
| React admin UI | ❌ spec only, in `docs/ui-requirements.md` |

See `NEXT_STEPS.md` for the full backlog.

---

## 3. Core design in 9 points

1. **Sync Model A**: Party master is source of truth; per-tenant DBs are async projections. Writes land in master + enqueue `tenant_sync_job` atomically; Temporal carries the change to the tenant DB.
2. **DB-per-tenant** named `{opShort}_{opId}_{tnShort}_{tnId}` (e.g., `btcl_1_btcl_1`). Master = `party_master` on Galera.
3. **Services never call Party REST.** They read their local tenant DB. Only Keycloak calls Party (via the SPI against `/internal/kc/*`).
4. **Keycloak is the token issuer.** Party does not issue tokens to downstream services. Keycloak delegates user lookup + credential validation to Party via the `party-user-storage` SPI.
5. **Two realm kinds**: `party-operators` (for master-level admins) and `tenant-{opShort}-{tnShort}` (one realm per tenant). Tokens carry `operatorId`, `tenantId`, `partnerId`, `scope`, `roles[]`, `permissions[]` as claims.
6. **3 active-active instances** co-located with Temporal + HAProxy + Galera. Stateless by design. Any instance serves any API request; any worker handles any Temporal task.
7. **Plain per-tenant DataSource map** (not Hibernate multi-tenancy) — `TenantDataSourceRegistry` lazily builds Agroal pools.
8. **BCrypt password storage** (cost 12). Passwords live in master only (`operator_user.password_hash`, `auth_user.password_hash`).
9. **Idempotent projection SQL**: `INSERT ... ON DUPLICATE KEY UPDATE` for CREATE/UPDATE; `DELETE WHERE id=?` for DELETE. For many-to-many tables (user_role, role_permission), projection uses replace-set semantics by the parent id.

---

## 4. Where to look — files ranked by importance

Read top-down; stop once you have enough context for your task.

### 4.1 Start here
- `plan.md` equivalent: `../routesphere-architect/party/plan.md` — the executable spec; sections 2–6 cover schemas, workflows, REST.
- `docs/keycloak-integration.md` — how Keycloak fits in; describes `/internal/kc/*` and realm model. **Read if touching auth.**
- `docs/freeswitchrest-party-flow.html` — end-to-end data flow (open in browser).
- `docs/ui-requirements.md` — React admin UI spec (for the UI-building agent).
- `NEXT_STEPS.md` — what isn't done yet.

### 4.2 Schema — the shape of everything
- `party-api/src/main/resources/db/migration/master/V1__baseline.sql` — master DDL (operators, tenants, users, roles, permissions, sync jobs, all tenant-scoped data with `tenant_id` column).
- `party-api/src/main/resources/db/migration/tenant/V1__tenant_baseline.sql` — per-tenant DDL (same tables minus `tenant_id`, plus `owndb`).

### 4.3 Domain + entities
- `party-domain/src/main/java/com/telcobright/party/domain/` — 6 enums (`OperatorType`, `UserStatus`, `OperatorRole`, `SyncStatus`, `SyncOperation`, `EntityType`).
- `party-master/src/main/java/com/telcobright/party/master/entity/` — 13 Panache entities; mirrors the master schema.

### 4.4 Business logic (services)
- `party-master/.../service/TenantSyncJobService.java` — the enqueue primitive every mutating service calls.
- `party-master/.../service/SyncDispatcher.java` + `NoopSyncDispatcher.java` — interface + test-friendly default; real impl is `TemporalSyncDispatcher`.
- `party-master/.../service/PartnerService.java` — representative CRUD service; follow the same pattern for others.
- `party-master/.../service/AuthenticationService.java` — **legacy** Party-issued JWT. Used only for Party's own admin UI; downstream services use Keycloak tokens.

### 4.5 Security primitives
- `party-master/.../security/PasswordHasher.java` — BCrypt, cost 12.
- `party-master/.../security/TokenService.java` — HS256 JWT for admin UI (not for downstream).
- `party-api/.../security/KcIntegrationSecretFilter.java` — guards `/internal/kc/*` with `X-KC-Integration-Secret`.

### 4.6 Keycloak integration (critical — pivot point of the whole architecture)
- `party-master/.../kc/RealmDecoder.java` — turns `party-operators` / `tenant-btcl-btcl` into (operatorId, tenantId).
- `party-master/.../kc/KcUserLookupService.java` — unified lookup across `operator_user` and `auth_user`; flattens roles + permissions into Keycloak-style attributes.
- `party-api/.../resource/InternalKcResource.java` — 4 endpoints consumed by the SPI: `by-username`, `by-id`, `search`, `validate-credentials`.
- **`party-keycloak-spi/`** — the whole module is a jar deployed to Keycloak:
  - `PartyUserStorageProviderFactory.java` — factory + config properties (base URL, secret).
  - `PartyUserStorageProvider.java` — implements `UserLookupProvider` + `CredentialInputValidator` + `UserQueryProvider`.
  - `PartyUserAdapter.java` — bridges Party JSON to Keycloak `UserModel`.
  - `PartyClient.java` — HTTP client with shared-secret header.
  - `META-INF/services/org.keycloak.storage.UserStorageProviderFactory` — SPI registration.

### 4.7 Temporal / projection
- `party-workflows/.../SyncEntityWorkflowImpl.java` — per-entity sync.
- `party-workflows/.../ProvisionTenantWorkflowImpl.java` — tenant DB creation.
- `party-workflows/.../activity/TenantWriteActivityImpl.java` → calls `TenantProjectionWriter`.
- `party-workflows/.../activity/TenantProvisionActivityImpl.java` → calls `TenantProvisioner`.
- `party-workflows/.../PartyWorkerBootstrap.java` — worker lifecycle, gated by `party.temporal.enabled`.
- `party-workflows/.../TemporalSyncDispatcher.java` — replaces `NoopSyncDispatcher` when temporal is enabled.
- `party-tenant-projection/.../TenantDataSourceRegistry.java` — lazy Agroal pool per tenant.
- `party-tenant-projection/.../TenantProjectionWriter.java` — **the SQL bridge.** All 9 entity types; this is what services read from.
- `party-tenant-projection/.../TenantProvisioner.java` — creates the tenant DB + runs Flyway tenant migrations + seeds default roles/permissions.
- `party-tenant-projection/.../KeycloakRealmProvisioner.java` — creates tenant realm + attaches storage provider (gated by admin-enabled flag).

### 4.8 REST layer
- `party-api/.../resource/AuthResource.java` — login (admin-UI only).
- `party-api/.../resource/OperatorResource.java` · `TenantResource.java` · `OperatorTenantResource.java` · `OperatorUserResource.java` — master-level CRUD.
- `party-api/.../resource/PartnerResource.java` · `AuthUserResource.java` · `AuthRoleResource.java` · `AuthPermissionResource.java` · `SyncJobResource.java` — tenant-scoped CRUD.
- `party-api/src/main/resources/application.properties` — every knob. Read this before adding new config.

### 4.9 Tests
- `party-api/src/test/java/com/telcobright/party/api/CrudFlowTest.java` — reference pattern: operator → tenant → partner → user → sync jobs → login. When adding a new feature, add a test in this style.
- `party-api/src/test/java/com/telcobright/party/api/resource/PingResourceTest.java` — smoke.

---

## 5. Running

```bash
# build + unit tests (uses Quarkus Dev Services MariaDB — Docker must be up)
mvn install -DskipITs

# run locally against Dev Services
cd party-api && mvn quarkus:dev
# → http://localhost:8080/api/v1   (dev mode port; prod config uses 18081)

# build with Temporal worker enabled
mvn install -DskipITs -Dparty.temporal.enabled=true

# deploy the Keycloak SPI jar
cp party-keycloak-spi/target/party-keycloak-spi.jar /opt/keycloak-24.0.5/providers/
/opt/keycloak-24.0.5/bin/kc.sh build
# restart Keycloak
```

Keycloak running locally at **http://localhost:7104** (v24.0.5, dev mode).

---

## 6. Environment variables (all with sensible dev defaults)

| Var | Purpose |
|---|---|
| `PARTY_DB_URL` / `_USER` / `_PASS` | Master Galera connection (prod only; dev uses Dev Services) |
| `PARTY_JWT_SECRET` | Legacy Party-admin JWT signing key |
| `PARTY_KC_INTEGRATION_SECRET` | Shared secret between Party and Keycloak SPI |
| `PARTY_KC_ADMIN_ENABLED` | `true` to enable realm provisioning via Keycloak admin REST |
| `PARTY_KC_ADMIN_URL` / `_REALM` / `_CLIENT_ID` / `_USER` / `_PASS` | Keycloak admin creds (used only when admin-enabled) |
| `PARTY_TEMPORAL_ENABLED` | `true` to wire worker + TemporalSyncDispatcher |
| `PARTY_TEMPORAL_TARGET` | DNS round-robin like `dns:///temporal-a:7233,...` |

---

## 7. Gotchas / sharp edges

- **Dev Services overrides your datasource.** In test profile, MariaDB is auto-provisioned. If you accidentally set `quarkus.datasource.jdbc.url` globally, Dev Services won't kick in and tests will fail with auth errors. Keep datasource config under `%prod.` only.
- **`@JsonIgnore` blocks deserialization too.** Use `@JsonProperty(access = WRITE_ONLY)` for fields that must be read in but hidden on the way out (e.g., `Tenant.dbPassRef`).
- **Tenant DB name has a post-persist compute step.** On tenant create, the DB name template is computed *before* `persist()` gives us the tenant id, then corrected to include the id afterward. Don't move this around casually.
- **Sync job row is the contract with the UI.** Every mutating service call MUST enqueue a row. If you add a new entity, wire it through `TenantSyncJobService.enqueue(...)` and add a projection branch in `TenantProjectionWriter.apply(...)`.
- **Panache active record uses public fields.** Don't add Lombok `@Data` — it conflicts with Panache's bytecode rewriting.
- **Keycloak 24 split `AbstractUserAdapter` across several artifacts.** If you touch the SPI module, remember it needs `keycloak-core`, `keycloak-server-spi`, `keycloak-server-spi-private`, `keycloak-model-storage`, and `keycloak-model-legacy` — all `provided` scope.
- **Temporal is disabled by default.** Tests assume NoopSyncDispatcher (sync jobs stay PENDING). To verify real projection in a local run, set `-Dparty.temporal.enabled=true` and have a Temporal server reachable.

---

## 8. Conventions the repo follows

- JDK 21, Panache active-record style (public fields), no Lombok.
- JWT via JJWT 0.12; never smallrye-jwt (we picked JJWT for simpler HS256).
- Flyway migrations split into `master/` and `tenant/` folders; master migrations auto-run at startup, tenant migrations run from `TenantProvisioner`.
- Every mutating service: **write master → enqueue sync_job → dispatch Temporal**. No shortcuts.
- Idempotent projection SQL only.
- `/internal/kc/*` never exposed to public LB — private VPC or WireGuard only.

---

## 9. If you're about to…

- **…add a new entity type** → master Flyway migration + tenant Flyway migration + entity class + service (with sync enqueue) + resource + projection case in `TenantProjectionWriter` + new value in `EntityType` enum + test in `CrudFlowTest`-style.
- **…wire up Keycloak end-to-end** → read `docs/keycloak-integration.md`, deploy SPI jar, set env vars, run Keycloak admin-create flow through `KeycloakRealmProvisioner`.
- **…build the React admin UI** → target dir is `/home/mustafa/telcobright-projects/party-ui/` (not inside this repo). Spec is `docs/ui-requirements.md`.
- **…deploy to the 3-instance HA stack** → Flyway advisory lock already in place; add `party-ops/compose/` with 3× Party + 3× Temporal + 3× Galera.

---

## 10. Last thing before you code

Run `mvn install -DskipITs` first. If the baseline doesn't build, stop and investigate — don't pile changes on top of a broken tree.

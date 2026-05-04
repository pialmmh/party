# Party Service — Project Instructions

Working directory: `/home/mustafa/telcobright-projects/party/`

## Project goal

A Quarkus (JDK 21) multi-tenant identity & authorization microservice. Canonical owner
of the graph `operator → tenant → partner → user → role → permission → ip-rules →
menu-perms`. Master schema in Galera (`party_master`); per-tenant projections in
`{opShort}_{opId}_{tnShort}_{tnId}` DBs, driven by Temporal workflows. Integrates
with Keycloak as the token issuer via a User Storage SPI.

For the full design, read `AGENT_ONBOARDING.md` (in this directory). Keep it in
mind before editing code.

## Scope of this repo

**Both backend and UI are built here.** Do not scatter Party code across other
repos:
- Backend modules: `party-domain/`, `party-master/`, `party-tenant-projection/`,
  `party-workflows/`, `party-api/`, `party-keycloak-spi/`, `party-integration-test/`.
- **UI will live under `party-ui/`** (to be created in this repo, sibling to
  the Maven modules). Do not build the Party admin UI inside orchestrix-v2,
  routesphere, or any other repo.

### Existing UI work to reference (not to extend in place)

A first-pass Party admin UI was already built inside orchestrix-v2 at
`/home/mustafa/telcobright-projects/orchestrix-v2/ui/src/pages/party/` (15 files)
plus supporting files:
- `orchestrix-v2/ui/src/services/party.js` — axios client + resolveTenantIdBySlug cache
- `orchestrix-v2/ui/src/hooks/usePartyTenantId.js`
- `orchestrix-v2/ui/src/App.jsx` — routes under `/:tenant/party/*` and `/:tenant/party/admin/*`
- `orchestrix-v2/ui/src/layouts/Sidebar.jsx`

Pages present there: `PartyStatusChip`, `PartySuperGuard`, `PartyTenantGate`,
`Operators` + `OperatorDetail`, `PartyTenants`, `OperatorUsers`, `Partners` +
`PartnerDetail`, `PartyUsers` + `PartyUserDetail`, `Roles` + `RoleDetail`,
`Permissions`, `SyncJobs`.

Treat those files as **the reference implementation to port into this repo's
`party-ui/`**, not as the live UI. New UI changes happen here.

## UI build conventions (per global CLAUDE.md)

- Never use React default port 3000 or anything in the 3000 range. Ask the user
  which port to use before scaffolding `party-ui/`.
- Forms: enough left/right padding so fields aren't ugly; keep fields vertically
  compact to minimize scroll.

## Config + deploy system (routesphere-style)

Already built this session. Do not rebuild — extend.

Party runs **per operator** (BTCL, etc.). The deploy-time term is "operator"
to avoid colliding with Party's runtime `Tenant` domain entity (which sits
below operator in the graph).

- `PartyProfileConfigSource.java` — custom Quarkus ConfigSource, ordinal 270,
  reads `(operator, profile)` from sysprops → env → `application.properties`.
  Sysprops: `-Dparty.operator.name`, `-Dparty.operator.profile`.
  Env: `PARTY_OPERATOR_NAME`, `PARTY_OPERATOR_PROFILE`.
- `application.properties` — **bootstrap stub only**. Runtime knobs live in YAML.
  Selector keys are `party.operators[N].{name,enabled,profile}`.
- `config/operators/<operator>/<profile>/profile-<profile>.yml` — runtime config
  per (operator, environment). The directory + selector use "operator", NOT
  Party's `Tenant` domain entity.
- Deploy: `tools/deploy/remote-deploy.sh <operator> <profile>` — builds uber
  fast-jar, ships over SSH (inventory reused from routesphere's
  `ssh-automation/servers/<operator>/`), installs as `party` user,
  systemd-managed. Secrets from `~/.secrets/party-<operator>.env`.
- One-shot Galera DDL: `tools/deploy/dependencies/bootstrap-db.sh`.

Adding a new operator = new `operator-conf/<op>.conf` + new
`config/operators/<op>/<env>/profile-<env>.yml` + reuse / add to the SSH inventory.

## Key infrastructure addresses (BTCL cluster)

- **Galera** overlay IPs: `10.10.199.20, 10.10.198.20, 10.10.197.20 : 3306`
  (kafka1/kafka2/kafka3 VMs). JDBC URL:
  `jdbc:mariadb:sequential://10.10.199.20:3306,10.10.198.20:3306,10.10.197.20:3306/party_master`
- **Temporal** overlay IPs, port 7233, plaintext (overlay is the trust boundary):
  `dns:///10.10.199.20:7233,10.10.198.20:7233,10.10.197.20:7233`, namespace `party-prod`.
- Party co-locates on kafka1/2/3 (3 active-active replicas).

## Gotchas (from AGENT_ONBOARDING §7 — re-check there before coding)

- `party.temporal.enabled` is **build-time** (`@IfBuildProperty`), not runtime.
  `remote-deploy.sh` passes `-Dparty.temporal.enabled=true` automatically for any
  `prod*` profile. Editing the YAML value alone does nothing.
- Dev Services provisions MariaDB for tests + `mvn quarkus:dev`. `profile-dev.yml`
  deliberately omits the datasource block so Dev Services wins.
- Panache active-record uses public fields; do NOT add Lombok `@Data` to entities.
- Every mutating service: `write master → enqueue sync_job → dispatch Temporal`.
  No shortcuts.
- `/internal/kc/*` is LAN-only (overlay or WireGuard). Never expose publicly.

## Where things are

- Design spec: `AGENT_ONBOARDING.md` (§4 is the file map — read top-down).
- Progress: `PROGRESS.md`, `NEXT_STEPS.md`.
- Routesphere config-migration plan (target state — central config service):
  `/home/mustafa/telcobright-projects/routesphere/routesphere-core/docs/config-system-and-central-db-migration.md`.

## Build + run

```bash
# dev build + unit tests (Dev Services provisions MariaDB — Docker must be up)
mvn install -DskipITs

# dev-mode app
cd party-api && mvn quarkus:dev        # → http://localhost:8081/api/v1

# deploy (see tools/deploy/README.md for first-time setup)
./tools/deploy/remote-deploy.sh btcl prod
```

# Party — Deploy Tooling

Routesphere-style config + deploy for the Party service.

Party runs **per operator** (BTCL, etc.). Each operator's deployment manages
many `Tenant` domain entities below it (operator → tenant → partner → user) —
do not confuse the deploy-time **operator** name with the runtime `Tenant` row.

## Layout

```
tools/deploy/
├── remote-deploy.sh                # main entry point
├── operator-conf/
│   └── btcl.conf                   # per-operator deploy config (INI)
├── dependencies/
│   ├── party.service               # systemd unit
│   ├── remote-setup.sh             # runs on remote host as root
│   └── bootstrap-db.sh             # one-shot Galera DB/user creation
├── secrets-template.env            # copy to ~/.secrets/party-<operator>.env
└── README.md
```

## Config system (in-repo)

`party-api/src/main/resources/`:
- `application.properties` — **bootstrap stub only** (operator selector + Quarkus essentials).
- `config/operators/<operator>/<profile>/profile-<profile>.yml` — runtime knobs (datasource, Temporal, Keycloak, JWT, HTTP port, logs).

`PartyProfileConfigSource` (ordinal 270) reads `(operator, profile)` from system properties → env vars → `application.properties`, then loads the YAML from the classpath (filesystem fallback for unpacked dev runs) and flattens it into Quarkus config.

Resolution keys:
- `-Dparty.operator.name=...` / `PARTY_OPERATOR_NAME=...`
- `-Dparty.operator.profile=...` / `PARTY_OPERATOR_PROFILE=...`
- fallback: first `party.operators[N].enabled=true` row in `application.properties`

Rule: **infrastructure endpoints live in the YAML, secrets live in env vars** referenced by the YAML via `${VAR}`. Secrets never sit in git.

## First-time setup

### 1. Secrets

```bash
cp tools/deploy/secrets-template.env ~/.secrets/party-btcl.env
chmod 600 ~/.secrets/party-btcl.env
# Fill in PARTY_DB_PASSWORD / PARTY_JWT_SECRET / PARTY_KC_INTEGRATION_SECRET.
# Generate values:
#   openssl rand -base64 24 | tr -d '/+=' | head -c 32   # DB password
#   openssl rand -base64 32                              # JWT + KC SPI secrets
```

### 2. Create the DB + user on Galera (one-shot)

```bash
# Requires ~/.secrets/galera-btcl.env with MARIADB_ROOT_PASSWORD
cd tools/deploy
./dependencies/bootstrap-db.sh 10.10.199.20   # any live Galera node
```

### 3. First deploy

```bash
./tools/deploy/remote-deploy.sh btcl prod
```

Subsequent nodes:
```bash
./tools/deploy/remote-deploy.sh btcl prod-n2 --skip-build
./tools/deploy/remote-deploy.sh btcl prod-n3 --skip-build
```

All three load the same `profile-prod.yml` — Party is stateless.

## Dev-local run

Still works the old way with Dev Services:

```bash
cd party-api && mvn quarkus:dev
```

`profile-dev.yml` deliberately omits the `quarkus.datasource` block — under `mvn quarkus:dev` and `mvn test`, Quarkus Dev Services auto-provisions a fresh MariaDB container and its datasource config wins.

## Inventory

SSH hosts come from the routesphere inventory (single source of truth across services). The inventory directory name happens to match the operator name for our customers:

```
/home/mustafa/telcobright-projects/routesphere/routesphere-core/tools/ssh-automation/servers/btcl/hosts/kafka{1,2,3}
```

`btcl.conf` references these by name (`server=kafka1`). Adding a new host = add the host file to routesphere's inventory and a new `[profile-X]` section here.

## Adding a new operator

1. Create routesphere SSH inventory: `routesphere-core/tools/ssh-automation/servers/<op>/hosts/<host-name>`.
2. Create `tools/deploy/operator-conf/<op>.conf` with `[dev]`, `[prod]`, etc. sections (`inventory_operator = <op>`, `server = <host-name>`).
3. Create `party-api/src/main/resources/config/operators/<op>/<profile>/profile-<profile>.yml`.
4. Add a row to `application.properties`: `party.operators[N].name=<op>` (kept disabled — it's only the build-time fallback; the deploy script overrides via `-Dparty.operator.name`).

## Changing config after deploy

Editing `profile-prod.yml` requires a rebuild + redeploy — Party is packaged as an uber fast-jar and the YAML is a classpath resource. A later phase will move this to a central config service (see `routesphere-core/docs/config-system-and-central-db-migration.md`); the same `PartyProfileConfigSource` will grow a `remote` mode at that point.

## Rollback

```bash
ssh <host>
sudo systemctl stop party
LATEST=$(ls -1t /opt/party/party-backup-*.tgz | head -1)
sudo tar -xzf $LATEST -C /opt/party
sudo chown -R party:party /opt/party/quarkus-app
sudo systemctl start party
```

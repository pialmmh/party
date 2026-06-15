#!/usr/bin/env bash
#
# remote-deploy-v2.sh — deploy partyV2 to it_vm (Temurin 21 + systemd), reading its
# HS256 key + DB creds from OpenBao at startup (quarkus-vault, AppRole).
#
# SECRET-FREE: no key/password/secret-id in this script or in git. The AppRole
# role-id + secret-id are read from OpenBao's host-only seal file ON it_vm and placed
# into the unit's Environment= (an EARLY config source — quarkus-vault fetches the
# secret-config during config bootstrap, before -Dquarkus.config.locations is merged,
# so the AppRole creds must be early). The HS256 key + DB password never leave OpenBao.
#
# Config split:
#   - jar application.properties : NON-secret vault wiring (url/tls/kv/secret-config/
#                                  credentials-provider) — must be an early base source.
#   - unit Environment=          : AppRole role-id + secret-id (the only secrets here).
#   - host party-v2.properties   : env-specific NON-secret runtime (jdbc url, nats, xmpp).
#
# Usage:  ./remote-deploy-v2.sh [--no-build]
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
SSH="/home/mustafa/telcobright-projects/routesphere/routesphere-core/tools/ssh-automation/servers/btcl/ssh"
JAR="$REPO/partyV2/target/party-v2-0.1.0-SNAPSHOT-runner.jar"
JDK21_LOCAL=/usr/lib/jvm/java-21-openjdk-amd64

if [ "${1:-}" != "--no-build" ]; then
  echo "[deploy] building uber-jar under JDK 21 (dev facade seed ON for this it_vm build)"
  # -Dsecurelink.devseed.enabled=true is a BUILD property (@IfBuildProperty): it
  # activates DevSeedFacadeDirectory in THIS jar so contact resolution works before the
  # real Odoo secure_link.facade addon is installed. A plain `mvn package` (no flag) keeps
  # the Odoo @DefaultBean — prod-safe.
  ( cd "$REPO" && JAVA_HOME="$JDK21_LOCAL" mvn -q -pl partyV2 -am -DskipTests package \
      -Dsecurelink.devseed.enabled=true )
fi
[ -f "$JAR" ] || { echo "[deploy] jar not found: $JAR (build first)" >&2; exit 1; }

echo "[deploy] pushing jar -> it_vm ($(du -h "$JAR" | cut -f1))"
"$SSH" it_vm "sudo mkdir -p /opt/secure-link/party && sudo chown debian:debian /opt/secure-link/party"
"$SSH" it_vm "cat > /opt/secure-link/party/party-v2-runner.jar" < "$JAR"

echo "[deploy] rendering host config + unit on it_vm (AppRole creds from OpenBao seal file)"
"$SSH" it_vm "sudo bash -s" <<'REMOTE'
set -euo pipefail
SEAL=/etc/openbao/seal/secure-link-approle.env
CONF=/opt/secure-link/conf/party-v2.properties
UNIT=/etc/systemd/system/partyv2.service
APP_DIR=/opt/secure-link/party
T21=/usr/lib/jvm/temurin-21-jdk-amd64
RID=$(grep '^role_id='   "$SEAL" | cut -d= -f2)
SID=$(grep '^secret_id=' "$SEAL" | cut -d= -f2)
[ -n "$RID" ] && [ -n "$SID" ] || { echo "missing AppRole creds in $SEAL" >&2; exit 1; }

# host config: env-specific NON-secret runtime only (vault wiring is in the jar's
# application.properties; AppRole creds are in the unit Environment= below).
mkdir -p /opt/secure-link/conf
umask 077
cat > "$CONF" <<EOF
# party-v2 host runtime config — rendered by remote-deploy-v2.sh. chmod 600.
# No secrets here (HS256 key + DB password live in OpenBao; AppRole creds in the unit).
quarkus.datasource.jdbc.url=jdbc:mysql://10.10.185.1:3306/party_v2?sslMode=DISABLED&serverTimezone=UTC&allowPublicKeyRetrieval=true&connectTimeout=5000&socketTimeout=30000&tcpKeepAlive=true
party.v2.contacts.nats.enabled=true
party.v2.contacts.nats.url=nats://10.10.185.1:4222
party.v2.registration.xmpp.host=10.10.185.1
party.v2.registration.otp.dev-mode=true
# tenant t1 -> the live Odoo 19 on this box (overlay IP). partyV2 authenticates end
# users against it with their own creds; no admin secret on the login path.
party.v2.tenants.t1.odoo.base-url=http://10.9.9.7:7170
party.v2.tenants.t1.odoo.db=platform_dev
# dev facade seed (the Odoo secure_link.facade addon is not installed in platform_dev
# yet, so contact owner/match resolution uses these seeded test users). Activated by the
# build flag above. +8801711111111 = the architect's canonical is-a-user test number;
# +8801710000001 = a test device owner. Remove once the real Odoo facade addon lands.
# (Namespace is securelink.* NOT party.v2.* — the latter is a @ConfigMapping root.)
securelink.devseed.numbers=+8801711111111,+8801710000001
EOF
chmod 600 "$CONF"; chown debian:debian "$CONF"

# systemd unit: AppRole role-id/secret-id as env (EARLY config source for quarkus-vault's
# bootstrap secret-config fetch). Env vars map to quarkus.vault.authentication.app-role.*.
cat > "$UNIT" <<EOF
[Unit]
Description=secure-link partyV2 (identity + contacts; secrets from OpenBao)
After=network-online.target
Wants=network-online.target
[Service]
User=debian
Environment=QUARKUS_VAULT_AUTHENTICATION_APP_ROLE_ROLE_ID=$RID
Environment=QUARKUS_VAULT_AUTHENTICATION_APP_ROLE_SECRET_ID=$SID
ExecStart=$T21/bin/java -Dquarkus.config.locations=$CONF -jar $APP_DIR/party-v2-runner.jar
Restart=on-failure
RestartSec=5
SuccessExitStatus=143
[Install]
WantedBy=multi-user.target
EOF
echo "rendered $CONF + $UNIT (role_id=$RID)"
REMOTE

echo "[deploy] starting partyv2.service"
"$SSH" it_vm "sudo systemctl daemon-reload && sudo systemctl enable partyv2 >/dev/null 2>&1; sudo systemctl restart partyv2"
sleep 8
echo "[deploy] ===== status ====="
"$SSH" it_vm "systemctl is-active partyv2; echo -n 'listening 18081: '; (ss -ltn | grep -q ':18081' && echo yes) || echo NO; echo '----- log -----'; sudo journalctl -u partyv2 --no-pager -n 25 | grep -ivE 'secret-id|SECRET_ID'"

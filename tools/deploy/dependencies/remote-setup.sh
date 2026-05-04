#!/bin/bash
#
# Remote-side installer for Party. Invoked by remote-deploy.sh over SSH as root.
# Expects the uber JAR + systemd unit already placed under /tmp/party-deploy/
# and the secrets env file at /tmp/party-deploy/party-secrets.env.
#
# Usage: remote-setup.sh <operator> <profile>
#
# Env vars (passed by remote-deploy.sh):
#   JVM_HEAP_MIN, JVM_HEAP_MAX, JVM_GC_TYPE, JVM_GC_MAX_PAUSE,
#   JVM_DEBUG_PORT, JVM_OPTIONS
#

set -e

OPERATOR="${1:-}"
PROFILE="${2:-}"

if [ -z "$OPERATOR" ] || [ -z "$PROFILE" ]; then
    echo "Usage: $0 <operator> <profile>" >&2
    exit 1
fi

JVM_HEAP_MIN="${JVM_HEAP_MIN:-2g}"
JVM_HEAP_MAX="${JVM_HEAP_MAX:-4g}"
JVM_GC_TYPE="${JVM_GC_TYPE:-G1GC}"
JVM_GC_MAX_PAUSE="${JVM_GC_MAX_PAUSE:-200}"
JVM_DEBUG_PORT="${JVM_DEBUG_PORT:-8791}"
JVM_OPTIONS="${JVM_OPTIONS:---add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED}"

# ---- user + dirs ----
if ! id -u party &>/dev/null; then
    echo "Creating 'party' user..."
    useradd -r -s /bin/false -d /opt/party party
fi
mkdir -p /opt/party /var/log/party /etc/party
chown -R party:party /opt/party /var/log/party
chown root:party /etc/party && chmod 750 /etc/party

# ---- write environment file (merge JVM settings + secrets) ----
SECRETS_FILE="/tmp/party-deploy/party-secrets.env"
if [ ! -f "$SECRETS_FILE" ]; then
    echo "ERROR: secrets file missing: $SECRETS_FILE" >&2
    echo "       remote-deploy.sh should have copied it from ~/.secrets/party-<tenant>.env" >&2
    exit 1
fi

cat > /etc/party/party.env <<EOF
# Party runtime environment — generated $(date -Iseconds) by remote-setup.sh
# DO NOT EDIT BY HAND. Redeploy to regenerate.

# Operator + profile selector (consumed by PartyProfileConfigSource)
PARTY_OPERATOR_NAME=$OPERATOR
PARTY_OPERATOR_PROFILE=$PROFILE

# JVM
JVM_HEAP_MIN=$JVM_HEAP_MIN
JVM_HEAP_MAX=$JVM_HEAP_MAX
JVM_GC_TYPE=$JVM_GC_TYPE
JVM_GC_MAX_PAUSE=$JVM_GC_MAX_PAUSE
JVM_DEBUG_PORT=$JVM_DEBUG_PORT
JVM_OPTIONS=$JVM_OPTIONS

EOF

# Append secrets (PARTY_DB_PASSWORD, PARTY_JWT_SECRET, PARTY_KC_INTEGRATION_SECRET, etc.)
cat "$SECRETS_FILE" >> /etc/party/party.env
chown root:party /etc/party/party.env
chmod 640 /etc/party/party.env

# ---- systemd unit ----
if [ -f /tmp/party-deploy/party.service ]; then
    mv /tmp/party-deploy/party.service /etc/systemd/system/party.service
    chmod 644 /etc/systemd/system/party.service
    systemctl daemon-reload
fi

systemctl enable party

# ---- start ----
echo "Starting party service..."
systemctl restart party

sleep 3
systemctl is-active --quiet party \
    && echo "  party is active" \
    || { echo "  party failed to start"; journalctl -u party --no-pager -n 50; exit 1; }

# ---- cleanup ----
rm -f "$SECRETS_FILE"

echo
echo "Setup complete — operator=$OPERATOR profile=$PROFILE"

#!/bin/bash
#
# Build & deploy Party to a remote VM owned by an operator.
#
# Party runs PER OPERATOR (BTCL etc.). "Tenant" is a domain entity below
# operator (operator → tenant → partner → user) — do NOT confuse the two.
#
# Usage:
#   ./remote-deploy.sh <operator> <profile> [--skip-build]
#
# Examples:
#   ./remote-deploy.sh btcl dev
#   ./remote-deploy.sh btcl prod               # node 1 (kafka1)
#   ./remote-deploy.sh btcl prod-n2            # node 2 (kafka2) — same profile-prod.yml
#   ./remote-deploy.sh btcl prod-n3            # node 3 (kafka3)
#   ./remote-deploy.sh btcl prod --skip-build  # reuse existing quarkus-app/
#
# SSH inventory is the routesphere-managed one (keyed by operator name):
#   /home/mustafa/telcobright-projects/routesphere/routesphere-core/tools/ssh-automation/servers/<operator>/
#
# Secrets come from ~/.secrets/party-<operator>.env (see secrets-template.env).
#

set -e

OPERATOR="${1:-}"
PROFILE="${2:-}"
SKIP_BUILD=false
[ "${3:-}" = "--skip-build" ] && SKIP_BUILD=true

if [ -z "$OPERATOR" ] || [ -z "$PROFILE" ]; then
    echo "Usage: $0 <operator> <profile> [--skip-build]"
    SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    echo
    echo "Available operator configs:"
    for f in "$SCRIPT_DIR/operator-conf"/*.conf; do
        [ -f "$f" ] && echo "  $(basename "${f%.conf}")"
    done
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PARTY_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
CONFIG_FILE="$SCRIPT_DIR/operator-conf/${OPERATOR}.conf"
INVENTORY_ROOT="/home/mustafa/telcobright-projects/routesphere/routesphere-core/tools/ssh-automation/servers"
QUARKUS_APP="$PARTY_ROOT/party-api/target/quarkus-app"
SECRETS_FILE="$HOME/.secrets/party-${OPERATOR}.env"

# ---- sanity ----
[ -f "$CONFIG_FILE" ] || { echo "ERROR: config not found: $CONFIG_FILE" >&2; exit 1; }
[ -f "$SECRETS_FILE" ] || {
    echo "ERROR: secrets file not found: $SECRETS_FILE" >&2
    echo "       Copy $SCRIPT_DIR/secrets-template.env and fill in real values." >&2
    exit 1
}
# Secrets file must be private
PERMS=$(stat -c '%a' "$SECRETS_FILE" 2>/dev/null || echo "")
if [ "$PERMS" != "600" ] && [ "$PERMS" != "400" ]; then
    echo "ERROR: $SECRETS_FILE perms are $PERMS — must be 600 or 400" >&2
    echo "       Fix: chmod 600 $SECRETS_FILE" >&2
    exit 1
fi

# ---- ini parser ----
parse_config() {
    local file="$1" section="$2" key="$3"
    awk -v section="[$section]" -v key="$key" '
        $0 == section { in_section=1; next }
        /^\[/         { in_section=0 }
        in_section {
            line=$0; gsub(/^[ \t]+/,"",line)
            if (line ~ "^" key "[ \t]*=") {
                idx=index($0,"="); v=substr($0,idx+1)
                gsub(/^[ \t]+|[ \t]+$/,"",v); print v; exit
            }
        }' "$file"
}

INVENTORY_OPERATOR=$(parse_config "$CONFIG_FILE" "$PROFILE" "inventory_operator")
SERVER_NAME=$(parse_config        "$CONFIG_FILE" "$PROFILE" "server")
DESCRIPTION=$(parse_config        "$CONFIG_FILE" "$PROFILE" "description")
PROFILE_YML=$(parse_config        "$CONFIG_FILE" "$PROFILE" "profile_yml")
# Default: YAML profile name = deploy profile name (prod uses profile-prod.yml).
# `profile_yml=prod` on prod-n2/prod-n3 maps all three nodes to profile-prod.yml.
PROFILE_YML=${PROFILE_YML:-$PROFILE}

if [ -z "$INVENTORY_OPERATOR" ] || [ -z "$SERVER_NAME" ]; then
    echo "ERROR: [$PROFILE] in $CONFIG_FILE must define inventory_operator + server" >&2
    exit 1
fi

HOST_FILE="$INVENTORY_ROOT/$INVENTORY_OPERATOR/hosts/$SERVER_NAME"
KEYS_DIR="$INVENTORY_ROOT/$INVENTORY_OPERATOR/keys"
[ -f "$HOST_FILE" ] || { echo "ERROR: host file not found: $HOST_FILE" >&2; exit 1; }

HOST=$(grep    -E "^host="        "$HOST_FILE" | cut -d= -f2)
PORT=$(grep    -E "^port="        "$HOST_FILE" | cut -d= -f2)
USER=$(grep    -E "^user="        "$HOST_FILE" | cut -d= -f2)
KEY_NAME=$(grep -E "^key="        "$HOST_FILE" | cut -d= -f2)
PRIVATE_IP=$(grep -E "^private_ip=" "$HOST_FILE" | cut -d= -f2)
SSH_KEY="$KEYS_DIR/$KEY_NAME"
[ -f "$SSH_KEY" ] || { echo "ERROR: ssh key not found: $SSH_KEY" >&2; exit 1; }

# ---- JVM knobs ----
JVM_HEAP_MIN=$(parse_config     "$CONFIG_FILE" "$PROFILE" "heap_min")
JVM_HEAP_MAX=$(parse_config     "$CONFIG_FILE" "$PROFILE" "heap_max")
JVM_GC_TYPE=$(parse_config      "$CONFIG_FILE" "$PROFILE" "gc_type")
JVM_GC_MAX_PAUSE=$(parse_config "$CONFIG_FILE" "$PROFILE" "gc_max_pause_ms")
JVM_DEBUG_PORT=$(parse_config   "$CONFIG_FILE" "$PROFILE" "debug_port")
JVM_OPTIONS=$(parse_config      "$CONFIG_FILE" "$PROFILE" "jvm_options")
JVM_HEAP_MIN=${JVM_HEAP_MIN:-2g}
JVM_HEAP_MAX=${JVM_HEAP_MAX:-4g}
JVM_GC_TYPE=${JVM_GC_TYPE:-G1GC}
JVM_GC_MAX_PAUSE=${JVM_GC_MAX_PAUSE:-200}
JVM_DEBUG_PORT=${JVM_DEBUG_PORT:-8791}

SERVER_ADDR="$USER@$HOST"

echo "========================================================"
echo "  Party Deploy"
echo "========================================================"
echo "Config:      $CONFIG_FILE"
echo "Inventory:   $INVENTORY_OPERATOR / $SERVER_NAME"
echo "Server:      $SERVER_ADDR:$PORT (private $PRIVATE_IP)"
echo "Operator:    $OPERATOR"
echo "Profile:     $PROFILE  (YAML: profile-$PROFILE_YML.yml)"
[ -n "$DESCRIPTION" ] && echo "Description: $DESCRIPTION"
echo "Build:       $( [ "$SKIP_BUILD" = true ] && echo 'SKIPPED (--skip-build)' || echo 'YES' )"
echo "JVM:         -Xms$JVM_HEAP_MIN -Xmx$JVM_HEAP_MAX $JVM_GC_TYPE pause=${JVM_GC_MAX_PAUSE}ms debug=$JVM_DEBUG_PORT"
echo

# ---- java 21 local ----
check_java_21() {
    local v
    v=$(java -version 2>&1 | head -1 | grep -oP '"\K[^"]+' | cut -d. -f1)
    [ "$v" = "21" ] && return 0
    for p in /usr/lib/jvm/java-21-openjdk-amd64 /usr/lib/jvm/temurin-21-jdk-amd64 /opt/java/jdk-21; do
        if [ -x "$p/bin/java" ]; then
            export JAVA_HOME="$p"; export PATH="$JAVA_HOME/bin:$PATH"
            echo "Switched to Java 21: $p"; return 0
        fi
    done
    return 1
}
echo "Checking Java 21..."
check_java_21 || { echo "ERROR: Java 21 required"; java -version; exit 1; }
echo

# ---- dirty tree guard ----
echo "Checking for uncommitted changes..."
DIRTY=$(cd "$PARTY_ROOT" && git status --porcelain 2>/dev/null | wc -l)
if [ "$DIRTY" -gt 0 ]; then
    echo "ERROR: working tree has $DIRTY uncommitted changes — commit or stash first" >&2
    (cd "$PARTY_ROOT" && git status --short | head -10)
    exit 1
fi
echo "  Working tree clean"
echo

# ---- cleanup trap ----
CONTROL_PATH="/tmp/ssh-party-$$"
APP_PROPS="$PARTY_ROOT/party-api/src/main/resources/application.properties"
APP_PROPS_BAK=""
cleanup() {
    if [ -n "$APP_PROPS_BAK" ] && [ -f "$APP_PROPS_BAK" ]; then
        cp "$APP_PROPS_BAK" "$APP_PROPS"
        rm -f "$APP_PROPS_BAK"
        echo "  Restored application.properties"
    fi
    ssh -O exit -o ControlPath="$CONTROL_PATH" "$SERVER_ADDR" 2>/dev/null || true
}
trap cleanup EXIT

# ---- build ----
if [ "$SKIP_BUILD" = false ]; then
    echo "========================================================"
    echo "  Build"
    echo "========================================================"
    APP_PROPS_BAK="${APP_PROPS}.deploy-bak"
    cp "$APP_PROPS" "$APP_PROPS_BAK"

    # Force the right operator/profile into the bootstrap stub so that even if
    # the remote systemd unit forgets -Dparty.operator.*, the bundled defaults match.
    sed -i "s/^\(party\.operators\[0\]\.name=\).*/\1${OPERATOR}/"           "$APP_PROPS"
    sed -i "s/^\(party\.operators\[0\]\.enabled=\).*/\1true/"               "$APP_PROPS"
    sed -i "s/^\(party\.operators\[0\]\.profile=\).*/\1${PROFILE_YML}/"     "$APP_PROPS"
    echo "  application.properties pinned to operator=$OPERATOR profile=$PROFILE_YML"

    # Temporal worker is bean-gated by @IfBuildProperty at Quarkus BUILD time.
    # Flip it on for any prod-* profile; dev leaves it off (NoopSyncDispatcher wins).
    # Tests are skipped here: with temporal enabled the test JVM would try to
    # register the worker against an unreachable target. Tests should be run
    # at dev time (`mvn install -DskipITs`) before invoking this script.
    BUILD_FLAGS="-DskipTests -DskipITs"
    if [[ "$PROFILE_YML" == prod* ]]; then
        BUILD_FLAGS="$BUILD_FLAGS -Dparty.temporal.enabled=true"
        echo "  Temporal worker: ENABLED at build time"
    else
        echo "  Temporal worker: disabled at build time (dev/non-prod)"
    fi

    echo "Running mvn install $BUILD_FLAGS ..."
    (cd "$PARTY_ROOT" && mvn -q install $BUILD_FLAGS)

    cp "$APP_PROPS_BAK" "$APP_PROPS"
    rm -f "$APP_PROPS_BAK"
    APP_PROPS_BAK=""
    echo "  application.properties restored"
fi
[ -d "$QUARKUS_APP" ] || { echo "ERROR: $QUARKUS_APP missing — build first" >&2; exit 1; }
echo

# ---- ssh master ----
echo "Establishing SSH connection..."
ssh -o ControlMaster=yes -o ControlPath="$CONTROL_PATH" -o ControlPersist=600 \
    -p "$PORT" -i "$SSH_KEY" \
    -o ConnectTimeout=10 -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o LogLevel=ERROR \
    -fN "$SERVER_ADDR" \
    || { echo "ERROR: cannot ssh to $SERVER_ADDR"; exit 1; }
echo "  connected"
echo

run_ssh() { ssh -o ControlPath="$CONTROL_PATH" -p "$PORT" "$SERVER_ADDR" "$@"; }
run_scp() { scp -o ControlPath="$CONTROL_PATH" -P "$PORT" "$@"; }

# ---- remote java 21 ----
echo "Checking Java 21 on remote..."
RJV=$(run_ssh "java -version 2>&1 | head -1 | sed -n 's/.*version \"\([0-9]*\).*/\1/p'" | tr -d '\n\r')
if [ "$RJV" != "21" ]; then
    echo "Installing Temurin 21 on remote..."
    CODENAME=$(run_ssh "lsb_release -cs 2>/dev/null || echo bookworm" | tr -d '\n\r')
    run_ssh "sudo apt-get update -qq && \
             sudo apt-get install -y -qq wget apt-transport-https gnupg && \
             wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | sudo gpg --dearmor -o /usr/share/keyrings/adoptium.gpg 2>/dev/null || true && \
             echo 'deb [signed-by=/usr/share/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb $CODENAME main' | sudo tee /etc/apt/sources.list.d/adoptium.list > /dev/null && \
             sudo apt-get update -qq && \
             sudo apt-get install -y -qq temurin-21-jdk"
    RJV=$(run_ssh "java -version 2>&1 | head -1 | sed -n 's/.*version \"\([0-9]*\).*/\1/p'" | tr -d '\n\r')
    [ "$RJV" = "21" ] || { echo "ERROR: Java 21 install failed"; exit 1; }
fi
echo "  Java 21 present"
echo

# ---- version info ----
GIT_COMMIT=$(cd "$PARTY_ROOT" && git rev-parse --short HEAD 2>/dev/null || echo unknown)
GIT_BRANCH=$(cd "$PARTY_ROOT" && git rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown)
DEPLOY_TIME=$(date '+%Y-%m-%d %H:%M:%S %z')
DEPLOY_TS=$(date '+%Y%m%d-%H%M')
DEPLOY_USER=$(whoami)

# ---- deploy ----
echo "========================================================"
echo "  Deploy"
echo "========================================================"

echo "Step 1/5: stopping remote party service..."
run_ssh "sudo systemctl stop party 2>/dev/null || true"
run_ssh "PIDS=\$(pgrep -f '[q]uarkus-run.jar' || true); \
    if [ -n \"\$PIDS\" ]; then \
        echo '  Killing leftover PIDs: '\$PIDS; \
        sudo kill \$PIDS 2>/dev/null || true; sleep 2; \
        STILL=\$(pgrep -f '[q]uarkus-run.jar' || true); \
        [ -n \"\$STILL\" ] && sudo kill -9 \$STILL 2>/dev/null || true; \
    fi"

echo "Step 2/5: backing up current install (keep last 3)..."
run_ssh "if [ -d /opt/party/quarkus-app ]; then \
    sudo tar -czf /opt/party/party-backup-${DEPLOY_TS}.tgz -C /opt/party quarkus-app 2>/dev/null; \
    sudo chown party:party /opt/party/party-backup-${DEPLOY_TS}.tgz; \
    ls -1t /opt/party/party-backup-*.tgz 2>/dev/null | tail -n +4 | xargs -r sudo rm -f; \
fi"

echo "Step 3/5: staging new artifacts..."
REMOTE_TMP="/tmp/party-deploy"
run_ssh "sudo rm -rf $REMOTE_TMP && sudo mkdir -p $REMOTE_TMP && sudo chown \$USER $REMOTE_TMP"

# Pack quarkus-app, ship it, unpack on remote — fast-jar has many small files.
LOCAL_TGZ="/tmp/party-$$-quarkus-app.tgz"
tar -czf "$LOCAL_TGZ" -C "$PARTY_ROOT/party-api/target" quarkus-app
run_scp "$LOCAL_TGZ"                                   "$SERVER_ADDR:$REMOTE_TMP/quarkus-app.tgz"
run_scp "$SCRIPT_DIR/dependencies/party.service"        "$SERVER_ADDR:$REMOTE_TMP/party.service"
run_scp "$SCRIPT_DIR/dependencies/remote-setup.sh"      "$SERVER_ADDR:$REMOTE_TMP/remote-setup.sh"
run_scp "$SECRETS_FILE"                                 "$SERVER_ADDR:$REMOTE_TMP/party-secrets.env"
rm -f "$LOCAL_TGZ"
run_ssh "chmod 600 $REMOTE_TMP/party-secrets.env && chmod +x $REMOTE_TMP/remote-setup.sh"

echo "Step 4/5: installing..."
run_ssh "sudo rm -rf /opt/party/quarkus-app && \
    sudo tar -xzf $REMOTE_TMP/quarkus-app.tgz -C /opt/party && \
    sudo chown -R party:party /opt/party/quarkus-app"

# write version file
run_ssh "sudo tee /opt/party/version.txt > /dev/null <<VEOF
project=party
commit=$GIT_COMMIT
branch=$GIT_BRANCH
operator=$OPERATOR
profile=$PROFILE
profile_yml=$PROFILE_YML
deployed_at=$DEPLOY_TIME
deployed_by=$DEPLOY_USER
VEOF
sudo chown party:party /opt/party/version.txt"
run_ssh "echo '${DEPLOY_TIME} | commit=${GIT_COMMIT} branch=${GIT_BRANCH} | operator=${OPERATOR} profile=${PROFILE} | by=${DEPLOY_USER}' | sudo tee -a /opt/party/deploy-history.log > /dev/null; \
         sudo chown party:party /opt/party/deploy-history.log"

echo "Step 5/5: setup + start..."
run_ssh "sudo JVM_HEAP_MIN='$JVM_HEAP_MIN' \
             JVM_HEAP_MAX='$JVM_HEAP_MAX' \
             JVM_GC_TYPE='$JVM_GC_TYPE' \
             JVM_GC_MAX_PAUSE='$JVM_GC_MAX_PAUSE' \
             JVM_DEBUG_PORT='$JVM_DEBUG_PORT' \
             JVM_OPTIONS='$JVM_OPTIONS' \
         bash $REMOTE_TMP/remote-setup.sh $OPERATOR $PROFILE_YML"
run_ssh "rm -rf $REMOTE_TMP"

echo
echo "========================================================"
echo "  Done"
echo "========================================================"
run_ssh "sudo systemctl status party --no-pager -l | head -20" || true
echo
echo "Logs:      ssh -i $SSH_KEY -p $PORT $SERVER_ADDR 'sudo journalctl -u party -f'"
echo "Health:    curl http://$PRIVATE_IP:18081/q/health"
echo "Debug:     attach IntelliJ Remote JVM Debug → $HOST : $JVM_DEBUG_PORT"
echo "Rollback:  ls /opt/party/party-backup-*.tgz   (tar -xzf + systemctl restart)"

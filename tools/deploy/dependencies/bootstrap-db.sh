#!/bin/bash
#
# One-shot Galera bootstrap for Party. Creates party_master DB + party user
# with least-privilege grants. Run this ONCE before the first deploy —
# remote-deploy.sh does NOT call this automatically (DDL on a cluster is a
# deliberate act, not a side-effect of `deploy`).
#
# Usage:
#   ./bootstrap-db.sh [galera-node-host] [root-user]
#
# Examples:
#   ./bootstrap-db.sh 10.10.199.20                  # default root user
#   ./bootstrap-db.sh 10.10.199.20 root
#
# Prereqs on the caller's shell:
#   - ~/.secrets/galera-btcl.env sourced OR MARIADB_ROOT_PASSWORD exported
#   - ~/.secrets/party-btcl.env  sourced OR PARTY_DB_PASSWORD       exported
#

set -e

GALERA_HOST="${1:-10.10.199.20}"
ROOT_USER="${2:-root}"

if [ -z "$MARIADB_ROOT_PASSWORD" ]; then
    if [ -f "$HOME/.secrets/galera-btcl.env" ]; then
        # shellcheck disable=SC1091
        . "$HOME/.secrets/galera-btcl.env"
    fi
fi
if [ -z "$PARTY_DB_PASSWORD" ]; then
    if [ -f "$HOME/.secrets/party-btcl.env" ]; then
        # shellcheck disable=SC1091
        . "$HOME/.secrets/party-btcl.env"
    fi
fi

if [ -z "$MARIADB_ROOT_PASSWORD" ]; then
    echo "ERROR: MARIADB_ROOT_PASSWORD not set and ~/.secrets/galera-btcl.env missing" >&2
    exit 1
fi
if [ -z "$PARTY_DB_PASSWORD" ]; then
    echo "ERROR: PARTY_DB_PASSWORD not set and ~/.secrets/party-btcl.env missing" >&2
    echo "       Generate one:  openssl rand -base64 24 | tr -d '/+=' | head -c 32"      >&2
    echo "       Store at:      ~/.secrets/party-btcl.env  (export PARTY_DB_PASSWORD=...)" >&2
    exit 1
fi

SQL=$(cat <<SQL
CREATE DATABASE IF NOT EXISTS party_master
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'party'@'10.10.%'   IDENTIFIED BY '${PARTY_DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'party'@'10.9.%'    IDENTIFIED BY '${PARTY_DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'party'@'127.0.0.1' IDENTIFIED BY '${PARTY_DB_PASSWORD}';

ALTER USER 'party'@'10.10.%'   IDENTIFIED BY '${PARTY_DB_PASSWORD}';
ALTER USER 'party'@'10.9.%'    IDENTIFIED BY '${PARTY_DB_PASSWORD}';
ALTER USER 'party'@'127.0.0.1' IDENTIFIED BY '${PARTY_DB_PASSWORD}';

GRANT ALL PRIVILEGES ON party_master.* TO 'party'@'10.10.%';
GRANT ALL PRIVILEGES ON party_master.* TO 'party'@'10.9.%';
GRANT ALL PRIVILEGES ON party_master.* TO 'party'@'127.0.0.1';

FLUSH PRIVILEGES;

SHOW DATABASES LIKE 'party_master';
SELECT User, Host FROM mysql.user WHERE User = 'party';
SQL
)

echo "Bootstrapping Galera at $GALERA_HOST ..."
mysql -h "$GALERA_HOST" -P 3306 -u "$ROOT_USER" -p"$MARIADB_ROOT_PASSWORD" <<< "$SQL"

echo
echo "party_master and party user ready on $GALERA_HOST (replicates to all Galera nodes)."

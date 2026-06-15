#!/usr/bin/env bash
#
# setup-openbao-it_vm.sh — bootstrap the dev secret store (OpenBao) on it_vm.
#
# Decision (user, 2026-06-15): secure-link secrets live in OpenBao, NOT env vars
# or scp'd files. This installs the minimal scope: KV v2 + AppRole, TLS on the
# private interface, file storage, a per-project read-only policy + AppRole, and
# seeds the two secure-link secrets (HS256 device-JWT key, party MySQL creds).
#
# SECRET-FREE BY DESIGN: this script contains no keys/passwords. It GENERATES the
# unseal key, root token, AppRole secret-id, HS256 key and DB password at runtime
# and writes them to host-only files under /etc/openbao/seal (chmod 600, root).
# Re-runnable (idempotent): existing install/init/seeds are left untouched.
#
# Run on it_vm as root:  sudo bash /tmp/setup-openbao-it_vm.sh
set -euo pipefail

BIND_IP=10.10.185.1
BAO_PORT=8200
export BAO_ADDR="https://${BIND_IP}:${BAO_PORT}"
export BAO_CACERT=/etc/openbao/tls/bao.crt

DATA=/var/lib/openbao/data
TLS=/etc/openbao/tls
SEAL=/etc/openbao/seal
INIT_JSON="$SEAL/init.json"
APPROLE_ENV="$SEAL/secure-link-approle.env"

say() { echo "[openbao-setup] $*"; }

# ── 1. system user + dirs ──────────────────────────────────────────────────
id openbao >/dev/null 2>&1 || useradd --system --home /var/lib/openbao --shell /usr/sbin/nologin openbao
mkdir -p "$DATA" "$TLS" "$SEAL" /etc/openbao
chown -R openbao:openbao /var/lib/openbao /etc/openbao/tls
chown -R root:root "$SEAL"; chmod 700 "$SEAL"

# ── 2. binary (latest OpenBao linux/amd64 tarball) ─────────────────────────
if ! command -v bao >/dev/null 2>&1; then
  say "fetching latest OpenBao release"
  URL=$(curl -fsSL https://api.github.com/repos/openbao/openbao/releases/latest | python3 -c '
import json,sys
d=json.load(sys.stdin)
for a in d["assets"]:
    n=a["name"]
    # the plain (non-hsm) linux/amd64 server tarball, named e.g. bao_2.5.4_Linux_x86_64.tar.gz
    if n.startswith("bao_") and n.endswith("_Linux_x86_64.tar.gz"):
        print(a["browser_download_url"]); break')
  [ -n "$URL" ] || { echo "could not resolve OpenBao download URL" >&2; exit 1; }
  say "downloading $URL"
  curl -fsSLo /tmp/bao.tgz "$URL"
  rm -rf /tmp/bao_extract && mkdir -p /tmp/bao_extract
  tar -xzf /tmp/bao.tgz -C /tmp/bao_extract
  BIN=$(find /tmp/bao_extract -type f -name bao | head -1)
  [ -n "$BIN" ] || { echo "bao binary not found inside tarball" >&2; exit 1; }
  install -m 0755 "$BIN" /usr/local/bin/bao
  rm -rf /tmp/bao.tgz /tmp/bao_extract
fi
say "bao version: $(bao version)"

# ── 3. self-signed TLS for the private bind IP ─────────────────────────────
if [ ! -f "$TLS/bao.crt" ]; then
  say "generating self-signed TLS cert"
  openssl req -x509 -newkey rsa:2048 -nodes -days 3650 \
    -keyout "$TLS/bao.key" -out "$TLS/bao.crt" \
    -subj "/CN=it_vm-openbao" \
    -addext "subjectAltName=IP:${BIND_IP},IP:10.9.9.7,IP:127.0.0.1,DNS:localhost"
  chown openbao:openbao "$TLS/bao.key" "$TLS/bao.crt"; chmod 640 "$TLS/bao.key"; chmod 644 "$TLS/bao.crt"
fi

# ── 4. config ──────────────────────────────────────────────────────────────
cat >/etc/openbao/openbao.hcl <<EOF
storage "file" { path = "$DATA" }
listener "tcp" {
  address       = "${BIND_IP}:${BAO_PORT}"
  tls_cert_file = "$TLS/bao.crt"
  tls_key_file  = "$TLS/bao.key"
}
api_addr       = "$BAO_ADDR"
disable_mlock  = true
ui             = false
EOF

# ── 5. systemd unit + start ────────────────────────────────────────────────
cat >/etc/systemd/system/openbao.service <<'EOF'
[Unit]
Description=OpenBao (dev secret store)
After=network-online.target
Wants=network-online.target
[Service]
User=openbao
Group=openbao
ExecStart=/usr/local/bin/bao server -config=/etc/openbao/openbao.hcl
ExecReload=/bin/kill --signal HUP $MAINPID
Restart=on-failure
LimitNOFILE=65536
AmbientCapabilities=CAP_IPC_LOCK
[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable --now openbao >/dev/null 2>&1 || systemctl restart openbao

# Pipefail-safe status probes: capture first (tolerate bao's exit-2 when sealed),
# THEN parse. A piped `bao status | python` would let pipefail propagate bao's
# non-zero exit and trigger a trailing `|| echo unknown`, smearing two values
# into the result — so we separate the call from the parse.
bao_field() {
  local out
  out=$(bao status -format=json 2>/dev/null) || true
  [ -n "$out" ] || { echo unknown; return; }
  printf '%s' "$out" | python3 -c "import json,sys
try: print(str(json.load(sys.stdin).get('$1')).lower())
except Exception: print('unknown')"
}
initialized() { bao_field initialized; }
sealed()      { bao_field sealed; }

# wait until the server answers with a real initialized=true/false (not 'unknown')
say "waiting for OpenBao to listen on ${BIND_IP}:${BAO_PORT}"
for i in $(seq 1 30); do
  st=$(initialized)
  if [ "$st" = "true" ] || [ "$st" = "false" ]; then break; fi
  sleep 1
done

# ── 6. init (single unseal key for dev) — the one irreducible bootstrap secret
if [ "$(initialized)" = "false" ]; then
  say "initializing (key-shares=1, threshold=1)"
  ( umask 077; bao operator init -key-shares=1 -key-threshold=1 -format=json > "$INIT_JSON" )
  chmod 600 "$INIT_JSON"; chown root:root "$INIT_JSON"
  say "init secrets stored host-only at $INIT_JSON"
else
  say "already initialized — leaving $INIT_JSON untouched"
fi

UNSEAL_KEY=$(python3 -c 'import json;print(json.load(open("'"$INIT_JSON"'"))["unseal_keys_b64"][0])')
ROOT_TOKEN=$(python3 -c 'import json;print(json.load(open("'"$INIT_JSON"'"))["root_token"])')

# ── 7. unseal now + on every boot ──────────────────────────────────────────
if [ "$(sealed)" = "true" ]; then
  say "unsealing"
  bao operator unseal "$UNSEAL_KEY" >/dev/null
fi

cat >/usr/local/bin/openbao-unseal.sh <<EOF
#!/usr/bin/env bash
set -euo pipefail
export BAO_ADDR="$BAO_ADDR"
export BAO_CACERT="$BAO_CACERT"
for i in \$(seq 1 30); do
  bao status >/dev/null 2>&1 && exit 0           # already unsealed
  bao status 2>&1 | grep -qi 'Sealed' && break   # up but sealed
  sleep 1
done
KEY=\$(python3 -c 'import json;print(json.load(open("$INIT_JSON"))["unseal_keys_b64"][0])')
bao operator unseal "\$KEY" >/dev/null
EOF
chmod 0750 /usr/local/bin/openbao-unseal.sh
cat >/etc/systemd/system/openbao-unseal.service <<'EOF'
[Unit]
Description=OpenBao auto-unseal (dev, host-only key)
After=openbao.service
Requires=openbao.service
[Service]
Type=oneshot
ExecStart=/usr/local/bin/openbao-unseal.sh
RemainAfterExit=yes
[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable openbao-unseal.service >/dev/null 2>&1 || true

# ── 8. authenticate as root for the one-time configuration ─────────────────
export BAO_TOKEN="$ROOT_TOKEN"

# KV v2 + AppRole (idempotent: already-enabled returns non-zero, ignore)
bao secrets enable -version=2 kv      >/dev/null 2>&1 || true
bao auth    enable approle            >/dev/null 2>&1 || true

# per-project read-only policy
bao policy write secure-link-read - >/dev/null <<'POL'
path "kv/data/secure-link/*"     { capabilities = ["read"] }
path "kv/metadata/secure-link/*" { capabilities = ["read","list"] }
POL

# per-project AppRole (how party logs in)
bao write auth/approle/role/secure-link \
  token_policies=secure-link-read secret_id_ttl=0 token_ttl=1h token_max_ttl=4h >/dev/null
ROLE_ID=$(bao read -field=role_id auth/approle/role/secure-link/role-id)
if [ ! -f "$APPROLE_ENV" ]; then
  SECRET_ID=$(bao write -f -field=secret_id auth/approle/role/secure-link/secret-id)
  ( umask 077; printf 'role_id=%s\nsecret_id=%s\n' "$ROLE_ID" "$SECRET_ID" > "$APPROLE_ENV" )
  chmod 600 "$APPROLE_ENV"; chown root:root "$APPROLE_ENV"
  say "AppRole creds stored host-only at $APPROLE_ENV"
else
  say "AppRole creds already present at $APPROLE_ENV — not regenerating secret-id"
fi

# ── 9. seed secure-link secrets (only if absent — never churn) ─────────────
# HS256 device-JWT key: GENERATED fresh. ejabberd uses auth_method:sql and
# sync-proxy/media auth=off, so nothing verifies a party JWT yet — party is the
# sole signer. Future verifiers read THIS same value.
if ! bao kv get kv/secure-link/hs256 >/dev/null 2>&1; then
  HS256=$(openssl rand -base64 48 | tr -d '\n')   # >=32 chars after strip
  bao kv put kv/secure-link/hs256 key="$HS256" >/dev/null
  say "seeded kv/secure-link/hs256 (generated fresh)"
else
  say "kv/secure-link/hs256 already seeded — left as-is"
fi

# party MySQL service creds: username fixed, password GENERATED. The MySQL user
# itself must be created separately with these values (needs DB-admin rights).
if ! bao kv get kv/secure-link/party-db >/dev/null 2>&1; then
  DBPASS=$(openssl rand -base64 24 | tr -d '/+=\n')
  bao kv put kv/secure-link/party-db username=partyv2 password="$DBPASS" >/dev/null
  say "seeded kv/secure-link/party-db (username=partyv2, password generated)"
else
  say "kv/secure-link/party-db already seeded — left as-is"
fi

# ── 10. verify the consumer path (AppRole login -> read both secrets) ──────
say "verifying AppRole consumer path"
RID=$(grep '^role_id='   "$APPROLE_ENV" | cut -d= -f2)
SID=$(grep '^secret_id=' "$APPROLE_ENV" | cut -d= -f2)
APP_TOKEN=$(BAO_TOKEN="" bao write -field=token auth/approle/login role_id="$RID" secret_id="$SID")
HS_OK=$(BAO_TOKEN="$APP_TOKEN" bao kv get -field=key      kv/secure-link/hs256    >/dev/null 2>&1 && echo ok || echo FAIL)
DB_OK=$(BAO_TOKEN="$APP_TOKEN" bao kv get -field=username kv/secure-link/party-db >/dev/null 2>&1 && echo ok || echo FAIL)

echo
say "================= RESULT ================="
say "address      : $BAO_ADDR  (CA: $BAO_CACERT)"
say "status       : initialized=$(initialized) sealed=$(sealed)"
say "approle      : role_id=$RID  (secret_id in $APPROLE_ENV)"
say "kv/hs256     : AppRole read = $HS_OK"
say "kv/party-db  : AppRole read = $DB_OK  (username=partyv2)"
say "host-only    : $INIT_JSON , $APPROLE_ENV"
say "=========================================="
[ "$HS_OK" = ok ] && [ "$DB_OK" = ok ] || { echo "VERIFY FAILED" >&2; exit 1; }

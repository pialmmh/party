-- =========================================================
-- Add app_name to tenant + seed the bound operator (btcl).
-- =========================================================
-- One Party deployment serves exactly one operator (bound at
-- JVM startup via YAML — see PartyProfileConfigSource). The
-- operator row must exist before any tenant can be created
-- under it, so we seed it here. INSERT IGNORE keeps the
-- migration idempotent across re-runs.

ALTER TABLE tenant
    ADD COLUMN app_name VARCHAR(40) NOT NULL DEFAULT 'orchestrix';

INSERT IGNORE INTO operator (short_name, full_name, operator_type, company_name, status)
VALUES ('btcl', 'BTCL operator', 'ITSP',
        'Bangladesh Telecommunications Company Limited', 'ACTIVE');

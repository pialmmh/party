# Odoo tenant template

`odoo-tenant-template.sql.gz` — gzipped plain-SQL dump of `odoo_billing_19`,
used by `OdooTenantProvisioner` to spawn a fresh per-tenant Odoo 19 database
during `ProvisionTenantWorkflow`.

## What's inside

A `pg_dump --no-owner --no-acl odoo_billing_19` snapshot. Targets an empty
destination database; emits no `CREATE DATABASE`, no `ALTER OWNER`, no
`GRANT`. Just schema + rows.

The provisioner creates the destination DB itself (`createdb odoo_<slug>`),
then streams this file in:

```bash
gunzip -c odoo-tenant-template.sql.gz | psql -h <host> -p 5433 -U <user> -d odoo_<slug>
```

## Properties

- **Source:** `odoo_billing_19` on dev host (Postgres :5433)
- **Format:** plain SQL, gzip-compressed (~2 MB compressed, ~17 MB plain)
- **Postgres version compatibility:** dumped with v16.13. Compatible with v15+ load targets.
- **Carries demo data.** This is the in-development instance DB and includes
  sample partners, products, etc. Acceptable per the current cutover plan;
  swap for a clean-install dump before going live.
- **Includes `\restrict` / `\unrestrict` psql meta-commands** at top/bottom
  (a safety feature emitted by pg_dump v16+). Load the file via the `psql`
  CLI; a JDBC-only loader would need to strip those two lines first.

## Regenerating

Whenever the source schema or seed data changes meaningfully, re-dump:

```bash
PGHOST=/run/postgresql PGPORT=5433 PGUSER=mustafa \
  pg_dump --no-owner --no-acl odoo_billing_19 \
  | gzip -9 \
  > party-tenant-projection/src/main/resources/artifact/scripts/odoo-tenant-template.sql.gz
```

Commit the new artifact alongside any code change that depends on it.

## Why it lives in resources

So it ships inside the party uber-jar — provisioning works on any deploy
target without an out-of-band file fetch. The file is read via
`getClass().getResourceAsStream("/artifact/scripts/odoo-tenant-template.sql.gz")`
and piped to a `psql` subprocess.

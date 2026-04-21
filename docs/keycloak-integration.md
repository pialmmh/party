# Party ↔ Keycloak Integration (Architecture Addendum)

**Date:** 2026-04-22.
**Status:** design locked after pivot discussion.

This amends `../routesphere-architect/party/plan.md`. Where the original plan said "Party issues JWTs directly to services", the real architecture is now:

```
      ┌────────────┐  1. login                 ┌────────────┐
      │  Browser   │ ─────────────────────────▶│  Keycloak  │
      │  / client  │   OIDC auth-code / ROPC   │ (realm)    │
      └────────────┘                           └─────┬──────┘
                                                     │ 2. validate creds
                                                     │    (User Storage SPI)
                                                     ▼
                                              ┌─────────────┐
                                              │    Party    │
                                              │   master    │
                                              └─────────────┘
      ┌────────────┐  3. token w/ claims      ┌────────────┐
      │  Browser   │ ◀────────────────────────│  Keycloak  │
      └─────┬──────┘                          └────────────┘
            │ 4. API call, Authorization: Bearer <kc-token>
            ▼
      ┌────────────────────────────────────────────────────┐
      │  downstream services (routesphere-core, RTC-Mgr)   │
      │  - verify token against Keycloak JWKS              │
      │  - read authorization data from their LOCAL        │
      │    per-tenant DB (partner, auth_user, auth_role,   │
      │    auth_permission, ip_access_rule, etc.)          │
      │  - Party REST is NEVER called by services          │
      └────────────────────────────────────────────────────┘
```

---

## 1. Locked decisions

| # | Decision |
|---|---|
| 1 | **Option A** — Keycloak User Storage SPI. Party stays the authoritative user store. Keycloak calls Party on every login to validate credentials. |
| 2 | **Services always read local** tenant-DB data. They do NOT call Party REST. Only Keycloak calls Party. |
| 3 | Keycloak **24.0.5** (already deployed, dev mode, `http://localhost:7104`). |
| 4 | Passwords remain in Party (`operator_user.password_hash`, `auth_user.password_hash`, BCrypt). Keycloak does NOT hold credentials — it delegates to Party via the SPI. |
| 5 | The **5 unfinished tenant-DB projectors are still needed** — services read roles/permissions/ACL from local DB, so the projection must fill those tables. |

---

## 2. Implications for the existing code

### 2a. Kept (was uncertain, now confirmed necessary)

- `PasswordHasher` — Keycloak's SPI will call this through Party to verify bcrypt.
- `operator_user` / `auth_user` tables with `password_hash` columns — authoritative.
- All 13 JPA entities, REST CRUD, Temporal projection machinery.
- All 5 remaining projection writers — **now implemented** (see commit log).

### 2b. Deprecated but kept for transition

- `AuthResource /auth/login` + `TokenService` — Party no longer issues service-facing tokens. Keep the login endpoint **for Party's own admin UI** only. Downstream services never consume this token.
- Rename long-term: `/auth/login` → `/internal/admin-login` (Phase: when UI flips to Keycloak too).

### 2c. New modules

- **`party-keycloak-spi`** — a Keycloak provider jar deployed to `$KC_HOME/providers/`. Implements `UserStorageProviderFactory` + `UserStorageProvider` + `UserLookupProvider` + `CredentialInputValidator`. Talks to Party via a thin internal REST endpoint.
- **New Party REST under `/internal/kc/`** — endpoints only Keycloak calls (authenticated with a shared-secret header, NOT open to the world):
  - `GET  /internal/kc/users/by-username?realm=<realm>&username=<u>` → user record or 404
  - `GET  /internal/kc/users/by-id?realm=<realm>&id=<id>` → user record
  - `POST /internal/kc/users/validate-credentials` body `{realm, username, password}` → `{valid: true|false}`
  - `GET  /internal/kc/users/search?realm=<realm>&q=<query>&first=<int>&max=<int>` → paged list

---

## 3. Realm / user-federation model

### 3a. Two realm kinds

| Realm | Purpose | User source |
|---|---|---|
| `master` (Keycloak's built-in) | Keycloak administration only. Manual admin users. | native Keycloak |
| `party-operators` | Login for `operator_user` (SYS_ADMIN + OPERATOR_ADMIN) | User Storage SPI → Party `/internal/kc/...?realm=party-operators` |
| `tenant-<operatorShort>-<tenantShort>` (e.g., `tenant-btcl-btcl`) | Login for tenant users (`auth_user`) | User Storage SPI → Party `/internal/kc/...?realm=tenant-btcl-btcl` |

One realm per tenant keeps blast-radius of a compromised realm small and maps 1:1 onto our per-tenant DB model.

### 3b. Realm provisioning

When a tenant is provisioned (`ProvisionTenantWorkflow`), append one step:
- `KeycloakRealmActivity.createRealm(tenantId)` — calls Keycloak Admin API to:
  1. Create realm `tenant-<opShort>-<tnShort>`.
  2. Attach the `party-user-storage` provider, configured with `tenantId` as a provider-config attribute.
  3. Create realm roles mirroring the seeded `auth_role` rows (`admin`, `reseller`, `agent`, `viewer`).
  4. Configure a client (the tenant's app) with token mappers for `tenantId`, `partnerId`, `scope`, `roles[]`.

### 3c. Token claims (what downstream services see)

```json
{
  "iss": "http://keycloak/realms/tenant-btcl-btcl",
  "sub": "<kc-subject>",
  "preferred_username": "alice@example.com",
  "operatorId": 1,
  "tenantId": 5,
  "partnerId": 42,
  "scope": "TENANT_USER",
  "roles": ["admin", "reseller"],
  "permissions": ["partner:read:own", "user:write:tenant"]
}
```

`tenantId`, `partnerId`, `roles`, `permissions` are injected by a Keycloak protocol mapper that reads them from the `UserStorageProvider` (which reads from Party) or from Keycloak's own user attributes seeded at login.

---

## 4. Credential flow (step-by-step)

1. User submits `POST /realms/tenant-btcl-btcl/protocol/openid-connect/token` with `grant_type=password&username=alice@...&password=...`.
2. Keycloak resolves the user via the storage provider chain; our `PartyUserStorageProvider.getUserByUsername("alice@...")` runs:
   - HTTP `GET /internal/kc/users/by-username?realm=tenant-btcl-btcl&username=alice@...`
   - Party decodes realm → `(operatorId=1, tenantId=5)`, queries `auth_user WHERE tenant_id=5 AND email=?`.
   - Returns `{id, email, firstName, lastName, attributes: {tenantId, partnerId, roles, permissions}}`.
3. Keycloak calls our `CredentialInputValidator.isValid(...)` to verify the password:
   - HTTP `POST /internal/kc/users/validate-credentials {realm, username, password}` → `{valid: true}` after Party BCrypt-verify.
4. Keycloak issues an access token. Protocol mappers copy user attributes into claims.
5. Services receive the token → verify JWKS from Keycloak → read `tenant_id`, `partner_id`, `roles`, `permissions` from claims → look up business data in local tenant DB.

---

## 5. Sync of master → Keycloak

We do NOT sync users from Party to Keycloak. The SPI pulls on demand.

But **realm configuration** (the realm itself, roles, client, mappers) IS a one-time-per-tenant push done by `KeycloakRealmActivity` inside the provisioning workflow. If realm config drifts, re-run the activity.

**User changes (email rename, suspend, delete) propagate automatically** because every login hits the SPI → Party. No sync required for user data. But Keycloak caches user lookups (configurable); set the cache TTL low (60 s) for stateful attributes like status.

---

## 6. Security

- The `/internal/kc/*` endpoints are authenticated with a **shared secret header** `X-KC-Integration-Secret: <secret>`. NOT JWT (chicken-and-egg). Secret rotated via env var `PARTY_KC_INTEGRATION_SECRET`.
- These endpoints are **not** exposed to the public LB — bound to the internal network only (WireGuard / same VPC as Keycloak).
- Rate-limit `validate-credentials` to prevent online brute force.
- Audit every `validate-credentials` call with outcome (success/fail/user-not-found), even successful ones.

---

## 7. Work items

### Done (this commit)
- All 5 projection writers implemented (services need local reads).
- This document.

### To do — order of work for the next session

1. **`party-keycloak-spi/` module**
   - Maven module packaged as jar with Keycloak deps as `provided`.
   - `PartyUserStorageProviderFactory` + `PartyUserStorageProvider` implementing `UserLookupProvider` + `CredentialInputValidator` + `UserQueryProvider`.
   - HTTP client hits Party `/internal/kc/*` with shared-secret header.
   - Deploy: `cp party-keycloak-spi/target/*.jar /opt/keycloak-24.0.5/providers/ && $KC_HOME/bin/kc.sh build && restart`.
2. **Party `/internal/kc/*` REST endpoints** (in `party-api`):
   - New resource class `InternalKcResource`, guarded by `@SharedSecretAuth` filter.
   - Realm-name → (operatorId, tenantId) decoder utility.
3. **`KeycloakAdminClient`** (in `party-tenant-projection`):
   - Uses Keycloak Admin REST to create realm, roles, client, mappers during `ProvisionTenantWorkflow`.
   - Needs a service account user in the `master` realm with `manage-realm` + `manage-users` roles.
4. **`ProvisionTenantWorkflow` addition**:
   - After `seedDefaultRolesAndPermissions`, add `createKeycloakRealm` activity.
5. **Deprecate `AuthResource`**:
   - Rename path to `/internal/admin-login`, keep only for Party's own admin UI.
   - Add a banner in OpenAPI: "Internal use only — downstream services authenticate via Keycloak."
6. **End-to-end test**:
   - Spin a Keycloak testcontainer with our SPI preloaded.
   - Create an operator + tenant + user via Party REST.
   - Hit Keycloak token endpoint with the user's creds.
   - Verify: token issued, claims include `tenantId`/`partnerId`/`roles`.

---

## 8. Open items / future polish

- Keycloak **event listener SPI** — when a user changes password via Keycloak's account console, push the new hash back to Party. Or: disable self-service password change in Keycloak and require Party's admin UI.
- **MFA** — handled entirely in Keycloak; Party doesn't see or store second factors.
- **SSO across tenants** — if one admin needs to jump between tenants, they need accounts in each realm OR we use a cross-realm broker. Out of scope for v1.
- **Keycloak HA** — when Keycloak goes to 3-node prod (to match Party's HA), revisit config (shared Infinispan cache, DB config).

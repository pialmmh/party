# Party Service — React Admin UI Requirements

**Audience:** agent building the Party admin frontend.
**Backend:** `party-service` (Quarkus, JDK 21), REST surface at `/api/v1/*`.
**Backend reference:** `party/README.md` (endpoint list), `../routesphere-architect/party/plan.md` (schema + auth model).
**Target dir:** `/home/mustafa/telcobright-projects/party-ui/` (new sibling repo).

**Status:** spec. Before starting, confirm port (user's global rule: never port 3000 range; ask before picking). Default recommendation: **Vite dev on 7400, built bundle served by nginx inside LXC on 7401**. Get user approval before finalizing.

---

## 1. Goal

A single React admin SPA that covers:

1. **Operator & tenant management** (SYS_ADMIN + OPERATOR_ADMIN scope).
2. **Tenant-scoped party CRUD** (partners, users, roles, permissions, ACLs, UI-menu overrides).
3. **Sync-job monitoring** (read-only audit of Temporal-driven projections).
4. **Auth flow** (login, refresh, logout; password reset for one's own user).

Non-goals: tenant-side end-user apps (login portal, self-service); those live in the downstream services that consume Party data.

---

## 2. Tech Stack — Locked

| Layer | Choice | Why |
|---|---|---|
| Framework | **React 18 + Vite** | Project convention; never CRA; never Next for internal admin. |
| Language | **TypeScript** | All new code TS; `.tsx` for components. |
| UI primitives | **MUI v5** | Project convention; no Tailwind, no Bootstrap. |
| Styling | **CSS custom properties + MUI theme** | Token system per `../routesphere-architect/campaign/docs/styling-guideline.md`. No hardcoded colors. |
| Routing | **React Router v6** | Nested routes per operator / tenant context. |
| State / async | **React Query (TanStack Query v5)** | Caching + stale-while-revalidate; less ceremony than Redux. |
| HTTP | **axios** with interceptors | 401 → redirect to login; 403 → scope-error toast. |
| Forms | **react-hook-form + zod** | Schema-driven validation; no hand-rolled field-by-field state. |
| Dates | **date-fns** | Consistent formatting. |
| Tables | **MUI DataGrid** (free tier) | Sort, filter, pagination out of the box. |
| Icons | **@mui/icons-material** | Default. |
| Build | **Vite** with tenant + env config pattern (see §11). |
| Tests | **Vitest + @testing-library/react** | Unit + component; Playwright later for smoke. |
| Package manager | **pnpm** | Faster than npm; avoid yarn mix. |

**Do not** add: Tailwind, Redux/Zustand, Formik, moment.js, Emotion (beyond what MUI uses).

---

## 3. Architecture

```
party-ui/
├── index.html
├── package.json                    pnpm; scripts: dev, build, preview, test, lint
├── tsconfig.json
├── vite.config.ts                  tenant+env config pattern
├── .env.local.example
├── public/
│   ├── favicon.ico
│   └── logos/
├── src/
│   ├── main.tsx                    Root render; wraps ThemeProvider + QueryClient + Router
│   ├── App.tsx                     Routes + auth guard
│   ├── configs/
│   │   ├── index.ts                Selects env config by VITE_PARTY_ENV
│   │   ├── local.ts                local dev (Dev Services)
│   │   ├── dev.ts                  shared dev cluster
│   │   └── prod.ts                 production
│   ├── api/
│   │   ├── client.ts               axios instance + 401/403 interceptors
│   │   ├── auth.ts                 login, refresh
│   │   ├── operators.ts
│   │   ├── tenants.ts
│   │   ├── operatorUsers.ts
│   │   ├── partners.ts
│   │   ├── partnerExtras.ts
│   │   ├── authUsers.ts
│   │   ├── roles.ts
│   │   ├── permissions.ts
│   │   ├── ipRules.ts
│   │   ├── menuPerms.ts
│   │   └── syncJobs.ts
│   ├── auth/
│   │   ├── AuthContext.tsx         JWT claims, currentScope, operatorId, tenantId
│   │   ├── useAuth.ts
│   │   ├── ProtectedRoute.tsx
│   │   └── tokenStore.ts           access + refresh token persistence (sessionStorage)
│   ├── theme/
│   │   ├── tokens.css              base CSS custom properties
│   │   ├── theme-light.css
│   │   ├── theme-dark.css
│   │   ├── muiTheme.ts             MUI theme that reads from CSS variables
│   │   └── ThemeProvider.tsx
│   ├── components/
│   │   ├── AppShell.tsx            sidebar (60px) + topbar (52px) + <Outlet/>
│   │   ├── Sidebar.tsx
│   │   ├── TopBar.tsx              tenant/operator switcher + user menu
│   │   ├── ScopeSwitcher.tsx       operator & tenant picker (visible per role)
│   │   ├── PageHeader.tsx
│   │   ├── DataTable.tsx           wraps MUI DataGrid with our defaults
│   │   ├── ConfirmDialog.tsx
│   │   ├── FormDialog.tsx          modal form wrapper
│   │   ├── StatusBadge.tsx         ACTIVE / SUSPENDED / DELETED / PROVISIONING...
│   │   ├── SyncStatusChip.tsx      PENDING / RUNNING / SUCCESS / FAILED
│   │   ├── EmptyState.tsx
│   │   ├── LoadingBlock.tsx
│   │   └── ErrorBlock.tsx
│   ├── forms/
│   │   ├── fields/                 TextField, SelectField, BoolField, PasswordField — all react-hook-form-integrated
│   │   └── schemas/                zod schemas per entity (see §7)
│   ├── pages/
│   │   ├── auth/
│   │   │   ├── LoginPage.tsx
│   │   │   └── LogoutPage.tsx
│   │   ├── dashboard/
│   │   │   └── DashboardPage.tsx
│   │   ├── operators/
│   │   │   ├── OperatorListPage.tsx
│   │   │   ├── OperatorDetailPage.tsx
│   │   │   └── OperatorFormDialog.tsx
│   │   ├── tenants/
│   │   │   ├── TenantListPage.tsx             (under operator context)
│   │   │   ├── TenantDetailPage.tsx
│   │   │   └── TenantFormDialog.tsx
│   │   ├── operatorUsers/
│   │   │   ├── OperatorUserListPage.tsx
│   │   │   └── OperatorUserFormDialog.tsx
│   │   ├── partners/
│   │   │   ├── PartnerListPage.tsx
│   │   │   ├── PartnerDetailPage.tsx           (tabs: Overview, Extra, Users)
│   │   │   └── PartnerFormDialog.tsx
│   │   ├── users/
│   │   │   ├── UserListPage.tsx                (tenant-scoped)
│   │   │   ├── UserDetailPage.tsx              (tabs: Overview, Roles, IP Rules, Menu Perms)
│   │   │   ├── UserFormDialog.tsx
│   │   │   ├── RoleAssignmentPanel.tsx
│   │   │   ├── IpRulesPanel.tsx
│   │   │   └── MenuPermissionsPanel.tsx
│   │   ├── roles/
│   │   │   ├── RoleListPage.tsx
│   │   │   ├── RoleDetailPage.tsx              (tab: Permissions)
│   │   │   └── RoleFormDialog.tsx
│   │   ├── permissions/
│   │   │   ├── PermissionListPage.tsx
│   │   │   └── PermissionFormDialog.tsx
│   │   └── syncJobs/
│   │       └── SyncJobListPage.tsx
│   ├── hooks/
│   │   ├── useOperators.ts        React Query hooks per entity; keys are stable tuples
│   │   └── ...
│   ├── types/
│   │   └── api.ts                 TS types mirroring Party DTOs + entities
│   └── utils/
│       ├── scope.ts               scope-based route guards
│       └── errors.ts
└── tests/
    ├── setup.ts
    └── pages/...
```

**Route table** (all inside `<AppShell/>` except `/login`):

```
/login
/                                   dashboard
/operators                          (SYS_ADMIN only)
/operators/:operatorId              tabs: Overview, Tenants, Operator Users
/operators/:operatorId/tenants/:tenantId       redirect → /tenants/:tenantId
/tenants/:tenantId                  tabs: Overview, Partners, Users, Roles, Permissions, Sync Jobs
/tenants/:tenantId/partners/:partnerId        tabs: Overview, Extra, Users
/tenants/:tenantId/users/:userId              tabs: Overview, Roles, IP Rules, Menu Perms
/tenants/:tenantId/roles/:roleId              tabs: Overview, Permissions
/tenants/:tenantId/sync-jobs
/operator-users                     (SYS_ADMIN only)
```

---

## 4. Authentication & Authorization

### 4.1 Login flow

`POST /api/v1/auth/login` → `{ accessToken, refreshToken, scope, userId, operatorId }`.

- Store tokens in **sessionStorage** (not localStorage — limits XSS blast radius).
- Decode the access token client-side (no verification; server verifies) to extract claims.
- Populate `AuthContext`: `{ subjectId, email, scope, operatorId, tenantId?, partnerId?, roles[], permissions[] }`.
- Redirect to `/` after login; if there was a `?next=<url>`, go there instead.

### 4.2 Token refresh

- Access token TTL 15 min. Before expiry (or on 401), axios interceptor calls `POST /api/v1/auth/refresh`.
- If refresh fails (or no refresh token), clear store and redirect to `/login`.
- One refresh in flight at a time (singleton promise guard).

### 4.3 Scope-based route guards

- `ProtectedRoute` wraps any route requiring auth.
- `SysAdminRoute` allows only `scope === 'SYS_ADMIN'`.
- `OperatorScopedRoute` — requires `scope` ∈ {`SYS_ADMIN`, `OPERATOR_ADMIN`} AND param `:operatorId` matches `operatorId` in claims (unless SYS_ADMIN).
- `TenantScopedRoute` — loads the tenant, checks its operatorId against scope. SYS_ADMIN sees all.
- On denial: show 403 page with "Your scope is X; this requires Y" and a back button. Do NOT silently redirect.

### 4.4 API error → UI mapping

| HTTP | UI behavior |
|---|---|
| 400 | Inline form error(s); show field-level messages if `{field, message}` shape is returned, else toast. |
| 401 | Interceptor attempts refresh once; on failure, redirect to `/login?next=<current>`. |
| 403 | Toast: "You don't have permission for this action"; keep user on current page. |
| 404 | Page-level empty state with "Not found" + back link. |
| 409 | Toast: "Conflict — <server message>"; for uniqueness violations, surface on the field. |
| 5xx | Toast: "Server error — try again shortly"; log the request-id if the server includes one. |

---

## 5. Screen Inventory (by scope)

### 5.1 SYS_ADMIN (party-service super-admin)

- Dashboard: counts of operators, tenants, active users, pending sync jobs across system. Top 5 failed jobs in last 24 h.
- Operators: list, create, edit, suspend, delete.
- Operator users: list all, create, reset password, suspend, delete.
- Tenants: list across all operators; filter by operator.
- Global sync job list with filters (operator, tenant, status, entity_type).

### 5.2 OPERATOR_ADMIN (admin of one operator)

- Dashboard: counts for their operator only; tenant breakdown.
- Operator detail (read-only for identity fields; can edit address/phone/email).
- Tenants under operator: create, edit, suspend, delete. View provisioning status.
- Within each tenant: full tenant-scoped CRUD (partners, users, roles, permissions, sync jobs).
- **Cannot** see operator-users list, cannot create/delete operators.

### 5.3 Tenant-scoped screens (same for either role when operating inside a tenant)

Partners, users, roles, permissions, IP rules, menu permissions. Sync-job audit.

---

## 6. Common Components

### 6.1 AppShell

- **Fixed sidebar** (60 px) with icon-only nav; tooltips on hover.
- **Top bar** (52 px): tenant/operator switcher (center-left) + user menu (right: current user, scope chip, logout).
- **Main content**: page body with `var(--space-6)` padding; `<Outlet/>` for nested routes.
- Theme reference: `../routesphere-architect/campaign/docs/styling-guideline.md` §5.

### 6.2 ScopeSwitcher

- SYS_ADMIN: dropdown showing all operators → expand to show tenants under each.
- OPERATOR_ADMIN: no operator choice (fixed); tenant dropdown.
- TENANT_USER: no switcher (scope is fixed).
- Changing selection updates the URL (pushes `/tenants/:tenantId`) and resets any in-page filters.

### 6.3 DataTable

Wraps MUI DataGrid with:

- Default `pageSize=25`, `pageSizeOptions=[10, 25, 50, 100]`.
- Built-in column for status → `<StatusBadge/>`.
- Row actions column (right-aligned, sticky): edit / delete (icon buttons), with optional extra actions per screen.
- Empty state: `<EmptyState title message actionLabel onAction/>`.
- Errors: `<ErrorBlock/>` with Retry button that refetches the React Query key.

### 6.4 FormDialog

- MUI Dialog with `maxWidth="sm"` (default) or `"md"`.
- Header: title + close icon.
- Footer: Cancel + Save (primary).
- Body: `<form onSubmit={handleSubmit(onSave)}>` with `react-hook-form`.
- **Padding:** `var(--space-5)` left/right on the form body (user's explicit rule: don't make fields full-width and ugly).
- **Field spacing:** vertical spacing `var(--space-3)` between fields (keep forms compact; minimize scroll).
- Save button shows spinner while mutation pending; disabled while invalid or pending.
- Success: toast + close dialog + invalidate relevant query keys. Failure: inline error via `setError` per field if 400, otherwise toast.

### 6.5 ConfirmDialog

- "Are you sure?" for DELETE, suspend, provision, password-reset.
- Requires typed confirmation for destructive ops on tenants (type the tenant short_name to confirm). For lesser ops, a plain yes/no is fine.

### 6.6 StatusBadge & SyncStatusChip

| State | Color token | Example |
|---|---|---|
| ACTIVE / SUCCESS | `--color-success` | operator ACTIVE, tenant ACTIVE, sync SUCCESS |
| PROVISIONING / RUNNING / PENDING | `--color-warning` | tenant PROVISIONING, sync PENDING |
| SUSPENDED | `--color-neutral` | operator/tenant/user SUSPENDED |
| DELETED | `--color-text-muted` with strikethrough | soft-deleted |
| FAILED / LOCKED | `--color-danger` | sync FAILED, user LOCKED |

Exact pill shape per `styling-guideline.md` §6 ("Status Badge (pill)").

---

## 7. Per-Entity Screens & Validation

All validation schemas live in `src/forms/schemas/`. Use **zod** and infer TS types from the schema where possible.

### 7.1 Operator (`/operators`, SYS_ADMIN only)

**List columns:** shortName, fullName, operatorType, country, status, tenantCount (derived, optional), updatedAt.

**Create/Edit form fields:**

| Field | Validation |
|---|---|
| shortName | required, lowercase, `/^[a-z0-9]{2,40}$/`, unique (backend enforces) |
| fullName | required, 3–200 chars |
| operatorType | required, enum select: MNO / MVNO / ISP / ITSP / ENTERPRISE / OTHER |
| companyName | optional, ≤ 200 |
| address1 | optional, ≤ 200 |
| city | optional, ≤ 80 |
| country | optional, ISO 3166-1 alpha-2 suggested (`/^[A-Z]{2}$/` if provided) |
| phone | optional, E.164-ish: `/^\+?[0-9 \-]{5,40}$/` |
| email | optional, valid email |
| status | select (ACTIVE / SUSPENDED); DELETED not editable — use delete action |

**Actions:** create, edit, suspend (sets status), delete (soft → status=DELETED).

### 7.2 Tenant (`/operators/:opId/tenants` and `/tenants/:id`)

**List columns:** shortName, fullName, status, dbName (derived), createdAt.

**Create form fields** (only creation-only fields; edit shares most except db_* are read-only):

| Field | Validation |
|---|---|
| shortName | required, lowercase, `/^[a-z0-9]{2,40}$/`, unique within operator |
| fullName | required, 3–200 |
| companyName, address1, city, country, phone, email | same as Operator |
| dbHost | required, `/^[a-z0-9\.\-]{3,120}$/` |
| dbPort | required int, 1–65535, default 3306 |
| dbUser | required, `/^[a-z0-9_]{3,80}$/` |
| dbPassRef | required, 1–200 — **this is an env-var name or vault ref, NOT a password**. Show help text: "Name of env var or vault path that holds the DB password. Never paste the password itself." |
| dbName | **readonly after create** — computed server-side as `{opShort}_{opId}_{tnShort}_{tnId}`. Show computed preview on form while user types shortName. |

**Actions:** create (triggers ProvisionTenantWorkflow; show PROVISIONING status until ACTIVE), edit, suspend, delete (soft).

**Provisioning polling:** after create, redirect to tenant detail; poll `/tenants/:id` every 3 s until `status !== 'PROVISIONING'`. If still PROVISIONING after 60 s, show info banner "Provisioning is taking longer than usual; check Sync Jobs".

### 7.3 Operator User (`/operator-users`, SYS_ADMIN only)

**List columns:** email, firstName + lastName, role, operatorId (blank for SYS_ADMIN), status, lastLoginAt.

**Create form fields:**

| Field | Validation |
|---|---|
| email | required, valid email, unique |
| password | required on create, ≥ 10 chars, ≥ 1 upper + 1 digit + 1 special (zod refine) |
| passwordConfirm | must equal password (form-level refine) |
| firstName | optional, ≤ 80 |
| lastName | optional, ≤ 80 |
| phone | optional, same regex as Operator |
| role | required: SYS_ADMIN / OPERATOR_ADMIN |
| operatorId | **required iff role=OPERATOR_ADMIN**; select from operators list |
| status | defaults to ACTIVE |

**Edit form:** password NOT editable here — separate "Reset password" action that opens a different dialog with password + confirm only.

**Actions:** create, reset password, suspend (POST `/status`), delete (soft).

### 7.4 Partner (`/tenants/:tenantId/partners`)

**List columns:** partnerName, partnerType, email, telephone, country, status, updatedAt.
**Filters:** status, partnerType, text search by name/email.

**Create/Edit form fields:**

| Field | Validation |
|---|---|
| partnerName | required, 2–120, unique per tenant |
| alternateNameInvoice | optional, ≤ 400 |
| alternateNameOther | optional, ≤ 400 |
| address1, address2 | optional, ≤ 100 each |
| city, state | optional, ≤ 45 |
| postalCode | optional, ≤ 45 |
| country | optional, ≤ 45 (ISO-2 hint) |
| telephone | optional, phone regex |
| email | optional, valid email |
| customerPrepaid | required bool, default true |
| partnerType | required select: DIRECT / RESELLER / SUB_RESELLER / AGENT |
| billingDate | optional int 1–31 |
| allowedDaysForInvoicePayment | optional int 1–120 |
| timezoneOffsetMinutes | optional int −720…+840 |
| callSrcId | optional int (was legacy `field1`) |
| defaultCurrency | required int, default 1 (ISO 4217 numeric hint) |
| invoiceAddress | optional, ≤ 200 |
| vatRegistrationNo | optional, ≤ 45 |
| paymentAdvice | optional, ≤ 1000, textarea |
| status | select ACTIVE / SUSPENDED |

**PartnerExtra panel** (child tab):

| Field | Validation |
|---|---|
| address1..4 | optional, ≤ 200 each |
| city | optional, ≤ 80 |
| state | optional, ≤ 80 |
| postalCode | optional, ≤ 40 |
| nid | optional, ≤ 40 |
| tradeLicense | optional, ≤ 80 |
| tin | optional, ≤ 40 |
| taxReturnDate | optional, valid date (YYYY-MM-DD) |
| countryCode | optional, ISO 3166-1 alpha-2 if provided (len 2) |

"Save" maps to `PUT /partners/{id}/extra` (upsert).

### 7.5 Auth User (`/tenants/:tenantId/users` or nested under partner)

**List columns:** email, firstName + lastName, partnerId (+ link to partner), userStatus, lastLoginAt, role count.
**Filters:** status, partnerId, text search by email/name.

**Create form fields:**

| Field | Validation |
|---|---|
| email | required, valid, unique per tenant |
| password | required on create, same strength policy as operator user |
| passwordConfirm | match |
| firstName / lastName | optional ≤ 80 |
| phone | optional, phone regex |
| userStatus | defaults to ACTIVE |
| resellerDbName | optional, ≤ 120 (legacy carry-over; show help "Used only for reseller-mode tenants") |
| pbxUuid | optional, UUID format if provided |
| partnerId | required — when opening from a partner detail, pre-filled & readonly; otherwise a select |

**Sub-tabs on User Detail:**

- **Roles**: pick from `GET /roles`, checkbox-list; "Save" calls `POST /users/{id}/roles` with the full replacement set.
- **IP Rules**: list (`ip`, `permissionType`), add-row (inline form with validation: `ip` must be IPv4 or CIDR `/^(\d{1,3}\.){3}\d{1,3}(\/\d{1,2})?$/`, `permissionType` ALLOW/DENY), delete per row.
- **Menu Permissions**: map-style table (menuKey ⇒ level). Add/edit uses upsert. Level select: NONE / READONLY / FULL.

**Actions:** create, edit, reset password, suspend (userStatus=SUSPENDED), delete (soft).

### 7.6 Auth Role (`/tenants/:tenantId/roles`)

**List columns:** name, description, permission count (derived), updatedAt.

**Create/Edit fields:**

| Field | Validation |
|---|---|
| name | required, 2–80, unique per tenant, `/^[a-z0-9_]+$/` (convention) |
| description | optional, ≤ 240 |

**Sub-tab Permissions:** pick from `GET /permissions`; "Save" calls `POST /roles/{id}/permissions` with the replacement set.

### 7.7 Auth Permission (`/tenants/:tenantId/permissions`)

**List columns:** name, description, createdAt.

**Create fields:**

| Field | Validation |
|---|---|
| name | required, 3–120, unique per tenant. **Suggest convention:** `domain:action:scope` (e.g., `partner:read:own`). Enforce regex `/^[a-z][a-z0-9_]*:[a-z][a-z0-9_]*(:[a-z][a-z0-9_]*)?$/` with a toggle "I know what I'm doing" to bypass. |
| description | optional, ≤ 240 |

Permission editing is intentionally omitted — permissions are immutable by name. Renames require delete + recreate.

### 7.8 Sync Jobs (`/tenants/:tenantId/sync-jobs`)

**Read-only audit view.**

**Columns:** id, entityType, entityId, operation, status (`<SyncStatusChip/>`), attempts, startedAt, finishedAt, error (truncated; click to expand).
**Filters:** status (multi-select), entityType (multi-select), date range.
**Actions per row:** none for SUCCESS. For FAILED: "Retry" (POST `/sync-jobs/{id}/retry` — backend endpoint TBD; if not available, disable the button and show tooltip).

**Global alert:** if > 10 FAILED in last hour, show red banner at top of all pages within that tenant's scope.

---

## 8. Async Sync-Job UX

Every mutating action against a tenant-scoped entity returns the primary data immediately (202-style body, though backend uses 200). The resulting `syncJob.status=PENDING` travels to the tenant DB asynchronously. Two UX rules:

1. **Don't block.** Saving a partner returns, dialog closes, list refreshes, the partner appears. Don't spin on the sync job — the master write is already committed.
2. **Do surface lag.** A small chip next to each row showing sync state:
   - `✓ synced` (green, default when last known job for that entity is SUCCESS)
   - `⋯ syncing` (amber, if latest job is PENDING or RUNNING)
   - `! sync failed` (red, if latest job is FAILED — click to jump to the sync job detail)

Query strategy: a single `GET /tenants/:id/sync-jobs?limit=100` poll every 15 s, cached in React Query; all list rows derive their chip from the most recent matching job by `(entityType, entityId)` in the cache. Don't hit per-row endpoints.

---

## 9. Environment Config

Follow the tenant-env pattern from `SOFTSWITCH_DASHBOARD`:

```ts
// src/configs/index.ts
const envs = { local, dev, prod } as const;
const env = (import.meta.env.VITE_PARTY_ENV ?? 'local') as keyof typeof envs;
export const config = envs[env];
```

Each env file:

```ts
// src/configs/local.ts
export default {
  apiBaseUrl: 'http://localhost:18081/api/v1',
  appName: 'Party Admin (local)',
  theme: 'light' as const,
  nginx: {
    serverName: 'party-admin.local.telcobright.test',
    listenPort: 7401,
    containerIp: '10.20.0.200',
  },
};
```

`.env.local.example` documents:

```
VITE_PARTY_ENV=local
VITE_PORT=7400
```

---

## 10. Styling — Token References

Adopt the CSS-custom-property + MUI-theme bridge:

```ts
// src/theme/muiTheme.ts
import { createTheme } from '@mui/material/styles';
import { cssVar } from './utils';

export const muiTheme = createTheme({
  palette: {
    primary: { main: cssVar('--color-primary') },
    success: { main: cssVar('--color-success') },
    warning: { main: cssVar('--color-warning') },
    error:   { main: cssVar('--color-danger') },
    text: {
      primary: cssVar('--color-text-primary'),
      secondary: cssVar('--color-text-secondary'),
    },
    background: {
      default: cssVar('--color-bg-app'),
      paper:   cssVar('--color-bg-surface'),
    },
  },
  typography: { fontFamily: 'var(--font-sans)' },
  shape: { borderRadius: 8 },
});
```

Token sources are the CSS files from `../routesphere-architect/campaign/docs/styling-guideline.md` §§1–3. Copy `tokens.css`, `theme-light.css`, `theme-dark.css` into `src/theme/` verbatim.

**Form padding rule (user preference):**

```css
.form-body {
  padding: var(--space-5) var(--space-6);  /* top/bottom, left/right */
}
.form-field + .form-field {
  margin-top: var(--space-3);
}
```

Fields should not stretch edge-to-edge in a dialog — use max 560 px width for the inner grid; center it.

---

## 11. API Integration Layer

`src/api/client.ts`:

```ts
import axios from 'axios';
import { config } from '../configs';
import { tokenStore } from '../auth/tokenStore';

export const api = axios.create({ baseURL: config.apiBaseUrl });

api.interceptors.request.use(cfg => {
  const t = tokenStore.accessToken;
  if (t) cfg.headers.Authorization = `Bearer ${t}`;
  return cfg;
});

let refreshing: Promise<string> | null = null;

api.interceptors.response.use(undefined, async err => {
  const { response, config } = err;
  if (response?.status === 401 && !config._retry) {
    config._retry = true;
    refreshing ??= refreshToken().finally(() => { refreshing = null; });
    try {
      const newToken = await refreshing;
      config.headers.Authorization = `Bearer ${newToken}`;
      return api(config);
    } catch {
      tokenStore.clear();
      window.location.href = `/login?next=${encodeURIComponent(location.pathname)}`;
    }
  }
  throw err;
});
```

Per-entity modules return typed promises using Zod schemas for response validation (fail-fast on contract drift):

```ts
// src/api/operators.ts
const Operator = z.object({
  id: z.number(),
  shortName: z.string(),
  ...
});
export type Operator = z.infer<typeof Operator>;

export const operatorsApi = {
  list: () => api.get('/operators').then(r => z.array(Operator).parse(r.data)),
  create: (body: OperatorCreate) => api.post('/operators', body).then(r => Operator.parse(r.data)),
  ...
};
```

---

## 12. React Query Conventions

- Query keys: `['operators']`, `['operators', id]`, `['tenants', { operatorId }]`, `['partners', { tenantId }]`, `['sync-jobs', { tenantId }]`.
- Mutations call `queryClient.invalidateQueries({ queryKey: [...] })` on success.
- Default `staleTime: 30_000` for list queries; `refetchOnWindowFocus: false`.
- Sync-jobs list uses `refetchInterval: 15_000` while any PENDING/RUNNING exists, otherwise `false`.

---

## 13. Testing Requirements

Minimum for first merge:

- **Unit:** each zod schema has a happy-path + one invalid-path test.
- **Component:** every FormDialog renders, validates, submits.
- **Integration:** one end-to-end vitest test per CRUD flow using MSW to mock the API.
- **Accessibility:** `@axe-core/react` report is clean at dialog + page levels (no violations).

Not required in the first PR: Playwright e2e, visual regression.

---

## 14. Dev & Deploy

**Dev:**

```bash
pnpm install
pnpm dev                 # vite dev on port 7400 (configurable via VITE_PORT)
```

**Build:**

```bash
pnpm build
pnpm preview
```

**Container:** Debian 12 LXC, port **7401** (internal), nginx fronting the built bundle. See `/home/mustafa/telcobright-projects/orchestrix/images/lxc` for the existing LXC build pattern; copy the portal-front template.

---

## 15. Do / Don't Checklist

**Do**
- Match the token system + component patterns in the styling guideline.
- Add left/right padding on forms so fields don't stretch ugly-wide.
- Keep forms vertically compact; one-column layout except for short paired fields (firstName + lastName on one row).
- Validate every field; make error messages specific ("must be 10+ characters and contain a digit").
- Confirm destructive actions (always).
- Show computed values (e.g., tenant `dbName` preview) inline in the form so users see what they'll get.
- Use react-hook-form's `setError` for server-side field errors returned in 400s.
- Invalidate the right query keys after mutations — never refetch the whole page.

**Don't**
- Don't use Tailwind or hardcoded hex colors.
- Don't put real DB passwords in UI — `dbPassRef` is a name/path only.
- Don't block the UI on sync jobs — master write is authoritative; projection is async by design.
- Don't auto-redirect on 403 — user gets confused. Show the error and let them navigate.
- Don't use `localStorage` for tokens — use `sessionStorage`.
- Don't write to master DB directly from the UI or via a "raw SQL" dev tool. All access goes through the REST API.
- Don't ship without filter/search on list pages that will routinely have > 50 rows (partners, users).

---

## 16. Deliverable Definition (for executing agent)

A new repo at `/home/mustafa/telcobright-projects/party-ui/` with:

- `pnpm install && pnpm build` succeeds.
- `pnpm test` passes.
- Against a running party-service (local Dev Services), the UI can: login as a fresh OPERATOR_ADMIN, create an operator, create a tenant, create a partner, create an auth user under that partner, create a role, assign the role to the user, add an IP rule, and watch all resulting sync-job rows land in the sync-jobs list.
- Before starting: confirm port with the user (default 7400 dev / 7401 container).
- On completion: update `party/README.md` with a "UI" section pointing at the new repo, and add a `party/docs/ui-status.md` tracking what landed and what was deferred.

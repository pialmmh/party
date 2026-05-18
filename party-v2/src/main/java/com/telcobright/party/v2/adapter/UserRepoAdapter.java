package com.telcobright.party.v2.adapter;

import java.util.List;
import java.util.Optional;

/**
 * One adapter instance per (tenant, repo type). Stateless from Party's point of view —
 * Party owns no persistence, every call is forwarded to the underlying system (Odoo,
 * Routesphere, LDAP, custom).
 */
public interface UserRepoAdapter {

    UserRepoType type();

    /**
     * Verify the password and return the user's profile on success.
     * Returns a deny result on bad credentials; throws AdapterException only for
     * communication / configuration errors (so policies can distinguish "wrong password"
     * from "adapter is broken").
     */
    AuthResult authenticate(String login, String password);

    /**
     * Look up by login (email / MSISDN / username). No password check.
     * Used by administrative flows, not by login.
     */
    Optional<UserProfile> findByLogin(String login);

    /**
     * Free-text search.
     */
    List<UserProfile> search(String query, int first, int max);

    /**
     * Cheap health check (ideally one round-trip). Used by the /v2/health/adapters endpoint.
     */
    HealthStatus checkHealth();

    /**
     * Human-readable identifier for what this adapter is talking to.
     * Surfaced in the Adapters UI page so operators can see "this tenant's odoo points at X".
     */
    String describeTarget();

    /**
     * Entity vocabulary discovered from the underlying repo. Adapters introspect their
     * source at startup and cache the result; the policy builder UI calls this via the
     * /v2/tenants/{tenantId}/entities endpoint to populate node handles.
     *
     * Default: empty list — stubs and adapters without introspection support return
     * nothing, which the UI renders as "vocabulary not available".
     */
    default List<EntityMeta> entities() {
        return List.of();
    }
}

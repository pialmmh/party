package com.telcobright.party.v2.providers;

import com.telcobright.party.v2.model.AuthResult;
import com.telcobright.party.v2.model.EntityMeta;
import com.telcobright.party.v2.model.HealthStatus;
import com.telcobright.party.v2.model.UserProfile;

import java.util.List;
import java.util.Optional;

/**
 * SPI contract for a backend user repository. One provider instance per
 * (tenant, repo type). Stateless from Party's point of view — Party owns no
 * persistence; every call forwards to the underlying system (Odoo,
 * Routesphere, LDAP, custom).
 *
 * Note: this is NOT on the auth path. Login policies (e.g.
 * {@code OdooLoginPolicy}) own their own wire conversation. The provider
 * shape exists for ancillary endpoints — entity vocabulary, health probes —
 * and as a contract for future operations that need a uniform read surface
 * across backends.
 */
public interface UserRepoProvider {

    UserRepoType type();

    /**
     * Verify the password and return the user's profile on success.
     */
    AuthResult authenticate(String login, String password);

    /**
     * Look up by login (email / MSISDN / username). No password check.
     */
    Optional<UserProfile> findByLogin(String login);

    /**
     * Free-text search.
     */
    List<UserProfile> search(String query, int first, int max);

    /**
     * Cheap health check (ideally one round-trip). Used by /v2/health/adapters.
     */
    HealthStatus checkHealth();

    /**
     * Human-readable identifier for what this provider is talking to.
     */
    String describeTarget();

    /**
     * Entity vocabulary discovered from the underlying repo. Providers
     * introspect their source at startup and cache the result; the policy
     * builder UI calls this via {@code /v2/tenants/{tenantId}/entities}.
     */
    default List<EntityMeta> entities() {
        return List.of();
    }
}

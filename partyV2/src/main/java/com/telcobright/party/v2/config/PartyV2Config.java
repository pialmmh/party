package com.telcobright.party.v2.config;

import com.telcobright.party.v2.providers.UserRepoType;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Root config for v2. Mapped from YAML keys under {@code party.v2.*}.
 *
 * Quarkus / SmallRye kebab-cases Java method names, so {@code userRepoType()}
 * maps to {@code user-repo-type} in YAML, {@code adminPassword()} maps to
 * {@code admin-password}, etc.
 *
 * Optional-typed fields tolerate both "missing entirely" and "empty string"
 * (SmallRye's default converter treats {@code ""} as null), which is what we
 * want when admin credentials are supplied via env vars that may be unset.
 */
@ConfigMapping(prefix = "party.v2")
public interface PartyV2Config {

    @WithDefault("t1")
    String defaultTenant();

    /**
     * One entry per tenant.  Key = tenantId.
     */
    Map<String, TenantConfig> tenants();

    /**
     * Per-endpoint policy chains. The map key is the logical endpoint name
     * (e.g. {@code basicLogin}); the value lists policy bean names to evaluate
     * in order, AND-style. Each name must match an {@code AuthPolicy} bean's
     * {@code name()}.
     */
    Map<String, EndpointPolicies> api();

    // ── nested ───────────────────────────────────────────────────────────

    interface TenantConfig {
        UserRepoType userRepoType();

        Optional<OdooAdapterConfig> odoo();
        Optional<LdapAdapterConfig> ldap();
        Optional<RoutesphereAdapterConfig> routesphere();
        Optional<CustomAdapterConfig> custom();
    }

    interface OdooAdapterConfig {
        String baseUrl();
        String db();

        Optional<String> adminUser();
        Optional<String> adminPassword();

        @WithDefault("5000")
        int timeoutMillis();

        /**
         * Odoo model names (e.g. "res.users", "res.partner") to introspect for
         * field metadata at startup. The adapter calls ir.model.fields.search_read
         * for each and caches the result for the policy builder UI.
         *
         * If empty, no introspection runs and the vocabulary endpoint returns nothing.
         * Introspection requires admin-user + admin-password to be set; otherwise the
         * adapter logs a warning and falls back to names-only entries.
         */
        @WithDefault("")
        List<String> entities();
    }

    interface LdapAdapterConfig {
        String url();
        Optional<String> baseDn();
    }

    interface RoutesphereAdapterConfig {
        String baseUrl();
        Optional<String> apiKey();
    }

    interface CustomAdapterConfig {
        String className();
        Map<String, String> properties();
    }

    interface EndpointPolicies {
        /**
         * Ordered list of {@code AuthPolicy} bean names. Each policy is composed
         * of one or more rules; the chain across policies and the rules within
         * a policy are both AND — first denial returns and ends evaluation.
         */
        List<String> policies();
    }
}

package com.telcobright.party.v2.config;

import com.telcobright.party.v2.providers.UserRepoProvider;
import com.telcobright.party.v2.providers.UserRepoType;
import com.telcobright.party.v2.providers.custom.CustomUserRepoProvider;
import com.telcobright.party.v2.providers.ldap.LdapUserRepoProvider;
import com.telcobright.party.v2.providers.odoo.OdooUserRepoProvider;
import com.telcobright.party.v2.providers.routesphere.RoutesphereUserRepoProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves per-tenant {@link UserRepoProvider}s from config. One process-wide
 * cache keyed by tenantId; entries built lazily on first use.
 *
 * Policy chains are NOT owned here — they live in
 * {@link com.telcobright.party.v2.policy.ApiChain}, keyed by endpoint name.
 * The tenant only contributes the provider that ancillary endpoints
 * (health, entities) talk to.
 */
@ApplicationScoped
public class TenantRegistry {

    @Inject
    PartyV2Config cfg;

    private final Map<String, UserRepoProvider> providerByTenant = new ConcurrentHashMap<>();

    public String defaultTenant() {
        return cfg.defaultTenant();
    }

    public Set<String> tenantIds() {
        return new HashSet<>(cfg.tenants().keySet());
    }

    public UserRepoProvider provider(String tenantId) {
        return providerByTenant.computeIfAbsent(tenantId, this::buildProvider);
    }

    private UserRepoProvider buildProvider(String tenantId) {
        PartyV2Config.TenantConfig tc = cfg.tenants().get(tenantId);
        if (tc == null) {
            throw new IllegalArgumentException("unknown tenant: " + tenantId);
        }
        UserRepoType type = tc.userRepoType();
        return switch (type) {
            case ODOO -> {
                PartyV2Config.OdooAdapterConfig oc = tc.odoo().orElseThrow(() ->
                        new IllegalStateException("tenant " + tenantId
                                + " has user-repo-type=odoo but no party.v2.tenants." + tenantId + ".odoo config"));
                yield new OdooUserRepoProvider(
                        oc.baseUrl(), oc.db(), oc.timeoutMillis(),
                        oc.adminUser().orElse(""), oc.adminPassword().orElse(""),
                        oc.entities());
            }
            case LDAP -> {
                PartyV2Config.LdapAdapterConfig lc = tc.ldap().orElse(null);
                yield new LdapUserRepoProvider(
                        lc == null ? null : lc.url(),
                        lc == null ? null : lc.baseDn().orElse(null));
            }
            case ROUTESPHERE -> {
                PartyV2Config.RoutesphereAdapterConfig rc = tc.routesphere().orElse(null);
                yield new RoutesphereUserRepoProvider(rc == null ? null : rc.baseUrl());
            }
            case CUSTOM -> {
                PartyV2Config.CustomAdapterConfig cc = tc.custom().orElse(null);
                yield new CustomUserRepoProvider(
                        cc == null ? null : cc.className(),
                        cc == null ? Map.of() : cc.properties());
            }
        };
    }
}

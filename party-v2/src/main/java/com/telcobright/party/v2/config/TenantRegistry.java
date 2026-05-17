package com.telcobright.party.v2.config;

import com.telcobright.party.v2.adapter.UserRepoAdapter;
import com.telcobright.party.v2.adapter.UserRepoType;
import com.telcobright.party.v2.adapter.odoo.OdooUserRepoAdapter;
import com.telcobright.party.v2.adapter.stub.CustomUserRepoAdapter;
import com.telcobright.party.v2.adapter.stub.LdapUserRepoAdapter;
import com.telcobright.party.v2.adapter.stub.RoutesphereUserRepoAdapter;
import com.telcobright.party.v2.policy.Policy;
import com.telcobright.party.v2.policy.PolicyChain;
import com.telcobright.party.v2.policy.basic.BasicAuthPolicy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves per-tenant adapters and policy chains from config. One process-wide cache
 * keyed by tenantId; entries built lazily on first use.
 */
@ApplicationScoped
public class TenantRegistry {

    @Inject
    PartyV2Config cfg;

    private final Map<String, UserRepoAdapter> adapterByTenant = new ConcurrentHashMap<>();
    private final Map<String, PolicyChain>     chainByTenant   = new ConcurrentHashMap<>();

    public String defaultTenant() {
        return cfg.defaultTenant();
    }

    public Set<String> tenantIds() {
        return new HashSet<>(cfg.tenants().keySet());
    }

    public UserRepoAdapter adapter(String tenantId) {
        return adapterByTenant.computeIfAbsent(tenantId, this::buildAdapter);
    }

    public PolicyChain chain(String tenantId) {
        return chainByTenant.computeIfAbsent(tenantId, t -> new PolicyChain(buildPolicies(), adapter(t)));
    }

    // ── internals ─────────────────────────────────────────────────────────

    private UserRepoAdapter buildAdapter(String tenantId) {
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
                yield new OdooUserRepoAdapter(
                        oc.baseUrl(), oc.db(), oc.timeoutMillis(),
                        oc.adminUser().orElse(""), oc.adminPassword().orElse(""));
            }
            case LDAP -> {
                PartyV2Config.LdapAdapterConfig lc = tc.ldap().orElse(null);
                yield new LdapUserRepoAdapter(
                        lc == null ? null : lc.url(),
                        lc == null ? null : lc.baseDn().orElse(null));
            }
            case ROUTESPHERE -> {
                PartyV2Config.RoutesphereAdapterConfig rc = tc.routesphere().orElse(null);
                yield new RoutesphereUserRepoAdapter(rc == null ? null : rc.baseUrl());
            }
            case CUSTOM -> {
                PartyV2Config.CustomAdapterConfig cc = tc.custom().orElse(null);
                yield new CustomUserRepoAdapter(
                        cc == null ? null : cc.className(),
                        cc == null ? Map.of() : cc.properties());
            }
        };
    }

    private List<Policy> buildPolicies() {
        return cfg.policies().chain().stream()
                .filter(PartyV2Config.PolicyEntry::enabled)
                .sorted(Comparator.comparingInt(PartyV2Config.PolicyEntry::order))
                .map(this::resolvePolicy)
                .toList();
    }

    private Policy resolvePolicy(PartyV2Config.PolicyEntry entry) {
        return switch (entry.name()) {
            case BasicAuthPolicy.NAME -> new BasicAuthPolicy();
            default -> throw new IllegalArgumentException("unknown policy in chain: " + entry.name());
        };
    }
}

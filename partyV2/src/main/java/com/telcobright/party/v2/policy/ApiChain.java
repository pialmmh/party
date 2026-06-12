package com.telcobright.party.v2.policy;

import com.telcobright.party.v2.config.PartyV2Config;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cisco-ACL-style policy runner. Each endpoint declares an ordered policy list
 * in YAML:
 *
 * <pre>{@code
 * party.v2.api:
 *   basicLogin:
 *     policies: [usernameAndPassword, nocReadOnly, timeBased, ipBased]
 * }</pre>
 *
 * Evaluation rule: first policy whose {@code execute()} returns
 * {@code matched=true} wins. Its allow/deny verdict is returned and the rest
 * of the chain is skipped. If nothing matches, the chain returns an implicit
 * deny ({@code denialCode=9001}). Policies that aren't applicable to a given
 * request must return {@link EvalResult#noMatch()} so the chain continues.
 *
 * Policy names are resolved against CDI's {@code Instance<AuthPolicy>} by each
 * policy's {@link AuthPolicy#name()}. The resolved per-endpoint chain is
 * cached after first use; unknown names fail fast at first lookup with the
 * list of registered policies.
 */
@ApplicationScoped
public class ApiChain {

    /** Denial code for the implicit "nothing matched" verdict. */
    public static final int IMPLICIT_DENY_CODE = 9001;

    @Inject
    PartyV2Config cfg;

    @Inject
    @Any
    Instance<AuthPolicy> allPolicies;

    private final Map<String, AuthPolicy> policiesByName = new HashMap<>();
    private final Map<String, List<AuthPolicy>> chainByEndpoint = new ConcurrentHashMap<>();

    @PostConstruct
    void index() {
        for (AuthPolicy p : allPolicies) {
            AuthPolicy prev = policiesByName.put(p.name(), p);
            if (prev != null) {
                throw new IllegalStateException(
                        "duplicate AuthPolicy beans for name '" + p.name() + "': "
                                + prev.getClass().getName() + " vs " + p.getClass().getName());
            }
        }
    }

    public EvalResult run(String endpoint, AuthContext ctx) {
        List<AuthPolicy> chain = chainByEndpoint.computeIfAbsent(endpoint, this::build);
        for (AuthPolicy p : chain) {
            EvalResult r = p.execute(ctx);
            if (r.matched) return r;     // Cisco ACL: first match wins, no further eval
        }
        EvalResult implicit = EvalResult.deny(IMPLICIT_DENY_CODE,
                "no policy matched; implicit deny");
        implicit.policyName = "<implicit>";
        return implicit;
    }

    public List<String> chainNames(String endpoint) {
        return chainByEndpoint.computeIfAbsent(endpoint, this::build).stream()
                .map(AuthPolicy::name)
                .toList();
    }

    private List<AuthPolicy> build(String endpoint) {
        PartyV2Config.EndpointPolicies ep = cfg.api().get(endpoint);
        if (ep == null) {
            throw new IllegalArgumentException(
                    "no party.v2.api." + endpoint + " block in config");
        }
        List<AuthPolicy> out = new ArrayList<>();
        for (String name : ep.policies()) {
            AuthPolicy p = policiesByName.get(name);
            if (p == null) {
                throw new IllegalStateException("endpoint '" + endpoint
                        + "' references unknown policy '" + name
                        + "' — known: " + policiesByName.keySet());
            }
            out.add(p);
        }
        return List.copyOf(out);
    }
}

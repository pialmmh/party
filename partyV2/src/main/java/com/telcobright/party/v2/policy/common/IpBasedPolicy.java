package com.telcobright.party.v2.policy.common;

import com.telcobright.party.v2.policy.AuthContext;
import com.telcobright.party.v2.policy.AuthPolicy;
import com.telcobright.party.v2.policy.EvalResult;
import com.telcobright.party.v2.policy.rule.IpAllowlistRule;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Network-origin gating. Pure Java {@code execute()}:
 *
 *   match condition : remote IP is not on the allowlist
 *   verdict on match: DENY (with rule's denial code/desc)
 *   otherwise        : noMatch — chain moves on
 *
 * Today {@link IpAllowlistRule} always allows (no list wired) so this policy
 * is effectively a no-op. Wire a per-policy params block to turn it on without
 * changing this code.
 */
@ApplicationScoped
public class IpBasedPolicy implements AuthPolicy {

    @Inject IpAllowlistRule ipAllowlistRule;

    @Override public String name() { return "ipBased"; }

    @Override
    public EvalResult execute(AuthContext ctx) {
        EvalResult r = ipAllowlistRule.execute(ctx);
        if (!r.allow) {
            r.policyName = name();
            return r;
        }
        return EvalResult.noMatch();
    }
}

package com.telcobright.party.v2.policy;

import com.telcobright.party.v2.adapter.UserRepoAdapter;

import java.util.List;

/**
 * Runs a fixed list of policies in order against a context. First REJECT short-circuits.
 * Per-tenant: the adapter is bound at construction; the policy list is shared across tenants.
 */
public final class PolicyChain {

    private final List<Policy> policies;
    private final UserRepoAdapter adapter;

    public PolicyChain(List<Policy> policies, UserRepoAdapter adapter) {
        this.policies = List.copyOf(policies);
        this.adapter = adapter;
    }

    public List<Policy> policies() { return policies; }

    public PolicyOutcome run(PolicyContext ctx) {
        for (Policy p : policies) {
            PolicyOutcome out = p.apply(ctx, adapter);
            if (out.rejected()) {
                ctx.denyReason = out.reason();
                return out;
            }
        }
        return PolicyOutcome.pass("chain");
    }
}

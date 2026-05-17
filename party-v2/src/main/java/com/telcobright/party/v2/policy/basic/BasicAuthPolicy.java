package com.telcobright.party.v2.policy.basic;

import com.telcobright.party.v2.adapter.AdapterException;
import com.telcobright.party.v2.adapter.AuthResult;
import com.telcobright.party.v2.adapter.UserRepoAdapter;
import com.telcobright.party.v2.policy.Policy;
import com.telcobright.party.v2.policy.PolicyContext;
import com.telcobright.party.v2.policy.PolicyOutcome;

/**
 * The default policy in every chain. On {@code authenticate} actions, calls the tenant's
 * adapter and either passes (attaching the user profile to the context) or rejects with
 * a clear reason. For non-authenticate actions, passes through.
 */
public final class BasicAuthPolicy implements Policy {

    public static final String NAME = "basic-auth";

    @Override public String name() { return NAME; }

    @Override
    public PolicyOutcome apply(PolicyContext ctx, UserRepoAdapter adapter) {
        if (!"authenticate".equals(ctx.action)) {
            return PolicyOutcome.pass(NAME);
        }
        if (ctx.login == null || ctx.login.isBlank()
                || ctx.password == null || ctx.password.isEmpty()) {
            return PolicyOutcome.reject(NAME, "missing credentials");
        }
        try {
            AuthResult r = adapter.authenticate(ctx.login, ctx.password);
            if (!r.valid()) {
                return PolicyOutcome.reject(NAME, r.reason() == null ? "denied" : r.reason());
            }
            r.profile().ifPresent(p -> ctx.resolvedUser = p);
            return PolicyOutcome.pass(NAME);
        } catch (AdapterException ae) {
            return PolicyOutcome.reject(NAME, "adapter error: " + ae.getMessage());
        }
    }
}

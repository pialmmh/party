package com.telcobright.party.v2.policy.common;

import com.telcobright.party.v2.policy.AuthContext;
import com.telcobright.party.v2.policy.AuthPolicy;
import com.telcobright.party.v2.policy.EvalResult;
import com.telcobright.party.v2.policy.rule.HasNocRoleRule;
import com.telcobright.party.v2.policy.rule.IsWriteActionRule;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * NOC users are read-only. Pure Java {@code execute()}:
 *
 *   match condition : user has NOC role AND action is a write
 *   verdict on match: DENY (NOC is read-only)
 *   otherwise        : noMatch — chain moves on
 *
 * Example of AND across two rule predicates, hand-written.
 */
@ApplicationScoped
public class NocReadOnlyPolicy implements AuthPolicy {

    @Inject HasNocRoleRule    hasNocRoleRule;
    @Inject IsWriteActionRule isWriteActionRule;

    @Override public String name() { return "nocReadOnly"; }

    @Override
    public EvalResult execute(AuthContext ctx) {
        if (ctx.user == null) return EvalResult.noMatch();

        boolean isNoc   = hasNocRoleRule.execute(ctx).allow;
        boolean isWrite = isWriteActionRule.execute(ctx).allow;

        if (isNoc && isWrite) {
            EvalResult deny = EvalResult.deny(1400, "NOC role is read-only");
            deny.policyName = name();
            return deny;
        }
        return EvalResult.noMatch();
    }
}

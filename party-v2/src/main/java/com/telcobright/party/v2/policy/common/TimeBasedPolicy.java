package com.telcobright.party.v2.policy.common;

import com.telcobright.party.v2.policy.AuthContext;
import com.telcobright.party.v2.policy.AuthPolicy;
import com.telcobright.party.v2.policy.EvalResult;
import com.telcobright.party.v2.policy.rule.TimeWindowRule;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Time-of-day gating. Pure Java {@code execute()}:
 *
 *   match condition : request falls outside the allowed window
 *   verdict on match: DENY (with rule's denial code/desc)
 *   otherwise        : noMatch — chain moves on
 *
 * Today {@link TimeWindowRule} always allows (no window wired) so this policy
 * is effectively a no-op. Wire a per-policy params block to the rule to turn
 * the gate on without changing this code.
 */
@ApplicationScoped
public class TimeBasedPolicy implements AuthPolicy {

    @Inject TimeWindowRule timeWindowRule;

    @Override public String name() { return "timeBased"; }

    @Override
    public EvalResult execute(AuthContext ctx) {
        EvalResult r = timeWindowRule.execute(ctx);
        if (!r.allow) {
            r.policyName = name();
            return r;
        }
        return EvalResult.noMatch();
    }
}

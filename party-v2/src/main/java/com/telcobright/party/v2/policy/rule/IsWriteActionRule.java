package com.telcobright.party.v2.policy.rule;

import com.telcobright.party.v2.policy.AuthContext;
import com.telcobright.party.v2.policy.EvalResult;
import com.telcobright.party.v2.policy.Rule;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Predicate: is {@link AuthContext#action} a write action? Whitelist of
 * recognised write verbs — {@code create}, {@code update}, {@code delete},
 * {@code write}. Anything else (including null) is a non-write.
 */
@ApplicationScoped
public class IsWriteActionRule implements Rule<AuthContext> {

    @Override public String name() { return "isWriteAction"; }

    @Override
    public EvalResult execute(AuthContext ctx) {
        if (ctx.action == null) return EvalResult.deny(1600, "no action on context");
        boolean isWrite = switch (ctx.action) {
            case "create", "update", "delete", "write" -> true;
            default -> false;
        };
        return isWrite ? EvalResult.allow() : EvalResult.deny(1601, "not a write action");
    }
}

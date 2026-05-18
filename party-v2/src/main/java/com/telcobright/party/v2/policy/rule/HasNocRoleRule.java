package com.telcobright.party.v2.policy.rule;

import com.telcobright.party.v2.model.Role;
import com.telcobright.party.v2.policy.AuthContext;
import com.telcobright.party.v2.policy.EvalResult;
import com.telcobright.party.v2.policy.Rule;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Predicate: does the resolved user carry the NOC role?
 *
 *   allow  -> yes (case-insensitive name match)
 *   deny   -> no, or no user has been resolved yet
 *
 * Used by {@link com.telcobright.party.v2.policy.common.NocReadOnlyPolicy}.
 */
@ApplicationScoped
public class HasNocRoleRule implements Rule<AuthContext> {

    private static final String NOC_ROLE = "NOC";

    @Override public String name() { return "hasNocRole"; }

    @Override
    public EvalResult execute(AuthContext ctx) {
        if (ctx.user == null) return EvalResult.deny(1500, "no resolved user");
        boolean has = ctx.user.roles().stream()
                .map(Role::name)
                .anyMatch(NOC_ROLE::equalsIgnoreCase);
        return has ? EvalResult.allow() : EvalResult.deny(1501, "user lacks NOC role");
    }
}

package com.telcobright.party.v2.policy.rule;

import com.telcobright.party.v2.policy.AuthContext;
import com.telcobright.party.v2.policy.EvalResult;
import com.telcobright.party.v2.policy.Rule;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Rejects logins for users flagged inactive in the upstream user repo. Requires
 * a resolved user — meant to run after {@link UsernameAndPasswordRule}.
 *
 * Denial codes:
 *   1100 - no resolved user (rule placed before credential rule, misconfig)
 *   1101 - account inactive
 */
@ApplicationScoped
public class AccountActiveRule implements Rule<AuthContext> {

    @Override public String name() { return "accountActive"; }

    @Override
    public EvalResult execute(AuthContext ctx) {
        if (ctx.user == null) {
            return EvalResult.deny(1100, "accountActive rule has no resolved user");
        }
        if (!ctx.user.active()) {
            return EvalResult.deny(1101, "account inactive");
        }
        return EvalResult.allow();
    }
}

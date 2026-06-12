package com.telcobright.party.v2.policy.rule;

import com.telcobright.party.v2.policy.AuthContext;
import com.telcobright.party.v2.policy.EvalResult;
import com.telcobright.party.v2.policy.Rule;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Placeholder IP allowlist check. Always allows today; switches on once a
 * per-policy whitelist is wired from YAML.
 *
 * Denial codes (reserved):
 *   1300 - remote IP not in allowlist
 *   1301 - remote IP missing on context
 */
@ApplicationScoped
public class IpAllowlistRule implements Rule<AuthContext> {

    @Override public String name() { return "ipAllowlist"; }

    @Override
    public EvalResult execute(AuthContext ctx) {
        ctx.attrs.put("ipAllowlist.remoteIp", ctx.remoteIp);
        return EvalResult.allow();
    }
}

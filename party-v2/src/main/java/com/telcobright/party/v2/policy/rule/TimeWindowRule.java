package com.telcobright.party.v2.policy.rule;

import com.telcobright.party.v2.policy.AuthContext;
import com.telcobright.party.v2.policy.EvalResult;
import com.telcobright.party.v2.policy.Rule;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Placeholder time-of-day check. Today it always allows — the hook is in place
 * so the tenant-editable variant can flip to a real window check once the YAML
 * carries {@code time-window} params per policy.
 *
 * Denial codes (reserved):
 *   1200 - outside permitted hours
 */
@ApplicationScoped
public class TimeWindowRule implements Rule<AuthContext> {

    @Override public String name() { return "timeWindow"; }

    @Override
    public EvalResult execute(AuthContext ctx) {
        // No config bound yet — pass through. Wire to a per-policy params struct later.
        LocalTime t = LocalTime.ofInstant(ctx.requestedAt, ZoneId.systemDefault());
        ctx.attrs.put("timeWindow.hour", t.getHour());
        return EvalResult.allow();
    }
}

package com.telcobright.party.v2.policy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Convenience builder for an "always-matches, AND-of-rules" policy. Useful for
 * the trailing default-allow policy in a chain, or for quick test setups. Most
 * production policies will be hand-written {@link Policy} implementations
 * combining rules with AND/OR — the builder is optional, not required.
 *
 * Semantics of {@link #build()}'s {@code execute}:
 *   - runs each rule in order
 *   - any rule with {@code allow=false} short-circuits and propagates as the
 *     policy's deny verdict
 *   - all rules pass -> matched=true, allow=true
 * The built policy NEVER returns {@code noMatch} — it always matches.
 */
public final class PolicyBuilder<T> {

    private String name;
    private final List<Rule<T>> rules = new ArrayList<>();

    private PolicyBuilder() {}

    public static <T> PolicyBuilder<T> builder() { return new PolicyBuilder<>(); }

    public PolicyBuilder<T> name(String n) { this.name = n; return this; }

    public PolicyBuilder<T> rule(Rule<T> r) {
        if (r == null) throw new IllegalArgumentException("rule must not be null");
        this.rules.add(r);
        return this;
    }

    @SafeVarargs
    public final PolicyBuilder<T> rules(Rule<T>... rs) {
        Collections.addAll(this.rules, rs);
        return this;
    }

    public Policy<T> build() {
        if (name == null || name.isBlank()) {
            throw new IllegalStateException("policy must have a non-blank name");
        }
        final String n = name;
        final List<Rule<T>> rs = List.copyOf(rules);
        return new Policy<T>() {
            @Override public String name() { return n; }

            @Override
            public EvalResult execute(T input) {
                for (Rule<T> r : rs) {
                    EvalResult res = r.execute(input);
                    if (res.ruleName == null) res.ruleName = r.name();
                    res.policyName = n;
                    if (!res.allow) return res;
                }
                EvalResult ok = EvalResult.allow();
                ok.policyName = n;
                return ok;
            }
        };
    }
}

package com.telcobright.party.v2.policy;

/**
 * Outcome of one {@link Rule#execute} or {@link Policy#execute} call.
 *
 * Tri-state via two booleans:
 *   matched=false              -> the policy did not apply to this request
 *                                 (Cisco-ACL: the chain moves on to the next policy)
 *   matched=true, allow=true   -> permit; chain returns this as the verdict
 *   matched=true, allow=false  -> deny;   chain returns this as the verdict
 *
 * Rules typically return {@code matched=true} always and use {@code allow} as
 * a plain boolean predicate. {@code matched=false} is reserved for policies
 * declaring "I do not apply — skip me."
 *
 * Plain class with public fields by design — subclasses can extend with extra
 * fields (matched scope, debug trace, etc.) without breaking the wire shape.
 */
public class EvalResult {

    public boolean matched;           // false = skip me, chain continues
    public boolean allow;
    public int denialCode;            // 0 = N/A
    public String denialDescription;
    public String ruleName;           // who emitted this result
    public String policyName;         // which policy the rule lives in

    public EvalResult() {}

    /** Policy/rule does not apply — Cisco-ACL chain moves on. */
    public static EvalResult noMatch() {
        EvalResult r = new EvalResult();
        r.matched = false;
        return r;
    }

    public static EvalResult allow() {
        EvalResult r = new EvalResult();
        r.matched = true;
        r.allow = true;
        return r;
    }

    public static EvalResult deny(int code, String description) {
        EvalResult r = new EvalResult();
        r.matched = true;
        r.allow = false;
        r.denialCode = code;
        r.denialDescription = description;
        return r;
    }
}

package com.telcobright.party.v2.policy;

public record PolicyOutcome(Decision decision, String reason, String policyName) {

    public enum Decision { PASS, REJECT }

    public static PolicyOutcome pass(String policyName) {
        return new PolicyOutcome(Decision.PASS, null, policyName);
    }

    public static PolicyOutcome reject(String policyName, String reason) {
        return new PolicyOutcome(Decision.REJECT, reason, policyName);
    }

    public boolean rejected() { return decision == Decision.REJECT; }
}

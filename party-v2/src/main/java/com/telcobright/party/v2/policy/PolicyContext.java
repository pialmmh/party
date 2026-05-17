package com.telcobright.party.v2.policy;

import com.telcobright.party.v2.adapter.UserProfile;

import java.util.HashMap;
import java.util.Map;

/**
 * Carries per-request state through the policy chain. Mutable on purpose:
 * upstream policies attach data (e.g. the resolved user) that downstream
 * policies read.
 */
public final class PolicyContext {

    public final String tenantId;
    public final String action;        // e.g. "authenticate"
    public final String login;
    public final String password;      // nullable for non-auth actions
    public final Map<String, Object> attrs = new HashMap<>();

    public UserProfile resolvedUser;   // set by BasicAuthPolicy on success
    public String denyReason;          // set when a policy rejects

    public PolicyContext(String tenantId, String action, String login, String password) {
        this.tenantId = tenantId;
        this.action = action;
        this.login = login;
        this.password = password;
    }
}

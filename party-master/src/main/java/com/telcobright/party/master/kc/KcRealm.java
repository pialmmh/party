package com.telcobright.party.master.kc;

/**
 * Decoded realm name.
 *
 * Realm naming convention:
 *   - "party-operators"                 → operator_user (master-level admin users)
 *   - "tenant-{opShort}-{tnShort}"      → auth_user scoped to (operator_id, tenant_id)
 */
public record KcRealm(Kind kind, Long operatorId, Long tenantId) {

    public enum Kind { OPERATORS, TENANT }

    public boolean isOperators() { return kind == Kind.OPERATORS; }
    public boolean isTenant()    { return kind == Kind.TENANT; }
}

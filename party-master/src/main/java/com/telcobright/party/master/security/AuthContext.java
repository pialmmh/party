package com.telcobright.party.master.security;

import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class AuthContext {
    public Long subjectId;
    public String email;
    public String scope; // SYS_ADMIN / OPERATOR_ADMIN / TENANT_USER
    public Long operatorId;
    public Long tenantId;
    public Long partnerId;

    public boolean isSysAdmin() { return "SYS_ADMIN".equals(scope); }
    public boolean isOperatorAdmin() { return "OPERATOR_ADMIN".equals(scope); }
    public boolean canManageOperator(Long operatorId) {
        return isSysAdmin() || (isOperatorAdmin() && operatorId != null && operatorId.equals(this.operatorId));
    }
}

package com.telcobright.party.master.kc;

import com.telcobright.party.domain.UserStatus;
import com.telcobright.party.master.entity.*;
import com.telcobright.party.master.security.PasswordHasher;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.*;

@ApplicationScoped
public class KcUserLookupService {

    private static final String PREFIX_OP = "op-";
    private static final String PREFIX_TU = "tu-";

    @Inject RealmDecoder realmDecoder;
    @Inject PasswordHasher hasher;

    public Optional<KcUserView> byUsername(String realmName, String username) {
        KcRealm r = realmDecoder.decode(realmName);
        return (r.isOperators()) ? operatorByEmail(username) : tenantUserByEmail(r, username);
    }

    public Optional<KcUserView> byId(String realmName, String id) {
        KcRealm r = realmDecoder.decode(realmName);
        if (r.isOperators() && id.startsWith(PREFIX_OP)) {
            return operatorById(Long.parseLong(id.substring(PREFIX_OP.length())));
        }
        if (r.isTenant() && id.startsWith(PREFIX_TU)) {
            return tenantUserById(r, Long.parseLong(id.substring(PREFIX_TU.length())));
        }
        return Optional.empty();
    }

    public List<KcUserView> search(String realmName, String query, int first, int max) {
        KcRealm r = realmDecoder.decode(realmName);
        String q = (query == null || query.isBlank()) ? "%" : "%" + query.toLowerCase() + "%";
        if (r.isOperators()) {
            return OperatorUser.<OperatorUser>find(
                            "LOWER(email) LIKE ?1 ORDER BY id", q)
                    .range(first, first + Math.max(1, max) - 1)
                    .list()
                    .stream().map(this::asViewOp).toList();
        }
        return AuthUser.<AuthUser>find(
                        "tenantId = ?1 AND LOWER(email) LIKE ?2 ORDER BY id", r.tenantId(), q)
                .range(first, first + Math.max(1, max) - 1)
                .list()
                .stream().map(u -> asViewTenant(u, r)).toList();
    }

    @Transactional
    public boolean validateCredentials(String realmName, String username, String password) {
        KcRealm r = realmDecoder.decode(realmName);
        if (r.isOperators()) {
            OperatorUser u = OperatorUser.find("email", username).firstResult();
            if (u == null || u.status != UserStatus.ACTIVE) return false;
            boolean ok = hasher.verify(password, u.passwordHash);
            if (ok) u.lastLoginAt = Instant.now();
            return ok;
        }
        AuthUser u = AuthUser.find("tenantId = ?1 and email = ?2", r.tenantId(), username).firstResult();
        if (u == null || u.userStatus != UserStatus.ACTIVE) return false;
        boolean ok = hasher.verify(password, u.passwordHash);
        if (ok) u.lastLoginAt = Instant.now();
        return ok;
    }

    // ---------- operator_user ----------

    private Optional<KcUserView> operatorByEmail(String email) {
        OperatorUser u = OperatorUser.find("email", email).firstResult();
        return Optional.ofNullable(u).map(this::asViewOp);
    }

    private Optional<KcUserView> operatorById(Long id) {
        OperatorUser u = OperatorUser.findById(id);
        return Optional.ofNullable(u).map(this::asViewOp);
    }

    private KcUserView asViewOp(OperatorUser u) {
        Map<String, List<String>> attrs = new LinkedHashMap<>();
        attrs.put("scope", List.of(u.role.name()));
        if (u.operatorId != null) attrs.put("operatorId", List.of(String.valueOf(u.operatorId)));
        attrs.put("roles", List.of(u.role.name()));
        return new KcUserView(
                PREFIX_OP + u.id, u.email, u.email, u.firstName, u.lastName,
                u.status == UserStatus.ACTIVE, true, attrs);
    }

    // ---------- auth_user (tenant-scoped) ----------

    private Optional<KcUserView> tenantUserByEmail(KcRealm r, String email) {
        AuthUser u = AuthUser.find("tenantId = ?1 and email = ?2", r.tenantId(), email).firstResult();
        return Optional.ofNullable(u).map(x -> asViewTenant(x, r));
    }

    private Optional<KcUserView> tenantUserById(KcRealm r, Long id) {
        AuthUser u = AuthUser.find("tenantId = ?1 and id = ?2", r.tenantId(), id).firstResult();
        return Optional.ofNullable(u).map(x -> asViewTenant(x, r));
    }

    private KcUserView asViewTenant(AuthUser u, KcRealm r) {
        List<String> roles = AuthUserRole.<AuthUserRole>list("userId", u.id).stream()
                .map(link -> {
                    AuthRole role = AuthRole.findById(link.roleId);
                    return role != null ? role.name : null;
                })
                .filter(Objects::nonNull)
                .toList();

        List<Long> roleIds = AuthUserRole.<AuthUserRole>list("userId", u.id).stream()
                .map(link -> link.roleId).toList();

        List<String> permissions = roleIds.isEmpty() ? List.of() :
                AuthRolePermission.<AuthRolePermission>find(
                                "roleId in ?1", roleIds).stream()
                        .map(link -> {
                            AuthPermission p = AuthPermission.findById(link.permissionId);
                            return p != null ? p.name : null;
                        })
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();

        Map<String, List<String>> attrs = new LinkedHashMap<>();
        attrs.put("scope", List.of("TENANT_USER"));
        attrs.put("operatorId", List.of(String.valueOf(r.operatorId())));
        attrs.put("tenantId", List.of(String.valueOf(r.tenantId())));
        attrs.put("partnerId", List.of(String.valueOf(u.partnerId)));
        if (!roles.isEmpty()) attrs.put("roles", roles);
        if (!permissions.isEmpty()) attrs.put("permissions", permissions);

        return new KcUserView(
                PREFIX_TU + u.id, u.email, u.email, u.firstName, u.lastName,
                u.userStatus == UserStatus.ACTIVE, true, attrs);
    }
}

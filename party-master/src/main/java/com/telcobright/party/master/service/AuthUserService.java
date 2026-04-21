package com.telcobright.party.master.service;

import com.telcobright.party.domain.EntityType;
import com.telcobright.party.domain.SyncOperation;
import com.telcobright.party.domain.UserStatus;
import com.telcobright.party.master.entity.AuthUser;
import com.telcobright.party.master.entity.AuthUserRole;
import com.telcobright.party.master.entity.TenantSyncJob;
import com.telcobright.party.master.security.PasswordHasher;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.util.List;

@ApplicationScoped
public class AuthUserService {

    @Inject PasswordHasher hasher;
    @Inject TenantSyncJobService syncJobs;
    @Inject SyncDispatcher dispatcher;

    public List<AuthUser> listByTenant(Long tenantId) {
        return AuthUser.list("tenantId", tenantId);
    }

    public List<AuthUser> listByPartner(Long tenantId, Long partnerId) {
        return AuthUser.list("tenantId = ?1 and partnerId = ?2", tenantId, partnerId);
    }

    public AuthUser findById(Long tenantId, Long id) {
        AuthUser u = AuthUser.find("tenantId = ?1 and id = ?2", tenantId, id).firstResult();
        if (u == null) throw new NotFoundException("auth user " + id + " not found in tenant " + tenantId);
        return u;
    }

    @Transactional
    public AuthUser create(Long tenantId, Long partnerId, String email, String password, AuthUser details) {
        if (AuthUser.find("tenantId = ?1 and email = ?2", tenantId, email).firstResult() != null) {
            throw new BadRequestException("email already used in tenant");
        }
        AuthUser u = new AuthUser();
        u.tenantId = tenantId;
        u.partnerId = partnerId;
        u.email = email;
        u.passwordHash = hasher.hash(password);
        u.firstName = details.firstName;
        u.lastName = details.lastName;
        u.phone = details.phone;
        u.userStatus = details.userStatus != null ? details.userStatus : UserStatus.ACTIVE;
        u.resellerDbName = details.resellerDbName;
        u.pbxUuid = details.pbxUuid;
        u.persist();
        TenantSyncJob job = syncJobs.enqueue(tenantId, EntityType.AUTH_USER, String.valueOf(u.id), SyncOperation.CREATE);
        dispatcher.dispatch(job);
        return u;
    }

    @Transactional
    public AuthUser update(Long tenantId, Long id, AuthUser patch) {
        AuthUser u = findById(tenantId, id);
        if (patch.firstName != null) u.firstName = patch.firstName;
        if (patch.lastName != null) u.lastName = patch.lastName;
        if (patch.phone != null) u.phone = patch.phone;
        if (patch.userStatus != null) u.userStatus = patch.userStatus;
        if (patch.resellerDbName != null) u.resellerDbName = patch.resellerDbName;
        if (patch.pbxUuid != null) u.pbxUuid = patch.pbxUuid;
        TenantSyncJob job = syncJobs.enqueue(tenantId, EntityType.AUTH_USER, String.valueOf(u.id), SyncOperation.UPDATE);
        dispatcher.dispatch(job);
        return u;
    }

    @Transactional
    public void resetPassword(Long tenantId, Long id, String newPassword) {
        AuthUser u = findById(tenantId, id);
        u.passwordHash = hasher.hash(newPassword);
        TenantSyncJob job = syncJobs.enqueue(tenantId, EntityType.AUTH_USER, String.valueOf(u.id), SyncOperation.UPDATE);
        dispatcher.dispatch(job);
    }

    @Transactional
    public void replaceRoles(Long tenantId, Long userId, List<Long> roleIds) {
        AuthUser u = findById(tenantId, userId);
        AuthUserRole.delete("userId", u.id);
        for (Long rid : roleIds) {
            AuthUserRole link = new AuthUserRole();
            link.userId = u.id;
            link.roleId = rid;
            link.tenantId = tenantId;
            link.persist();
        }
        TenantSyncJob job = syncJobs.enqueue(tenantId, EntityType.AUTH_USER_ROLE, String.valueOf(u.id), SyncOperation.UPDATE);
        dispatcher.dispatch(job);
    }

    @Transactional
    public void delete(Long tenantId, Long id) {
        AuthUser u = findById(tenantId, id);
        u.userStatus = UserStatus.DELETED;
        TenantSyncJob job = syncJobs.enqueue(tenantId, EntityType.AUTH_USER, String.valueOf(u.id), SyncOperation.DELETE);
        dispatcher.dispatch(job);
    }
}

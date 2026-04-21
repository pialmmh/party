package com.telcobright.party.tenant;

import com.telcobright.party.domain.EntityType;
import com.telcobright.party.domain.SyncOperation;
import com.telcobright.party.master.entity.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;

/**
 * Applies a master-row change to the per-tenant DB. Uses INSERT ... ON DUPLICATE KEY UPDATE
 * (for CREATE/UPDATE) and DELETE (for DELETE) so the activity is safely replayable.
 *
 * Phase 4+: only PARTNER and AUTH_USER are fully implemented; others are logged-only
 * placeholders until Phase 5+ wires them.
 */
@ApplicationScoped
public class TenantProjectionWriter {

    private static final Logger LOG = Logger.getLogger(TenantProjectionWriter.class);

    @Inject TenantDataSourceRegistry dataSources;

    public void apply(Long tenantId, EntityType type, SyncOperation op, String entityId) {
        DataSource ds = dataSources.get(tenantId);
        try (Connection c = ds.getConnection()) {
            switch (type) {
                case PARTNER    -> applyPartner(c, tenantId, op, entityId);
                case AUTH_USER  -> applyAuthUser(c, tenantId, op, entityId);
                case AUTH_ROLE  -> applyAuthRole(c, tenantId, op, entityId);
                case AUTH_PERMISSION -> applyAuthPermission(c, tenantId, op, entityId);
                default         -> LOG.warnf("projection for %s not yet implemented (tenant=%d op=%s id=%s)",
                                             type, tenantId, op, entityId);
            }
        } catch (Exception e) {
            throw new RuntimeException("projection write failed: tenant=" + tenantId + " type=" + type + " id=" + entityId, e);
        }
    }

    private void applyPartner(Connection c, Long tenantId, SyncOperation op, String entityId) throws Exception {
        long id = Long.parseLong(entityId);
        if (op == SyncOperation.DELETE) {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM partner WHERE id = ?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
            return;
        }
        Partner p = Partner.findById(id);
        if (p == null) {
            LOG.warnf("partner %d not found in master — skipping projection", id);
            return;
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO partner (id, partner_name, email, telephone, partner_type, status, default_currency, customer_prepaid) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE partner_name=VALUES(partner_name), email=VALUES(email), telephone=VALUES(telephone), " +
                "partner_type=VALUES(partner_type), status=VALUES(status), default_currency=VALUES(default_currency), " +
                "customer_prepaid=VALUES(customer_prepaid)")) {
            ps.setLong(1, p.id);
            ps.setString(2, p.partnerName);
            ps.setString(3, p.email);
            ps.setString(4, p.telephone);
            ps.setString(5, p.partnerType);
            ps.setString(6, p.status);
            ps.setInt(7, p.defaultCurrency);
            ps.setBoolean(8, p.customerPrepaid != null && p.customerPrepaid);
            ps.executeUpdate();
        }
    }

    private void applyAuthUser(Connection c, Long tenantId, SyncOperation op, String entityId) throws Exception {
        long id = Long.parseLong(entityId);
        if (op == SyncOperation.DELETE) {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM auth_user WHERE id = ?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
            return;
        }
        AuthUser u = AuthUser.findById(id);
        if (u == null) return;
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO auth_user (id, partner_id, email, password_hash, first_name, last_name, phone, user_status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE partner_id=VALUES(partner_id), email=VALUES(email), password_hash=VALUES(password_hash), " +
                "first_name=VALUES(first_name), last_name=VALUES(last_name), phone=VALUES(phone), user_status=VALUES(user_status)")) {
            ps.setLong(1, u.id);
            ps.setLong(2, u.partnerId);
            ps.setString(3, u.email);
            ps.setString(4, u.passwordHash);
            ps.setString(5, u.firstName);
            ps.setString(6, u.lastName);
            ps.setString(7, u.phone);
            ps.setString(8, u.userStatus == null ? "ACTIVE" : u.userStatus.name());
            ps.executeUpdate();
        }
    }

    private void applyAuthRole(Connection c, Long tenantId, SyncOperation op, String entityId) throws Exception {
        long id = Long.parseLong(entityId);
        if (op == SyncOperation.DELETE) {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM auth_role WHERE id = ?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
            return;
        }
        AuthRole r = AuthRole.findById(id);
        if (r == null) return;
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO auth_role (id, name, description) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE name=VALUES(name), description=VALUES(description)")) {
            ps.setLong(1, r.id);
            ps.setString(2, r.name);
            ps.setString(3, r.description);
            ps.executeUpdate();
        }
    }

    private void applyAuthPermission(Connection c, Long tenantId, SyncOperation op, String entityId) throws Exception {
        long id = Long.parseLong(entityId);
        if (op == SyncOperation.DELETE) {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM auth_permission WHERE id = ?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
            return;
        }
        AuthPermission p = AuthPermission.findById(id);
        if (p == null) return;
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO auth_permission (id, name, description) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE name=VALUES(name), description=VALUES(description)")) {
            ps.setLong(1, p.id);
            ps.setString(2, p.name);
            ps.setString(3, p.description);
            ps.executeUpdate();
        }
    }
}

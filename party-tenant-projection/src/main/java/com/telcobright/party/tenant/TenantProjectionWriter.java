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
import java.util.List;

/**
 * Applies a master-row change to the per-tenant DB. Uses INSERT ... ON DUPLICATE KEY UPDATE
 * (for CREATE/UPDATE) and DELETE (for DELETE) so the activity is safely replayable.
 *
 * Services read from the local per-tenant DB for authorization decisions, so every
 * tenant-scoped entity must have a projection here.
 */
@ApplicationScoped
public class TenantProjectionWriter {

    private static final Logger LOG = Logger.getLogger(TenantProjectionWriter.class);

    @Inject TenantDataSourceRegistry dataSources;

    public void apply(Long tenantId, EntityType type, SyncOperation op, String entityId) {
        DataSource ds = dataSources.get(tenantId);
        try (Connection c = ds.getConnection()) {
            switch (type) {
                case PARTNER               -> applyPartner(c, tenantId, op, entityId);
                case PARTNER_EXTRA         -> applyPartnerExtra(c, tenantId, op, entityId);
                case AUTH_USER             -> applyAuthUser(c, tenantId, op, entityId);
                case AUTH_ROLE             -> applyAuthRole(c, tenantId, op, entityId);
                case AUTH_PERMISSION       -> applyAuthPermission(c, tenantId, op, entityId);
                case AUTH_USER_ROLE        -> applyAuthUserRoles(c, tenantId, op, entityId);
                case AUTH_ROLE_PERMISSION  -> applyAuthRolePermissions(c, tenantId, op, entityId);
                case IP_ACCESS_RULE        -> applyIpAccessRule(c, tenantId, op, entityId);
                case UI_MENU_PERMISSION    -> applyUiMenuPermission(c, tenantId, op, entityId);
                case TENANT_PROVISION      -> { /* handled by ProvisionTenantWorkflow, not this writer */ }
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

    // ---------- partner_extra ----------

    private void applyPartnerExtra(Connection c, Long tenantId, SyncOperation op, String entityId) throws Exception {
        long id = Long.parseLong(entityId);
        if (op == SyncOperation.DELETE) {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM partner_extra WHERE id = ?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
            return;
        }
        PartnerExtra e = PartnerExtra.findById(id);
        if (e == null) return;
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO partner_extra (id, partner_id, address1, address2, address3, address4, city, state, " +
                "postal_code, nid, trade_license, tin, tax_return_date, country_code) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE partner_id=VALUES(partner_id), address1=VALUES(address1), address2=VALUES(address2), " +
                "address3=VALUES(address3), address4=VALUES(address4), city=VALUES(city), state=VALUES(state), " +
                "postal_code=VALUES(postal_code), nid=VALUES(nid), trade_license=VALUES(trade_license), tin=VALUES(tin), " +
                "tax_return_date=VALUES(tax_return_date), country_code=VALUES(country_code)")) {
            ps.setLong(1, e.id);
            ps.setLong(2, e.partnerId);
            ps.setString(3, e.address1);
            ps.setString(4, e.address2);
            ps.setString(5, e.address3);
            ps.setString(6, e.address4);
            ps.setString(7, e.city);
            ps.setString(8, e.state);
            ps.setString(9, e.postalCode);
            ps.setString(10, e.nid);
            ps.setString(11, e.tradeLicense);
            ps.setString(12, e.tin);
            if (e.taxReturnDate != null) ps.setDate(13, java.sql.Date.valueOf(e.taxReturnDate));
            else ps.setNull(13, java.sql.Types.DATE);
            ps.setString(14, e.countryCode);
            ps.executeUpdate();
        }
    }

    // ---------- auth_user_role (replace-set by user_id) ----------

    private void applyAuthUserRoles(Connection c, Long tenantId, SyncOperation op, String entityId) throws Exception {
        long userId = Long.parseLong(entityId);
        try (PreparedStatement del = c.prepareStatement("DELETE FROM auth_user_role WHERE user_id = ?")) {
            del.setLong(1, userId);
            del.executeUpdate();
        }
        if (op == SyncOperation.DELETE) return;
        List<AuthUserRole> links = AuthUserRole.list("tenantId = ?1 and userId = ?2", tenantId, userId);
        if (links.isEmpty()) return;
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO auth_user_role (user_id, role_id) VALUES (?, ?)")) {
            for (AuthUserRole l : links) {
                ps.setLong(1, l.userId);
                ps.setLong(2, l.roleId);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    // ---------- auth_role_permission (replace-set by role_id) ----------

    private void applyAuthRolePermissions(Connection c, Long tenantId, SyncOperation op, String entityId) throws Exception {
        long roleId = Long.parseLong(entityId);
        try (PreparedStatement del = c.prepareStatement("DELETE FROM auth_role_permission WHERE role_id = ?")) {
            del.setLong(1, roleId);
            del.executeUpdate();
        }
        if (op == SyncOperation.DELETE) return;
        List<AuthRolePermission> links = AuthRolePermission.list("tenantId = ?1 and roleId = ?2", tenantId, roleId);
        if (links.isEmpty()) return;
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO auth_role_permission (role_id, permission_id) VALUES (?, ?)")) {
            for (AuthRolePermission l : links) {
                ps.setLong(1, l.roleId);
                ps.setLong(2, l.permissionId);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    // ---------- ip_access_rule ----------

    private void applyIpAccessRule(Connection c, Long tenantId, SyncOperation op, String entityId) throws Exception {
        long id = Long.parseLong(entityId);
        if (op == SyncOperation.DELETE) {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM ip_access_rule WHERE id = ?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
            return;
        }
        IpAccessRule r = IpAccessRule.findById(id);
        if (r == null) return;
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO ip_access_rule (id, user_id, ip, permission_type) VALUES (?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE user_id=VALUES(user_id), ip=VALUES(ip), permission_type=VALUES(permission_type)")) {
            ps.setLong(1, r.id);
            ps.setLong(2, r.userId);
            ps.setString(3, r.ip);
            ps.setString(4, r.permissionType);
            ps.executeUpdate();
        }
    }

    // ---------- ui_menu_permission ----------

    private void applyUiMenuPermission(Connection c, Long tenantId, SyncOperation op, String entityId) throws Exception {
        long id = Long.parseLong(entityId);
        if (op == SyncOperation.DELETE) {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM ui_menu_permission WHERE id = ?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
            return;
        }
        UiMenuPermission m = UiMenuPermission.findById(id);
        if (m == null) return;
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO ui_menu_permission (id, user_id, menu_key, permission_level) VALUES (?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE user_id=VALUES(user_id), menu_key=VALUES(menu_key), permission_level=VALUES(permission_level)")) {
            ps.setLong(1, m.id);
            ps.setLong(2, m.userId);
            ps.setString(3, m.menuKey);
            ps.setString(4, m.permissionLevel);
            ps.executeUpdate();
        }
    }
}

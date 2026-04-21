package com.telcobright.party.master.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "auth_role_permission")
@IdClass(AuthRolePermission.PK.class)
public class AuthRolePermission extends PanacheEntityBase {

    @Id
    @Column(name = "role_id", nullable = false)
    public Long roleId;

    @Id
    @Column(name = "permission_id", nullable = false)
    public Long permissionId;

    @Column(name = "tenant_id", nullable = false)
    public Long tenantId;

    public static class PK implements Serializable {
        public Long roleId;
        public Long permissionId;

        public PK() {}
        public PK(Long roleId, Long permissionId) {
            this.roleId = roleId;
            this.permissionId = permissionId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PK pk)) return false;
            return Objects.equals(roleId, pk.roleId) && Objects.equals(permissionId, pk.permissionId);
        }

        @Override
        public int hashCode() { return Objects.hash(roleId, permissionId); }
    }
}

package com.telcobright.party.master.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "auth_user_role")
@IdClass(AuthUserRole.PK.class)
public class AuthUserRole extends PanacheEntityBase {

    @Id
    @Column(name = "user_id", nullable = false)
    public Long userId;

    @Id
    @Column(name = "role_id", nullable = false)
    public Long roleId;

    @Column(name = "tenant_id", nullable = false)
    public Long tenantId;

    public static class PK implements Serializable {
        public Long userId;
        public Long roleId;

        public PK() {}
        public PK(Long userId, Long roleId) {
            this.userId = userId;
            this.roleId = roleId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PK pk)) return false;
            return Objects.equals(userId, pk.userId) && Objects.equals(roleId, pk.roleId);
        }

        @Override
        public int hashCode() { return Objects.hash(userId, roleId); }
    }
}

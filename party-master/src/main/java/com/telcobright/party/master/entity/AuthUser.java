package com.telcobright.party.master.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.telcobright.party.domain.UserStatus;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "auth_user")
public class AuthUser extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "tenant_id", nullable = false)
    public Long tenantId;

    @Column(name = "partner_id", nullable = false)
    public Long partnerId;

    @Column(nullable = false, length = 160)
    public String email;

    @JsonIgnore
    @Column(name = "password_hash", nullable = false, length = 200)
    public String passwordHash;

    @Column(name = "first_name", length = 80)
    public String firstName;

    @Column(name = "last_name", length = 80)
    public String lastName;

    @Column(length = 40)
    public String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_status", nullable = false, length = 20)
    public UserStatus userStatus;

    @Column(name = "reseller_db_name", length = 120)
    public String resellerDbName;

    @Column(name = "pbx_uuid", length = 60)
    public String pbxUuid;

    @Column(name = "last_login_at")
    public Instant lastLoginAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    public Instant updatedAt;
}

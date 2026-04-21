package com.telcobright.party.master.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.telcobright.party.domain.OperatorRole;
import com.telcobright.party.domain.UserStatus;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "operator_user")
public class OperatorUser extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "operator_id")
    public Long operatorId;

    @Column(nullable = false, length = 160, unique = true)
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
    @Column(nullable = false, length = 30)
    public OperatorRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public UserStatus status;

    @Column(name = "last_login_at")
    public Instant lastLoginAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    public Instant updatedAt;
}

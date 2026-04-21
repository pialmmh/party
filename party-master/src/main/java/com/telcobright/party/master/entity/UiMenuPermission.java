package com.telcobright.party.master.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "ui_menu_permission")
public class UiMenuPermission extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "tenant_id", nullable = false)
    public Long tenantId;

    @Column(name = "user_id", nullable = false)
    public Long userId;

    @Column(name = "menu_key", nullable = false, length = 100)
    public String menuKey;

    @Column(name = "permission_level", nullable = false, length = 20)
    public String permissionLevel; // NONE / READONLY / FULL

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    public Instant updatedAt;
}

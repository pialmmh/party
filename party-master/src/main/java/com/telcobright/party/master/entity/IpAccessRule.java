package com.telcobright.party.master.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "ip_access_rule")
public class IpAccessRule extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "tenant_id", nullable = false)
    public Long tenantId;

    @Column(name = "user_id", nullable = false)
    public Long userId;

    @Column(nullable = false, length = 45)
    public String ip;

    @Column(name = "permission_type", nullable = false, length = 10)
    public String permissionType; // ALLOW / DENY

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    public Instant createdAt;
}

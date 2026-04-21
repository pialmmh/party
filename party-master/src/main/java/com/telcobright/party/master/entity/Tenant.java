package com.telcobright.party.master.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "tenant")
public class Tenant extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "operator_id", nullable = false)
    public Long operatorId;

    @Column(name = "short_name", nullable = false, length = 40)
    public String shortName;

    @Column(name = "full_name", nullable = false, length = 200)
    public String fullName;

    @Column(name = "company_name", length = 200)
    public String companyName;

    @Column(length = 200)
    public String address1;

    @Column(length = 80)
    public String city;

    @Column(length = 80)
    public String country;

    @Column(length = 40)
    public String phone;

    @Column(length = 120)
    public String email;

    @Column(name = "db_host", nullable = false, length = 120)
    public String dbHost;

    @Column(name = "db_port", nullable = false)
    public Integer dbPort;

    @Column(name = "db_name", nullable = false, length = 120, unique = true)
    public String dbName;

    @Column(name = "db_user", nullable = false, length = 80)
    public String dbUser;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(name = "db_pass_ref", nullable = false, length = 200)
    public String dbPassRef;

    @Column(nullable = false, length = 20)
    public String status;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    public Instant updatedAt;
}

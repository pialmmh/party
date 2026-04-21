package com.telcobright.party.master.entity;

import com.telcobright.party.domain.OperatorType;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "operator")
public class Operator extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "short_name", nullable = false, length = 40, unique = true)
    public String shortName;

    @Column(name = "full_name", nullable = false, length = 200)
    public String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "operator_type", nullable = false, length = 20)
    public OperatorType operatorType;

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

    @Column(nullable = false, length = 20)
    public String status;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    public Instant updatedAt;
}

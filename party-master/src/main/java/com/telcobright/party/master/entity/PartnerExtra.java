package com.telcobright.party.master.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "partner_extra")
public class PartnerExtra extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "tenant_id", nullable = false)
    public Long tenantId;

    @Column(name = "partner_id", nullable = false)
    public Long partnerId;

    @Column(length = 200) public String address1;
    @Column(length = 200) public String address2;
    @Column(length = 200) public String address3;
    @Column(length = 200) public String address4;
    @Column(length = 80)  public String city;
    @Column(length = 80)  public String state;
    @Column(name = "postal_code", length = 40) public String postalCode;
    @Column(length = 40)  public String nid;
    @Column(name = "trade_license", length = 80) public String tradeLicense;
    @Column(length = 40)  public String tin;

    @Column(name = "tax_return_date")
    public LocalDate taxReturnDate;

    @Column(name = "country_code", length = 5)
    public String countryCode;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    public Instant updatedAt;
}

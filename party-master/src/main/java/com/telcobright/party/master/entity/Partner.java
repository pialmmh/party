package com.telcobright.party.master.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "partner")
public class Partner extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "tenant_id", nullable = false)
    public Long tenantId;

    @Column(name = "partner_name", nullable = false, length = 120)
    public String partnerName;

    @Column(name = "alternate_name_invoice", length = 400)
    public String alternateNameInvoice;

    @Column(name = "alternate_name_other", length = 400)
    public String alternateNameOther;

    @Column(length = 100) public String address1;
    @Column(length = 100) public String address2;
    @Column(length = 45)  public String city;
    @Column(length = 45)  public String state;
    @Column(name = "postal_code", length = 45) public String postalCode;
    @Column(length = 45)  public String country;
    @Column(length = 45)  public String telephone;
    @Column(length = 120) public String email;

    @Column(name = "customer_prepaid", nullable = false)
    public Boolean customerPrepaid;

    @Column(name = "partner_type", nullable = false, length = 30)
    public String partnerType;

    @Column(name = "billing_date")
    public Integer billingDate;

    @Column(name = "allowed_days_for_invoice_payment")
    public Integer allowedDaysForInvoicePayment;

    @Column(name = "timezone_offset_minutes")
    public Integer timezoneOffsetMinutes;

    @Column(name = "call_src_id")
    public Integer callSrcId;

    @Column(name = "default_currency", nullable = false)
    public Integer defaultCurrency;

    @Column(name = "invoice_address", length = 200)
    public String invoiceAddress;

    @Column(name = "vat_registration_no", length = 45)
    public String vatRegistrationNo;

    @Column(name = "payment_advice", length = 1000)
    public String paymentAdvice;

    @Column(nullable = false, length = 20)
    public String status;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    public Instant updatedAt;
}

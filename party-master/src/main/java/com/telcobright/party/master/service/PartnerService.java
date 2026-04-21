package com.telcobright.party.master.service;

import com.telcobright.party.domain.EntityType;
import com.telcobright.party.domain.SyncOperation;
import com.telcobright.party.master.entity.Partner;
import com.telcobright.party.master.entity.TenantSyncJob;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.util.List;

@ApplicationScoped
public class PartnerService {

    @Inject TenantSyncJobService syncJobs;
    @Inject SyncDispatcher dispatcher;

    public List<Partner> listByTenant(Long tenantId) {
        return Partner.list("tenantId", tenantId);
    }

    public Partner findById(Long tenantId, Long id) {
        Partner p = Partner.find("tenantId = ?1 and id = ?2", tenantId, id).firstResult();
        if (p == null) throw new NotFoundException("partner " + id + " not found in tenant " + tenantId);
        return p;
    }

    @Transactional
    public Partner create(Long tenantId, Partner p) {
        p.tenantId = tenantId;
        if (p.status == null) p.status = "ACTIVE";
        if (p.partnerType == null) p.partnerType = "DIRECT";
        if (p.customerPrepaid == null) p.customerPrepaid = true;
        if (p.defaultCurrency == null) p.defaultCurrency = 1;
        p.persist();
        TenantSyncJob job = syncJobs.enqueue(tenantId, EntityType.PARTNER, String.valueOf(p.id), SyncOperation.CREATE);
        dispatcher.dispatch(job);
        return p;
    }

    @Transactional
    public Partner update(Long tenantId, Long id, Partner patch) {
        Partner p = findById(tenantId, id);
        if (patch.partnerName != null) p.partnerName = patch.partnerName;
        if (patch.alternateNameInvoice != null) p.alternateNameInvoice = patch.alternateNameInvoice;
        if (patch.alternateNameOther != null) p.alternateNameOther = patch.alternateNameOther;
        if (patch.address1 != null) p.address1 = patch.address1;
        if (patch.address2 != null) p.address2 = patch.address2;
        if (patch.city != null) p.city = patch.city;
        if (patch.state != null) p.state = patch.state;
        if (patch.postalCode != null) p.postalCode = patch.postalCode;
        if (patch.country != null) p.country = patch.country;
        if (patch.telephone != null) p.telephone = patch.telephone;
        if (patch.email != null) p.email = patch.email;
        if (patch.customerPrepaid != null) p.customerPrepaid = patch.customerPrepaid;
        if (patch.partnerType != null) p.partnerType = patch.partnerType;
        if (patch.billingDate != null) p.billingDate = patch.billingDate;
        if (patch.allowedDaysForInvoicePayment != null) p.allowedDaysForInvoicePayment = patch.allowedDaysForInvoicePayment;
        if (patch.timezoneOffsetMinutes != null) p.timezoneOffsetMinutes = patch.timezoneOffsetMinutes;
        if (patch.callSrcId != null) p.callSrcId = patch.callSrcId;
        if (patch.defaultCurrency != null) p.defaultCurrency = patch.defaultCurrency;
        if (patch.invoiceAddress != null) p.invoiceAddress = patch.invoiceAddress;
        if (patch.vatRegistrationNo != null) p.vatRegistrationNo = patch.vatRegistrationNo;
        if (patch.paymentAdvice != null) p.paymentAdvice = patch.paymentAdvice;
        if (patch.status != null) p.status = patch.status;
        TenantSyncJob job = syncJobs.enqueue(tenantId, EntityType.PARTNER, String.valueOf(p.id), SyncOperation.UPDATE);
        dispatcher.dispatch(job);
        return p;
    }

    @Transactional
    public void delete(Long tenantId, Long id) {
        Partner p = findById(tenantId, id);
        p.status = "DELETED";
        TenantSyncJob job = syncJobs.enqueue(tenantId, EntityType.PARTNER, String.valueOf(p.id), SyncOperation.DELETE);
        dispatcher.dispatch(job);
    }
}

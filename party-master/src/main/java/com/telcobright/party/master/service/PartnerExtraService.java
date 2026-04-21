package com.telcobright.party.master.service;

import com.telcobright.party.domain.EntityType;
import com.telcobright.party.domain.SyncOperation;
import com.telcobright.party.master.entity.PartnerExtra;
import com.telcobright.party.master.entity.TenantSyncJob;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class PartnerExtraService {

    @Inject TenantSyncJobService syncJobs;
    @Inject SyncDispatcher dispatcher;

    public PartnerExtra findByPartner(Long tenantId, Long partnerId) {
        return PartnerExtra.find("tenantId = ?1 and partnerId = ?2", tenantId, partnerId).firstResult();
    }

    @Transactional
    public PartnerExtra upsert(Long tenantId, Long partnerId, PartnerExtra incoming) {
        PartnerExtra existing = findByPartner(tenantId, partnerId);
        boolean create = (existing == null);
        PartnerExtra target = create ? new PartnerExtra() : existing;
        target.tenantId = tenantId;
        target.partnerId = partnerId;
        if (incoming.address1 != null) target.address1 = incoming.address1;
        if (incoming.address2 != null) target.address2 = incoming.address2;
        if (incoming.address3 != null) target.address3 = incoming.address3;
        if (incoming.address4 != null) target.address4 = incoming.address4;
        if (incoming.city != null) target.city = incoming.city;
        if (incoming.state != null) target.state = incoming.state;
        if (incoming.postalCode != null) target.postalCode = incoming.postalCode;
        if (incoming.nid != null) target.nid = incoming.nid;
        if (incoming.tradeLicense != null) target.tradeLicense = incoming.tradeLicense;
        if (incoming.tin != null) target.tin = incoming.tin;
        if (incoming.taxReturnDate != null) target.taxReturnDate = incoming.taxReturnDate;
        if (incoming.countryCode != null) target.countryCode = incoming.countryCode;
        if (create) target.persist();
        TenantSyncJob job = syncJobs.enqueue(tenantId, EntityType.PARTNER_EXTRA, String.valueOf(target.id),
                create ? SyncOperation.CREATE : SyncOperation.UPDATE);
        dispatcher.dispatch(job);
        return target;
    }
}

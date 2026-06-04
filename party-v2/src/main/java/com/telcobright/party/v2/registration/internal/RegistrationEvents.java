package com.telcobright.party.v2.registration.internal;

import com.telcobright.party.v2.registration.api.emit.DeviceRevoked;
import com.telcobright.party.v2.registration.api.emit.SubscriberProvisioned;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Publishes the feature's typed events (api/emit) on the CDI event bus and
 * logs them — both are lifecycle milestones, so info-level is RULE ONE
 * compliant. An external bus (Pulsar) can subscribe via a CDI observer later.
 */
@ApplicationScoped
public class RegistrationEvents {

    private static final Logger LOG = Logger.getLogger(RegistrationEvents.class);

    @Inject Event<SubscriberProvisioned> provisioned;
    @Inject Event<DeviceRevoked> revoked;

    public void emit(SubscriberProvisioned ev) {
        LOG.infof("SubscriberProvisioned partner=%d e164=%s jid=%s device=%s",
                ev.partnerId(), ev.e164(), ev.jid(), ev.deviceId());
        provisioned.fire(ev);
    }

    public void emit(DeviceRevoked ev) {
        LOG.infof("DeviceRevoked device=%s e164=%s", ev.deviceId(), ev.e164());
        revoked.fire(ev);
    }
}

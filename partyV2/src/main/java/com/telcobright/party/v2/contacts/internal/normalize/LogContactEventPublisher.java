package com.telcobright.party.v2.contacts.internal.normalize;

import com.telcobright.party.v2.contacts.publishes.ContactEvent;
import com.telcobright.party.v2.contacts.spi.ContactEventPublisher;
import org.jboss.logging.Logger;

/**
 * Dev publisher — logs the event at debug (RULE ONE: never log per-event at
 * info). Selected by ContactPublisherProvider when the NATS feed is disabled;
 * constructed directly (not a CDI bean), so it carries no scope annotation.
 */
public class LogContactEventPublisher implements ContactEventPublisher {

    private static final Logger LOG = Logger.getLogger(LogContactEventPublisher.class);

    @Override
    public void publish(String ownerPersonId, ContactEvent event) {
        LOG.debugf("contactEvent owner=%s type=%s contactId=%s personId=%s source=%s v=%d",
                ownerPersonId, event.type(), event.contactId(), event.personId(),
                event.source(), event.version());
    }
}

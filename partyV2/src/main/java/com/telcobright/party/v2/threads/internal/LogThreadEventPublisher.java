package com.telcobright.party.v2.threads.internal;

import com.telcobright.party.v2.threads.publishes.ThreadEvent;
import com.telcobright.party.v2.threads.spi.ThreadEventPublisher;
import org.jboss.logging.Logger;

/**
 * Dev publisher — logs the event at debug (RULE ONE: never log per-event at
 * info). Selected by ThreadPublisherProvider when the NATS feed is disabled;
 * constructed directly (not a CDI bean), so it carries no scope annotation.
 */
public class LogThreadEventPublisher implements ThreadEventPublisher {

    private static final Logger LOG = Logger.getLogger(LogThreadEventPublisher.class);

    @Override
    public void publish(String ownerPersonId, ThreadEvent event) {
        LOG.debugf("threadEvent owner=%s type=%s conversationId=%s v=%d readUpTo=%s",
                ownerPersonId, event.type(), event.conversationId(), event.version(), event.readUpTo());
    }
}

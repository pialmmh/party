package com.telcobright.party.v2.contacts.internal.nats;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telcobright.party.v2.contacts.internal.normalize.LogContactEventPublisher;
import com.telcobright.party.v2.contacts.spi.ContactEventPublisher;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import org.jboss.logging.Logger;

/**
 * Picks the contact-event publisher at runtime: the NATS publisher when
 * {@code party.v2.contacts.nats.enabled=true}, otherwise the dev log publisher.
 * One producer = one unambiguous {@link ContactEventPublisher} bean.
 */
@ApplicationScoped
public class ContactPublisherProvider {

    private static final Logger LOG = Logger.getLogger(ContactPublisherProvider.class);

    @Produces
    @ApplicationScoped
    public ContactEventPublisher contactEventPublisher(ContactsNatsConfig cfg, ObjectMapper json) {
        if (!cfg.enabled()) {
            LOG.info("contacts publisher = log (NATS disabled)");
            return new LogContactEventPublisher();
        }
        NatsContactEventPublisher publisher = new NatsContactEventPublisher(cfg, json);
        publisher.start();
        return publisher;
    }

    public void close(@Disposes ContactEventPublisher publisher) {
        if (publisher instanceof NatsContactEventPublisher nats) {
            nats.stop();
        }
    }
}

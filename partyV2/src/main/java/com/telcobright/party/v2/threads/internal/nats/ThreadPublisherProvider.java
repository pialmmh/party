package com.telcobright.party.v2.threads.internal.nats;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telcobright.party.v2.threads.internal.LogThreadEventPublisher;
import com.telcobright.party.v2.threads.spi.ThreadEventPublisher;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import org.jboss.logging.Logger;

/**
 * Picks the thread-event publisher at runtime: the NATS publisher when
 * {@code party.v2.threads.nats.enabled=true}, otherwise the dev log publisher.
 * One producer = one unambiguous {@link ThreadEventPublisher} bean. Mirrors
 * ContactPublisherProvider.
 */
@ApplicationScoped
public class ThreadPublisherProvider {

    private static final Logger LOG = Logger.getLogger(ThreadPublisherProvider.class);

    @Produces
    @ApplicationScoped
    public ThreadEventPublisher threadEventPublisher(ThreadsNatsConfig cfg, ObjectMapper json) {
        if (!cfg.enabled()) {
            LOG.info("threads publisher = log (NATS disabled)");
            return new LogThreadEventPublisher();
        }
        NatsThreadEventPublisher publisher = new NatsThreadEventPublisher(cfg, json);
        publisher.start();
        return publisher;
    }

    public void close(@Disposes ThreadEventPublisher publisher) {
        if (publisher instanceof NatsThreadEventPublisher nats) {
            nats.stop();
        }
    }
}

package com.telcobright.party.v2.threads.spi;

import com.telcobright.party.v2.threads.publishes.ThreadEvent;

/**
 * Drops one {@link ThreadEvent} on the owner's per-account thread feed. The
 * production impl publishes to SL_THREADS on NATS; dev logs it. Mirrors
 * {@code ContactEventPublisher} (threads are the 3rd sync datatype).
 */
public interface ThreadEventPublisher {

    /** Publish onto {@code sl.threads.<token(ownerPersonId)>}. */
    void publish(String ownerPersonId, ThreadEvent event);
}

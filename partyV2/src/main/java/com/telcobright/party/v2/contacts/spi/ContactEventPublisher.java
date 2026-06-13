package com.telcobright.party.v2.contacts.spi;

import com.telcobright.party.v2.contacts.publishes.ContactEvent;

/**
 * Sends ONE canonical contact event to the owner's per-account feed. The
 * production impl publishes to the per-account NATS JetStream subject (the
 * sync-proxy reads it and frames it to the device); tests use an in-memory
 * fake. Faking this port is what makes the normalizer unit-testable without
 * NATS.
 */
public interface ContactEventPublisher {

    void publish(String ownerPersonId, ContactEvent event);
}

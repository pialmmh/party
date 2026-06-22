package com.telcobright.party.v2.threads.internal;

import com.telcobright.party.v2.threads.publishes.ThreadEvent;
import com.telcobright.party.v2.threads.spi.ThreadEntryStore;
import com.telcobright.party.v2.threads.spi.ThreadEntryStore.State;
import com.telcobright.party.v2.threads.spi.ThreadEventPublisher;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Clock;

/**
 * Applies one thread action to the owner's overlay and announces it (frozen §8b).
 * The server is the version authority (O3): a real change takes the owner's NEXT
 * monotonic version and is published as ONE {@link ThreadEvent} on the owner's
 * feed; an idempotent no-op (the flag was already in that state, or a read marker
 * that does not advance) stores nothing and emits nothing. Mirrors the contacts
 * ContactNormalizer ingest, minus resolution (the conversationId is opaque).
 */
@ApplicationScoped
public class ThreadService {

    @Inject ThreadEntryStore store;
    @Inject ThreadEventPublisher publisher;
    @Inject Clock clock;

    /** The upload echo: the authoritative version + whether anything changed. */
    public record Applied(String conversationId, long version, boolean changed) {}

    public Applied apply(String ownerPersonId, ThreadAction action, String conversationId,
                         String originId, String postedReadUpTo) {
        State cur = store.find(ownerPersonId, conversationId).orElse(State.empty());
        State next = action.applyTo(cur, postedReadUpTo);
        if (next.sameOverlay(cur)) {
            // idempotent no-op: no version bump, no publish (the device keeps its optimistic row).
            return new Applied(conversationId, cur.version(), false);
        }
        long version = store.save(ownerPersonId, conversationId,
                next.archived(), next.pinned(), next.muted(), next.deleted(), next.readUpTo());
        long ts = clock.millis();
        ThreadEvent event = action.isRead()
                ? ThreadEvent.read(conversationId, version, originId, next.readUpTo(), ts)
                : ThreadEvent.flag(action.wire(), conversationId, version, originId, ts);
        publisher.publish(ownerPersonId, event);
        return new Applied(conversationId, version, true);
    }
}

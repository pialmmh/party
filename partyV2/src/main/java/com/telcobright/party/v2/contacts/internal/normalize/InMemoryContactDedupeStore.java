package com.telcobright.party.v2.contacts.internal.normalize;

import com.telcobright.party.v2.contacts.spi.ContactDedupeStore;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Dev default dedupe ledger — a per-process map. Proves the idempotency +
 * version contract; NOT durable across restarts. The production impl (a small
 * per-owner table) replaces it in the next slice (drift).
 */
@ApplicationScoped
public class InMemoryContactDedupeStore implements ContactDedupeStore {

    private final Map<String, String> hashByEntry = new ConcurrentHashMap<>();   // owner|contactId -> hash
    private final Map<String, AtomicLong> versionByOwner = new ConcurrentHashMap<>();

    @Override
    public Optional<String> lastHash(String ownerPersonId, String contactId) {
        return Optional.ofNullable(hashByEntry.get(entry(ownerPersonId, contactId)));
    }

    @Override
    public long commit(String ownerPersonId, String contactId, String contentHash) {
        long version = versionByOwner.computeIfAbsent(ownerPersonId, o -> new AtomicLong())
                .incrementAndGet();
        hashByEntry.put(entry(ownerPersonId, contactId), contentHash);
        return version;
    }

    private static String entry(String ownerPersonId, String contactId) {
        return ownerPersonId + "|" + contactId;
    }
}

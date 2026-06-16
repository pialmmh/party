package com.telcobright.party.v2.testkit;

import com.telcobright.party.v2.contacts.publishes.ContactCard;
import com.telcobright.party.v2.contacts.spi.ContactEntryStore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/** In-memory ContactEntryStore for unit tests — same idempotency/version/snapshot contract. */
public final class InMemoryContactEntryStore implements ContactEntryStore {

    private record Stored(String hash, long version, String personId, String source,
                          ContactCard card, boolean deleted) {}

    private final Map<String, Stored> byEntry = new ConcurrentHashMap<>();   // owner|contactId
    private final Map<String, AtomicLong> versionByOwner = new ConcurrentHashMap<>();

    @Override
    public Optional<Entry> find(String ownerPersonId, String contactId) {
        Stored s = byEntry.get(key(ownerPersonId, contactId));
        return s == null ? Optional.empty() : Optional.of(new Entry(s.hash(), s.version(), s.deleted()));
    }

    @Override
    public long upsert(String ownerPersonId, String contactId, String contentHash,
                       String personId, String source, ContactCard card) {
        long version = nextVersion(ownerPersonId);
        byEntry.put(key(ownerPersonId, contactId), new Stored(contentHash, version, personId, source, card, false));
        return version;
    }

    @Override
    public long tombstone(String ownerPersonId, String contactId) {
        long version = nextVersion(ownerPersonId);
        byEntry.put(key(ownerPersonId, contactId), new Stored("DELETED", version, null, null, null, true));
        return version;
    }

    @Override
    public Page snapshotPage(String ownerPersonId, String afterContactId, int limit) {
        String prefix = ownerPersonId + "|";
        String after = afterContactId == null ? "" : afterContactId;
        List<SnapshotRow> rows = byEntry.entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefix) && !e.getValue().deleted())
                .map(e -> new SnapshotRow(e.getKey().substring(prefix.length()), e.getValue().version(),
                        e.getValue().personId(), e.getValue().source(), e.getValue().card()))
                .filter(r -> r.contactId().compareTo(after) > 0)              // keyset
                .sorted(Comparator.comparing(SnapshotRow::contactId))
                .limit((long) limit + 1)                                     // +1 sentinel
                .collect(Collectors.toCollection(ArrayList::new));
        boolean more = rows.size() > limit;
        List<SnapshotRow> page = more ? rows.subList(0, limit) : rows;
        String nextCursor = more ? page.get(page.size() - 1).contactId() : null;
        long cursor = versionByOwner.getOrDefault(ownerPersonId, new AtomicLong()).get();
        return new Page(List.copyOf(page), nextCursor, cursor);
    }

    private long nextVersion(String ownerPersonId) {
        return versionByOwner.computeIfAbsent(ownerPersonId, o -> new AtomicLong()).incrementAndGet();
    }

    private static String key(String ownerPersonId, String contactId) {
        return ownerPersonId + "|" + contactId;
    }
}

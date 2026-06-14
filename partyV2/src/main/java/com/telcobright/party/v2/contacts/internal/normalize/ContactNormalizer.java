package com.telcobright.party.v2.contacts.internal.normalize;

import com.telcobright.party.v2.contacts.publishes.ContactCard;
import com.telcobright.party.v2.contacts.publishes.ContactEvent;
import com.telcobright.party.v2.contacts.spi.ContactEntryStore;
import com.telcobright.party.v2.contacts.spi.ContactEntryStore.Entry;
import com.telcobright.party.v2.contacts.spi.ContactEventPublisher;
import com.telcobright.party.v2.contacts.spi.ContactSource;
import com.telcobright.party.v2.contacts.spi.Handle;
import com.telcobright.party.v2.contacts.spi.PartyDirectory;
import com.telcobright.party.v2.contacts.spi.PartyDirectory.PersonRef;
import com.telcobright.party.v2.contacts.spi.RawContact;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

/**
 * The ONE funnel every contact change passes through: clean the handles,
 * resolve them to a global person, store the entry, and emit a single canonical
 * contact event on the owner's feed. Idempotent — re-ingesting unchanged content
 * stores nothing and emits nothing, and the owner's version advances only on a
 * real change. Every fan-in producer (phone-book import, manual add,
 * re-resolution on join) calls this; none publishes its own event.
 */
@ApplicationScoped
public class ContactNormalizer {

    private final PartyDirectory directory;
    private final ContactEventPublisher publisher;
    private final ContactEntryStore entries;

    public ContactNormalizer(PartyDirectory directory, ContactEventPublisher publisher,
                             ContactEntryStore entries) {
        this.directory = directory;
        this.publisher = publisher;
        this.entries = entries;
    }

    /** The outcome of an ingest: the entry's stable id, its version, and whether it changed. */
    public record IngestResult(String contactId, long version, boolean changed) {}

    /**
     * Normalize one raw contact, resolve its handles, store + emit ONE upsert.
     * @return the result, or empty when the entry had no usable handle (phone/email).
     */
    public Optional<IngestResult> ingest(String ownerPersonId, RawContact raw, ContactSource source) {
        List<Handle> handles = HandleNormalizer.normalize(raw.rawHandles());
        if (handles.isEmpty()) return Optional.empty();
        String personId = resolvePersonId(handles);
        ContactCard card = new ContactCard(personId, raw.fullName(), raw.label(), raw.note(), handles);
        return Optional.of(applyUpsert(ownerPersonId, ContactHashing.contactId(handles), personId, source, card));
    }

    /** Tombstone one entry. Idempotent — a second delete stores nothing and emits nothing. */
    public Optional<Long> remove(String ownerPersonId, String contactId, ContactSource source) {
        Optional<Entry> existing = entries.find(ownerPersonId, contactId);
        if (existing.isEmpty() || existing.get().deleted()) return Optional.empty();
        long version = entries.tombstone(ownerPersonId, contactId);
        publisher.publish(ownerPersonId, ContactEvent.delete(contactId, source.wire(), version));
        return Optional.of(version);
    }

    // ── named steps ───────────────────────────────────────────────────────

    private IngestResult applyUpsert(String owner, String contactId, String personId,
                                     ContactSource source, ContactCard card) {
        String hash = ContactHashing.contentHash(card);
        Optional<Entry> existing = entries.find(owner, contactId);
        if (existing.isPresent() && !existing.get().deleted()
                && hash.equals(existing.get().contentHash())) {
            return new IngestResult(contactId, existing.get().version(), false);   // unchanged
        }
        long version = entries.upsert(owner, contactId, hash, personId, source.wire(), card);
        publisher.publish(owner, ContactEvent.upsert(contactId, personId, source.wire(), version, card));
        return new IngestResult(contactId, version, true);
    }

    /** First handle that resolves to a user wins; null = a non-user entry (kept for invite). */
    private String resolvePersonId(List<Handle> handles) {
        for (Handle handle : handles) {
            Optional<PersonRef> resolved = directory.resolve(handle);
            if (resolved.isPresent()) return resolved.get().personId();
        }
        return null;
    }
}

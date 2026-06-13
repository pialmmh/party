package com.telcobright.party.v2.contacts.internal.normalize;

import com.telcobright.party.v2.contacts.publishes.ContactEvent;
import com.telcobright.party.v2.contacts.publishes.ContactEvent.ContactCard;
import com.telcobright.party.v2.contacts.spi.ContactDedupeStore;
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
 * resolve them to a global person, and emit a single canonical contact event
 * on the owner's feed. Idempotent — re-ingesting unchanged content emits
 * nothing, and the owner's version advances only on a real change. Every
 * fan-in producer (phone-book import, manual add, re-resolution on join) calls
 * this; none publishes its own event.
 */
@ApplicationScoped
public class ContactNormalizer {

    private static final String DELETED = "DELETED";

    private final PartyDirectory directory;
    private final ContactEventPublisher publisher;
    private final ContactDedupeStore dedupe;

    public ContactNormalizer(PartyDirectory directory, ContactEventPublisher publisher,
                             ContactDedupeStore dedupe) {
        this.directory = directory;
        this.publisher = publisher;
        this.dedupe = dedupe;
    }

    /**
     * Normalize one raw contact, resolve its handles, emit ONE upsert.
     * @return the new version, or empty when the entry was skipped (no usable
     *         handle) or unchanged (idempotent).
     */
    public Optional<Long> ingest(String ownerPersonId, RawContact raw, ContactSource source) {
        List<Handle> handles = HandleNormalizer.normalize(raw.rawHandles());
        if (handles.isEmpty()) return Optional.empty();
        String personId = resolvePersonId(handles);
        ContactCard card = new ContactCard(raw.name(), handles, raw.petname(), raw.groups());
        return publishUpsert(ownerPersonId, ContactHashing.contactId(handles), personId, source, card);
    }

    /** Tombstone one entry. Idempotent — a second delete emits nothing. */
    public Optional<Long> remove(String ownerPersonId, String contactId, ContactSource source) {
        if (DELETED.equals(dedupe.lastHash(ownerPersonId, contactId).orElse(null))) {
            return Optional.empty();
        }
        long version = dedupe.commit(ownerPersonId, contactId, DELETED);
        publisher.publish(ownerPersonId, ContactEvent.delete(ownerPersonId, contactId, source.wire(), version));
        return Optional.of(version);
    }

    // ── named steps ───────────────────────────────────────────────────────

    private Optional<Long> publishUpsert(String owner, String contactId, String personId,
                                         ContactSource source, ContactCard card) {
        String hash = ContactHashing.contentHash(card, personId);
        if (hash.equals(dedupe.lastHash(owner, contactId).orElse(null))) {
            return Optional.empty();   // unchanged — idempotent skip
        }
        long version = dedupe.commit(owner, contactId, hash);
        publisher.publish(owner, ContactEvent.upsert(owner, contactId, personId, source.wire(), version, card));
        return Optional.of(version);
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

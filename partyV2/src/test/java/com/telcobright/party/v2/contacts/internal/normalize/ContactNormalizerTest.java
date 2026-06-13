package com.telcobright.party.v2.contacts.internal.normalize;

import com.telcobright.party.v2.contacts.publishes.ContactEvent;
import com.telcobright.party.v2.contacts.spi.RawContact;
import com.telcobright.party.v2.testkit.FakeContactEventPublisher;
import com.telcobright.party.v2.testkit.FakePartyDirectory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static com.telcobright.party.v2.contacts.spi.ContactSource.MANUAL;
import static com.telcobright.party.v2.contacts.spi.ContactSource.PHONEBOOK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one funnel: normalize → resolve → emit ONE canonical event, idempotently.
 * Driven entirely on fakes — no Odoo, no NATS.
 */
class ContactNormalizerTest {

    private final FakePartyDirectory directory = new FakePartyDirectory();
    private final FakeContactEventPublisher publisher = new FakeContactEventPublisher();
    private final InMemoryContactDedupeStore dedupe = new InMemoryContactDedupeStore();
    private final ContactNormalizer normalizer = new ContactNormalizer(directory, publisher, dedupe);

    private static RawContact raw(String name, String... handles) {
        return new RawContact(name, List.of(handles), null, null);
    }

    @Test
    void userResolvesToPersonIdAndEmitsOneUpsert() {
        directory.user("+8801711000001", "p:42", "Alice");

        Optional<Long> version = normalizer.ingest("p:1", raw("Alice", "+8801711000001"), PHONEBOOK);

        assertEquals(1L, version.orElseThrow());
        assertEquals(1, publisher.events.size());
        ContactEvent e = publisher.last();
        assertEquals(ContactEvent.UPSERT, e.type());
        assertEquals("p:1", e.ownerPersonId());
        assertEquals("p:42", e.personId());
        assertEquals("phonebook", e.source());
        assertEquals("Alice", e.card().name());
        assertEquals(1, e.card().handles().size());
    }

    @Test
    void nonUserIsKeptWithNullPersonId() {
        Optional<Long> version = normalizer.ingest("p:1", raw("Stranger", "+8801799999999"), PHONEBOOK);

        assertTrue(version.isPresent());
        assertNull(publisher.last().personId());     // kept per-owner for invite, no global card
    }

    @Test
    void reingestingUnchangedContentEmitsNothing() {
        directory.user("+8801711000001", "p:42", "Alice");
        normalizer.ingest("p:1", raw("Alice", "+8801711000001"), PHONEBOOK);

        Optional<Long> again = normalizer.ingest("p:1", raw("Alice", "+8801711000001"), PHONEBOOK);

        assertTrue(again.isEmpty());
        assertEquals(1, publisher.events.size());
    }

    @Test
    void changedPetnameBumpsVersionButKeepsTheSameContactId() {
        RawContact first = new RawContact("Alice", List.of("+8801711000001"), null, null);
        RawContact renamed = new RawContact("Alice", List.of("+8801711000001"), "Ali", null);

        assertEquals(1L, normalizer.ingest("p:1", first, MANUAL).orElseThrow());
        String firstId = publisher.last().contactId();
        assertEquals(2L, normalizer.ingest("p:1", renamed, MANUAL).orElseThrow());

        assertEquals(2, publisher.events.size());
        assertEquals(firstId, publisher.last().contactId());   // same handles → same entry
    }

    @Test
    void firstHandleThatResolvesWins() {
        directory.user("+8801711000002", "p:9", "Bob");   // only the 2nd-sorted handle is a user

        normalizer.ingest("p:1", raw("Two Numbers", "+8801711000001", "+8801711000002"), PHONEBOOK);

        assertEquals("p:9", publisher.last().personId());
    }

    @Test
    void entryWithNoUsableHandleIsSkipped() {
        Optional<Long> version = normalizer.ingest("p:1", raw("Noise", "garbage"), PHONEBOOK);

        assertTrue(version.isEmpty());
        assertTrue(publisher.events.isEmpty());
    }

    @Test
    void removeTombstonesIdempotently() {
        directory.user("+8801711000001", "p:42", "Alice");
        normalizer.ingest("p:1", raw("Alice", "+8801711000001"), MANUAL);
        String contactId = publisher.last().contactId();

        Optional<Long> deleted = normalizer.remove("p:1", contactId, MANUAL);
        assertTrue(deleted.isPresent());
        assertEquals(ContactEvent.DELETE, publisher.last().type());
        assertNull(publisher.last().card());

        Optional<Long> deletedAgain = normalizer.remove("p:1", contactId, MANUAL);
        assertTrue(deletedAgain.isEmpty());              // already a tombstone
    }

    @Test
    void versionsAreMonotonicPerOwner() {
        normalizer.ingest("p:1", raw("A", "+8801711000001"), PHONEBOOK);
        normalizer.ingest("p:1", raw("B", "+8801711000002"), PHONEBOOK);

        assertEquals(2, publisher.events.size());
        assertEquals(1L, publisher.events.get(0).version());
        assertEquals(2L, publisher.events.get(1).version());
    }

    @Test
    void ownersAreIsolated() {
        normalizer.ingest("p:1", raw("A", "+8801711000001"), PHONEBOOK);
        normalizer.ingest("p:2", raw("A", "+8801711000001"), PHONEBOOK);

        assertEquals(1L, publisher.events.get(0).version());
        assertEquals(1L, publisher.events.get(1).version());   // per-owner counters
        assertFalse(publisher.owners.get(0).equals(publisher.owners.get(1)));
    }
}

package com.telcobright.party.v2.contacts.internal.normalize;

import com.telcobright.party.v2.contacts.internal.normalize.ContactNormalizer.IngestResult;
import com.telcobright.party.v2.contacts.publishes.ContactEvent;
import com.telcobright.party.v2.contacts.spi.RawContact;
import com.telcobright.party.v2.testkit.FakeContactEventPublisher;
import com.telcobright.party.v2.testkit.FakePartyDirectory;
import com.telcobright.party.v2.testkit.InMemoryContactEntryStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.telcobright.party.v2.contacts.spi.ContactSource.MANUAL;
import static com.telcobright.party.v2.contacts.spi.ContactSource.PHONEBOOK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one funnel: normalize → resolve → store + emit ONE canonical event,
 * idempotently. Driven entirely on fakes — no Odoo, no MySQL, no NATS.
 */
class ContactNormalizerTest {

    private final FakePartyDirectory directory = new FakePartyDirectory();
    private final FakeContactEventPublisher publisher = new FakeContactEventPublisher();
    private final InMemoryContactEntryStore entries = new InMemoryContactEntryStore();
    private final ContactNormalizer normalizer = new ContactNormalizer(directory, publisher, entries);

    private static RawContact raw(String fullName, String... handles) {
        return RawContact.of(fullName, List.of(handles));
    }

    @Test
    void userResolvesToPersonIdAndEmitsOneUpsert() {
        directory.user("+8801711000001", "p:42", "Alice");

        IngestResult result = normalizer.ingest("p:1", raw("Alice", "+8801711000001"), PHONEBOOK).orElseThrow();

        assertEquals(1L, result.version());
        assertTrue(result.changed());
        assertEquals(1, publisher.events.size());
        ContactEvent e = publisher.last();
        assertEquals(ContactEvent.UPSERT, e.type());
        assertEquals("p:42", e.personId());
        assertEquals("phonebook", e.source());
        assertEquals("Alice", e.card().fullName());
        assertEquals("p:42", e.card().uid());        // card uid mirrors the resolved person
        assertEquals(1, e.card().handles().size());
        assertEquals("phone", e.card().handles().get(0).kind());
        assertEquals("p:1", publisher.owners.get(0));   // owner travels with publish, not in the body
    }

    @Test
    void nonUserIsKeptWithNullPersonId() {
        IngestResult result = normalizer.ingest("p:1", raw("Stranger", "+8801799999999"), PHONEBOOK).orElseThrow();

        assertTrue(result.changed());
        assertNull(publisher.last().personId());      // kept per-owner for invite, no global card
        assertNull(publisher.last().card().uid());
    }

    @Test
    void reingestingUnchangedContentChangesNothing() {
        directory.user("+8801711000001", "p:42", "Alice");
        normalizer.ingest("p:1", raw("Alice", "+8801711000001"), PHONEBOOK);

        IngestResult again = normalizer.ingest("p:1", raw("Alice", "+8801711000001"), PHONEBOOK).orElseThrow();

        assertFalse(again.changed());
        assertEquals(1L, again.version());            // same version returned
        assertEquals(1, publisher.events.size());     // nothing re-published
    }

    @Test
    void changedLabelBumpsVersionButKeepsTheSameContactId() {
        RawContact first = new RawContact("Alice", List.of("+8801711000001"), null, null);
        RawContact relabeled = new RawContact("Alice", List.of("+8801711000001"), "Ali", null);

        IngestResult one = normalizer.ingest("p:1", first, MANUAL).orElseThrow();
        IngestResult two = normalizer.ingest("p:1", relabeled, MANUAL).orElseThrow();

        assertEquals(1L, one.version());
        assertEquals(2L, two.version());
        assertTrue(two.changed());
        assertEquals(one.contactId(), two.contactId());   // same handles → same entry
        assertEquals("Ali", publisher.last().card().label());
    }

    @Test
    void firstHandleThatResolvesWins() {
        directory.user("+8801711000002", "p:9", "Bob");   // only the 2nd-sorted handle is a user

        normalizer.ingest("p:1", raw("Two Numbers", "+8801711000001", "+8801711000002"), PHONEBOOK);

        assertEquals("p:9", publisher.last().personId());
    }

    @Test
    void entryWithNoUsableHandleIsSkipped() {
        assertTrue(normalizer.ingest("p:1", raw("Noise", "garbage"), PHONEBOOK).isEmpty());
        assertTrue(publisher.events.isEmpty());
    }

    @Test
    void removeTombstonesIdempotently() {
        directory.user("+8801711000001", "p:42", "Alice");
        String contactId = normalizer.ingest("p:1", raw("Alice", "+8801711000001"), MANUAL).orElseThrow().contactId();

        long deletedVersion = normalizer.remove("p:1", contactId, MANUAL).orElseThrow();
        assertEquals(2L, deletedVersion);
        assertEquals(ContactEvent.DELETE, publisher.last().type());
        assertNull(publisher.last().card());

        assertTrue(normalizer.remove("p:1", contactId, MANUAL).isEmpty());   // already a tombstone
    }

    @Test
    void reAddingAfterDeleteRepublishes() {
        directory.user("+8801711000001", "p:42", "Alice");
        String contactId = normalizer.ingest("p:1", raw("Alice", "+8801711000001"), MANUAL).orElseThrow().contactId();
        normalizer.remove("p:1", contactId, MANUAL);

        IngestResult readd = normalizer.ingest("p:1", raw("Alice", "+8801711000001"), MANUAL).orElseThrow();

        assertTrue(readd.changed());           // tombstone cleared → real change
        assertEquals(3L, readd.version());
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

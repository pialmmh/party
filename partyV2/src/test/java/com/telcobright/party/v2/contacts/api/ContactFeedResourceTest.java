package com.telcobright.party.v2.contacts.api;

import com.telcobright.party.v2.contacts.api.ContactFeedResource.AddContactRequest;
import com.telcobright.party.v2.contacts.api.ContactFeedResource.AddContactResponse;
import com.telcobright.party.v2.contacts.api.ContactFeedResource.DeleteContactResponse;
import com.telcobright.party.v2.contacts.api.ContactFeedResource.SnapshotResponse;
import com.telcobright.party.v2.contacts.internal.ContactsConfig;
import com.telcobright.party.v2.contacts.publishes.ContactEvent;
import com.telcobright.party.v2.contacts.internal.OwnerResolver;
import com.telcobright.party.v2.contacts.internal.normalize.ContactNormalizer;
import com.telcobright.party.v2.sync.SyncConfig;
import com.telcobright.party.v2.testkit.Beans;
import com.telcobright.party.v2.testkit.FakeContactEventPublisher;
import com.telcobright.party.v2.testkit.FakePartyDirectory;
import com.telcobright.party.v2.testkit.InMemoryContactEntryStore;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The feed endpoints over fakes: owner header → personId, add → snapshot, bad input → 4xx. */
class ContactFeedResourceTest {

    private static final String OWNER_E164 = "+8801712340000";

    private final FakePartyDirectory directory = new FakePartyDirectory().user(OWNER_E164, "p:7", "Owner");
    private final FakeContactEventPublisher publisher = new FakeContactEventPublisher();
    private final InMemoryContactEntryStore entries = new InMemoryContactEntryStore();

    private ContactFeedResource resource() {
        return resource(5000);
    }

    private ContactFeedResource resource(int batchSize) {
        OwnerResolver owner = new OwnerResolver();
        Beans.set(owner, "cfg", stubConfig());   // dev-header path; tokens unused
        ContactFeedResource res = new ContactFeedResource();
        Beans.set(res, "normalizer", new ContactNormalizer(directory, publisher, entries));
        Beans.set(res, "entries", entries);
        Beans.set(res, "owner", owner);
        Beans.set(res, "directory", directory);
        Beans.set(res, "sync", (SyncConfig) () -> batchSize);
        return res;
    }

    private static AddContactRequest add(String fullName, String handle) {
        return new AddContactRequest(fullName, List.of(handle), null, null, null);
    }

    @Test
    void addResolvesOwnerAndReturnsContactIdAndVersion() {
        AddContactResponse out = resource().add(null, OWNER_E164, add("Alice", "+8801711000001"));

        assertNotNull(out.contactId());
        assertEquals(1L, out.version());
        assertTrue(out.changed());
    }

    @Test
    void originIdInTheRequestBodyIsEchoedOnTheEvent() {
        AddContactRequest req = new AddContactRequest("Alice", List.of("+8801711000001"), null, null, "o:dev-42");
        resource().add(null, OWNER_E164, req);
        assertEquals("o:dev-42", publisher.last().originId());   // resource -> normalizer -> event wiring
    }

    @Test
    void snapshotReturnsFlattenedCardsWithMetadata() {
        ContactFeedResource res = resource();
        res.add(null, OWNER_E164, add("Alice", "+8801711000001"));
        res.add(null, OWNER_E164, add("Bob", "+8801711000002"));

        SnapshotResponse snap = res.snapshot(null, OWNER_E164, null, null);
        assertEquals("p:7", snap.owner());                 // device learns its own personId for the feed subscription
        assertEquals(2, snap.contacts().size());
        assertEquals(2L, snap.cursor());
        assertNull(snap.nextCursor());                     // ≤batch owner: one page, no more
        assertEquals("Alice", snap.contacts().get(0).fullName());
        assertEquals("manual", snap.contacts().get(0).source());
        assertEquals("phone", snap.contacts().get(0).handles().get(0).kind());
    }

    @Test
    void reAddingTheSameContactIsIdempotent() {
        ContactFeedResource res = resource();
        AddContactResponse first = res.add(null, OWNER_E164, add("Alice", "+8801711000001"));
        AddContactResponse again = res.add(null, OWNER_E164, add("Alice", "+8801711000001"));

        assertEquals(first.contactId(), again.contactId());
        assertFalse(again.changed());
        assertEquals(1, res.snapshot(null, OWNER_E164, null, null).contacts().size());
    }

    @Test
    void snapshotPagesAtBatchSizeAndCarriesAKeysetCursor() {
        ContactFeedResource res = resource(2);   // tiny batch so 3 contacts span two pages
        res.add(null, OWNER_E164, add("Alice", "+8801711000001"));
        res.add(null, OWNER_E164, add("Bob",   "+8801711000002"));
        res.add(null, OWNER_E164, add("Carol", "+8801711000003"));

        SnapshotResponse p1 = res.snapshot(null, OWNER_E164, null, null);
        assertEquals(2, p1.contacts().size());                   // capped at batchSize
        assertNotNull(p1.nextCursor());                          // more pages

        SnapshotResponse p2 = res.snapshot(null, OWNER_E164, p1.nextCursor(), null);
        assertEquals(1, p2.contacts().size());
        assertNull(p2.nextCursor());                             // last page

        // the two pages together = the whole book, no overlap
        var ids = new java.util.HashSet<String>();
        p1.contacts().forEach(c -> ids.add(c.contactId()));
        p2.contacts().forEach(c -> ids.add(c.contactId()));
        assertEquals(3, ids.size());
    }

    @Test
    void snapshotLimitIsClampedToBatchSize() {
        ContactFeedResource res = resource(2);
        res.add(null, OWNER_E164, add("Alice", "+8801711000001"));
        res.add(null, OWNER_E164, add("Bob",   "+8801711000002"));
        res.add(null, OWNER_E164, add("Carol", "+8801711000003"));

        // a client asking for 999 still gets at most batchSize (2) rows
        SnapshotResponse p = res.snapshot(null, OWNER_E164, null, 999);
        assertEquals(2, p.contacts().size());
        assertNotNull(p.nextCursor());
    }

    @Test
    void noUsableHandleIsBadRequest() {
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> resource().add(null, OWNER_E164, add("Noise", "garbage")));
        assertEquals(400, ex.getResponse().getStatus());
    }

    @Test
    void ownerThatIsNotAProvisionedPersonIsUnauthorized() {
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> resource().add(null, "+8801799999999", add("Alice", "+8801711000001")));
        assertEquals(401, ex.getResponse().getStatus());   // owner e164 not in the directory
    }

    @Test
    void deleteTombstonesEmitsContactDeleteAndIsIdempotent() {
        ContactFeedResource res = resource();
        AddContactResponse added = res.add(null, OWNER_E164, add("Alice", "+8801711000001"));

        DeleteContactResponse del = res.delete(null, OWNER_E164, added.contactId(), "manual");
        assertEquals(added.contactId(), del.contactId());
        assertTrue(del.changed());
        assertEquals(2L, del.version());                                    // tombstone bumps the per-owner seq
        assertEquals(0, res.snapshot(null, OWNER_E164, null, null).contacts().size());  // excluded from the active snapshot
        assertEquals(ContactEvent.DELETE, publisher.last().type());         // ONE contactDelete published
        assertEquals(added.contactId(), publisher.last().contactId());

        int eventsAfterFirst = publisher.events.size();
        DeleteContactResponse again = res.delete(null, OWNER_E164, added.contactId(), "manual");
        assertFalse(again.changed());                                       // idempotent no-op
        assertEquals(eventsAfterFirst, publisher.events.size());            // emits nothing the second time
    }

    @Test
    void deleteWithUnknownSourceIsBadRequest() {
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> resource().delete(null, OWNER_E164, "c:anything", "bogus"));
        assertEquals(400, ex.getResponse().getStatus());
    }

    private static ContactsConfig stubConfig() {
        return new ContactsConfig() {
            @Override public Invites invites() { return () -> false; }
            @Override public boolean ownerHeaderEnabled() { return true; }
        };
    }
}

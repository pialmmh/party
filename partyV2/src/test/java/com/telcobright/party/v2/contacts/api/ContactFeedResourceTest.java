package com.telcobright.party.v2.contacts.api;

import com.telcobright.party.v2.contacts.api.ContactFeedResource.AddContactRequest;
import com.telcobright.party.v2.contacts.api.ContactFeedResource.AddContactResponse;
import com.telcobright.party.v2.contacts.api.ContactFeedResource.DeleteContactResponse;
import com.telcobright.party.v2.contacts.api.ContactFeedResource.SnapshotResponse;
import com.telcobright.party.v2.contacts.internal.ContactsConfig;
import com.telcobright.party.v2.contacts.publishes.ContactEvent;
import com.telcobright.party.v2.contacts.internal.OwnerResolver;
import com.telcobright.party.v2.contacts.internal.normalize.ContactNormalizer;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The feed endpoints over fakes: owner header → personId, add → snapshot, bad input → 4xx. */
class ContactFeedResourceTest {

    private static final String OWNER_E164 = "+8801712340000";

    private final FakePartyDirectory directory = new FakePartyDirectory().user(OWNER_E164, "p:7", "Owner");
    private final FakeContactEventPublisher publisher = new FakeContactEventPublisher();
    private final InMemoryContactEntryStore entries = new InMemoryContactEntryStore();

    private ContactFeedResource resource() {
        OwnerResolver owner = new OwnerResolver();
        Beans.set(owner, "cfg", stubConfig());   // dev-header path; tokens unused
        ContactFeedResource res = new ContactFeedResource();
        Beans.set(res, "normalizer", new ContactNormalizer(directory, publisher, entries));
        Beans.set(res, "entries", entries);
        Beans.set(res, "owner", owner);
        Beans.set(res, "directory", directory);
        return res;
    }

    private static AddContactRequest add(String fullName, String handle) {
        return new AddContactRequest(fullName, List.of(handle), null, null);
    }

    @Test
    void addResolvesOwnerAndReturnsContactIdAndVersion() {
        AddContactResponse out = resource().add(null, OWNER_E164, add("Alice", "+8801711000001"));

        assertNotNull(out.contactId());
        assertEquals(1L, out.version());
        assertTrue(out.changed());
    }

    @Test
    void snapshotReturnsFlattenedCardsWithMetadata() {
        ContactFeedResource res = resource();
        res.add(null, OWNER_E164, add("Alice", "+8801711000001"));
        res.add(null, OWNER_E164, add("Bob", "+8801711000002"));

        SnapshotResponse snap = res.snapshot(null, OWNER_E164);
        assertEquals("p:7", snap.owner());                 // device learns its own personId for the feed subscription
        assertEquals(2, snap.contacts().size());
        assertEquals(2L, snap.cursor());
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
        assertEquals(1, res.snapshot(null, OWNER_E164).contacts().size());
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
        assertEquals(0, res.snapshot(null, OWNER_E164).contacts().size());  // excluded from the active snapshot
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

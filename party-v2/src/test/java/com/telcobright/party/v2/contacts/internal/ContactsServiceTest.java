package com.telcobright.party.v2.contacts.internal;

import com.telcobright.party.v2.contacts.api.emit.ContactsChanged;
import com.telcobright.party.v2.contacts.api.spi.ContactStore;
import com.telcobright.party.v2.contacts.api.spi.ContactStore.ContactRow;
import com.telcobright.party.v2.testkit.Beans;
import com.telcobright.party.v2.testkit.FakeFacadeDirectory;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.NotificationOptions;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** §6 graph semantics in-unit: states, tombstones, per-owner seq, cursor discipline. */
class ContactsServiceTest {

    static final String OWNER = "+8801711000099";

    /** In-memory impl of the ContactStore port — same per-owner monotonic-seq contract. */
    static class FakeContactStore implements ContactStore {
        final Map<String, Map<String, ContactRow>> byOwner = new LinkedHashMap<>();
        final Map<String, Long> high = new LinkedHashMap<>();

        private Map<String, ContactRow> rows(String owner) {
            return byOwner.computeIfAbsent(owner, k -> new LinkedHashMap<>());
        }

        @Override public long upsertWithNextSeq(String owner, String contact, String petname,
                                                String state, boolean keepExistingPetnameIfNull) {
            long seq = high.merge(owner, 1L, Long::sum);
            ContactRow existing = rows(owner).get(contact);
            String effective = (petname == null && keepExistingPetnameIfNull && existing != null)
                    ? existing.petname() : petname;
            rows(owner).put(contact, new ContactRow(owner, contact, effective, state, seq));
            return seq;
        }
        @Override public Optional<ContactRow> find(String owner, String contact) {
            return Optional.ofNullable(rows(owner).get(contact));
        }
        @Override public List<ContactRow> listSince(String owner, long since) {
            return rows(owner).values().stream()
                    .filter(r -> r.seq() > since)
                    .sorted(Comparator.comparingLong(ContactRow::seq)).toList();
        }
        @Override public List<ContactRow> snapshot(String owner) {
            return rows(owner).values().stream()
                    .filter(r -> !DELETED.equals(r.state()))
                    .sorted(Comparator.comparing(ContactRow::contactE164)).toList();
        }
        @Override public long highSeq(String owner) { return high.getOrDefault(owner, 0L); }
    }

    /** Records fires; async/select are out of scope for the unit. */
    static class FakeEvent implements Event<ContactsChanged> {
        final List<ContactsChanged> fired = new ArrayList<>();
        @Override public void fire(ContactsChanged ev) { fired.add(ev); }
        @Override public CompletionStage<ContactsChanged> fireAsync(ContactsChanged ev) {
            throw new UnsupportedOperationException();
        }
        @Override public CompletionStage<ContactsChanged> fireAsync(ContactsChanged ev, NotificationOptions o) {
            throw new UnsupportedOperationException();
        }
        @Override public Event<ContactsChanged> select(Annotation... q) {
            throw new UnsupportedOperationException();
        }
        @Override public <U extends ContactsChanged> Event<U> select(Class<U> t, Annotation... q) {
            throw new UnsupportedOperationException();
        }
        @Override public <U extends ContactsChanged> Event<U> select(TypeLiteral<U> t, Annotation... q) {
            throw new UnsupportedOperationException();
        }
    }

    private ContactsService service;
    private FakeContactStore store;
    private FakeFacadeDirectory facades;
    private FakeEvent changed;
    private List<String> invites;
    private boolean inviteDeliveryUp;

    @BeforeEach
    void setUp() {
        store = new FakeContactStore();
        facades = new FakeFacadeDirectory();
        changed = new FakeEvent();
        invites = new ArrayList<>();
        inviteDeliveryUp = true;

        service = new ContactsService();
        Beans.set(service, "store", store);
        Beans.set(service, "facades", facades);
        Beans.set(service, "inviteSender",
                (com.telcobright.party.v2.contacts.api.spi.InviteSender) (from, to) -> {
                    if (!inviteDeliveryUp) throw new IllegalStateException("no gateway");
                    invites.add(from + ">" + to);
                });
        Beans.set(service, "changed", changed);
        Beans.set(service, "xmppDomain", "localhost");
    }

    private static int status(WebApplicationException e) { return e.getResponse().getStatus(); }

    @Test
    void put_isActiveWithFacade_invitedWithout() {
        facades.seed("+8801711000001", "active", "Alice");

        ContactRow alice = service.put(OWNER, "+8801711000001", "Ali");
        ContactRow nobody = service.put(OWNER, "+8801711000002", null);

        assertEquals(ContactStore.ACTIVE, alice.state());
        assertEquals("Ali", alice.petname());
        assertEquals(ContactStore.INVITED, nobody.state());
        assertEquals(List.of(1L, 2L), changed.fired.stream().map(ContactsChanged::seq).toList());
    }

    @Test
    void put_neverSilentlyUnblocks() {
        facades.seed("+8801711000001", "active", "Alice");
        service.block(OWNER, "+8801711000001");

        ContactRow row = service.put(OWNER, "+8801711000001", "Ali");
        assertEquals(ContactStore.BLOCKED, row.state(), "PUT on a blocked contact stays blocked");
        assertEquals("Ali", row.petname(), "petname still updates");
    }

    @Test
    void delete_tombstones_andDeltaPropagatesIt() {
        facades.seed("+8801711000001", "active", "Alice");
        service.put(OWNER, "+8801711000001", null);
        long cursor = store.highSeq(OWNER);

        service.delete(OWNER, "+8801711000001");

        assertTrue(service.snapshot(OWNER).contacts().isEmpty(), "snapshot hides tombstones");
        List<ContactRow> delta = service.since(OWNER, cursor).contacts();
        assertEquals(1, delta.size());
        assertEquals(ContactStore.DELETED, delta.get(0).state());
    }

    @Test
    void delete_ofNeverContact_isIdempotentNoOp() {
        service.delete(OWNER, "+8801711000042");
        assertTrue(changed.fired.isEmpty());
    }

    @Test
    void since_staleOrInvalidCursor_is410() {
        facades.seed("+8801711000001", "active", "Alice");
        service.put(OWNER, "+8801711000001", null);

        assertEquals(410, status(assertThrows(WebApplicationException.class,
                () -> service.since(OWNER, 99))));
        assertEquals(410, status(assertThrows(WebApplicationException.class,
                () -> service.since(OWNER, -1))));
        assertEquals(1, service.since(OWNER, 0).contacts().size(), "cursor 0 replays all");
    }

    @Test
    void blockAnyNumber_thenUnblock_returnsStateByFacadeExistence() {
        facades.seed("+8801711000001", "active", "Alice");
        service.block(OWNER, "+8801711000001");
        service.block(OWNER, "+8801711000002"); // never a contact, no facade

        service.unblock(OWNER, "+8801711000001");
        service.unblock(OWNER, "+8801711000002");

        assertEquals(ContactStore.ACTIVE, store.find(OWNER, "+8801711000001").orElseThrow().state());
        assertEquals(ContactStore.INVITED, store.find(OWNER, "+8801711000002").orElseThrow().state());
    }

    @Test
    void unblock_whenNotBlocked_isNoOp() {
        facades.seed("+8801711000001", "active", "Alice");
        service.put(OWNER, "+8801711000001", null);
        int firedBefore = changed.fired.size();

        service.unblock(OWNER, "+8801711000001");
        assertEquals(firedBefore, changed.fired.size());
    }

    @Test
    void addingYourself_is400() {
        assertEquals(400, status(assertThrows(WebApplicationException.class,
                () -> service.put(OWNER, OWNER, null))));
    }

    @Test
    void jid_onlyForActiveRows() {
        facades.seed("+8801711000001", "active", "Alice");
        ContactRow active = service.put(OWNER, "+8801711000001", null);
        ContactRow invited = service.put(OWNER, "+8801711000002", null);

        assertEquals("8801711000001@localhost", service.jidIfActive(active));
        assertNull(service.jidIfActive(invited));
    }

    @Test
    void invite_deliversOr503() {
        service.invite(OWNER, "+8801711000042");
        assertEquals(List.of(OWNER + ">+8801711000042"), invites);

        inviteDeliveryUp = false;
        assertEquals(503, status(assertThrows(WebApplicationException.class,
                () -> service.invite(OWNER, "+8801711000043"))));
    }
}

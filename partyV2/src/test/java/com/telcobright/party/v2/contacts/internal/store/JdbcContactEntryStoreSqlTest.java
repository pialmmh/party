package com.telcobright.party.v2.contacts.internal.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telcobright.party.v2.contacts.publishes.ContactEvent.ContactCard;
import com.telcobright.party.v2.contacts.spi.ContactEntryStore.Entry;
import com.telcobright.party.v2.contacts.spi.ContactEntryStore.Snapshot;
import com.telcobright.party.v2.contacts.spi.Handle;
import com.telcobright.party.v2.testkit.Beans;
import com.telcobright.party.v2.testkit.LocalMysql;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SQL tier: the entry store's version/idempotency/snapshot contract on REAL MySQL (skipped if absent). */
class JdbcContactEntryStoreSqlTest {

    private static final String OWNER = "p:7001";

    private JdbcContactEntryStore store;

    private static ContactCard card(String uid, String name, String tel) {
        return new ContactCard(uid, name, List.of(Handle.tel(tel)), null, List.of(), null);
    }

    @BeforeEach
    void setUp() {
        Assumptions.assumeTrue(LocalMysql.available(), "local MySQL not reachable — SQL tier skipped");
        LocalMysql.dropTable("contact_entry");
        LocalMysql.dropTable("contact_entry_seq");
        store = new JdbcContactEntryStore();
        Beans.set(store, "ds", LocalMysql.ds());
        Beans.set(store, "json", new ObjectMapper());
    }

    @Test
    void versionsAreMonotonicPerOwnerAndIndependentAcrossOwners() {
        assertEquals(1, store.upsert(OWNER, "c1", "h1", "p:42", card("p:42", "Alice", "+8801711000001")));
        assertEquals(2, store.upsert(OWNER, "c2", "h2", null, card(null, "Stranger", "+8801799999999")));
        assertEquals(1, store.upsert("p:7002", "c1", "h1", null, card(null, "Other", "+8801711000003")));

        assertEquals(2, store.snapshot(OWNER).cursor());
    }

    @Test
    void findReturnsHashAndVersionAndDeletedState() {
        store.upsert(OWNER, "c1", "hashA", "p:42", card("p:42", "Alice", "+8801711000001"));

        Entry e = store.find(OWNER, "c1").orElseThrow();
        assertEquals("hashA", e.contentHash());
        assertEquals(1, e.version());
        assertFalse(e.deleted());
        assertTrue(store.find(OWNER, "missing").isEmpty());
    }

    @Test
    void upsertUpdatesInPlaceWithANewVersion() {
        store.upsert(OWNER, "c1", "hashA", "p:42", card("p:42", "Alice", "+8801711000001"));
        long v2 = store.upsert(OWNER, "c1", "hashB", "p:42", card("p:42", "Alice Smith", "+8801711000001"));

        assertEquals(2, v2);
        assertEquals("hashB", store.find(OWNER, "c1").orElseThrow().contentHash());
        assertEquals(1, store.snapshot(OWNER).rows().size());   // still one entry
    }

    @Test
    void snapshotReturnsCurrentCardsAndCursor() {
        store.upsert(OWNER, "c1", "h1", "p:42", card("p:42", "Alice", "+8801711000001"));
        store.upsert(OWNER, "c2", "h2", null, card(null, "Bob", "+8801711000002"));

        Snapshot snap = store.snapshot(OWNER);
        assertEquals(2, snap.rows().size());
        assertEquals(2, snap.cursor());
        assertEquals("c1", snap.rows().get(0).contactId());     // ordered by contact_id
        assertEquals("Alice", snap.rows().get(0).card().name());
        assertEquals("+8801711000001", snap.rows().get(0).card().handles().get(0).value());
        assertEquals("p:42", snap.rows().get(0).personId());
        assertNull(snap.rows().get(1).personId());              // Bob is a non-user
    }

    @Test
    void tombstoneHidesFromSnapshotButAdvancesCursor() {
        store.upsert(OWNER, "c1", "h1", "p:42", card("p:42", "Alice", "+8801711000001"));
        long deletedVersion = store.tombstone(OWNER, "c1");

        assertEquals(2, deletedVersion);
        Snapshot snap = store.snapshot(OWNER);
        assertTrue(snap.rows().isEmpty());
        assertEquals(2, snap.cursor());                         // cursor includes the tombstone
        assertTrue(store.find(OWNER, "c1").orElseThrow().deleted());
    }

    @Test
    void cardWithEveryFieldRoundTripsThroughJson() {
        ContactCard full = new ContactCard("p:42", "Alice", List.of(
                Handle.tel("+8801711000001"), Handle.email("alice@example.com")),
                "Ali", List.of("family", "vip"), "media:photo123");
        store.upsert(OWNER, "c1", "h1", "p:42", full);

        ContactCard back = store.snapshot(OWNER).rows().get(0).card();
        assertEquals(full, back);                               // record equality = every field survived
    }
}

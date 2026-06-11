package com.telcobright.party.v2.contacts.internal.store;

import com.telcobright.party.v2.contacts.api.spi.ContactStore;
import com.telcobright.party.v2.contacts.api.spi.ContactStore.ContactRow;
import com.telcobright.party.v2.testkit.Beans;
import com.telcobright.party.v2.testkit.LocalMysql;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** SQL tier: the §6 seq/tombstone contract on the REAL MySQL adapter (skipped if absent). */
class JdbcContactStoreSqlTest {

    static final String OWNER = "+8801711000099";

    private JdbcContactStore store;

    @BeforeEach
    void setUp() {
        Assumptions.assumeTrue(LocalMysql.available(), "local MySQL not reachable — SQL tier skipped");
        LocalMysql.dropTable("contact");
        LocalMysql.dropTable("contact_seq");
        store = new JdbcContactStore();
        Beans.set(store, "ds", LocalMysql.ds());
    }

    @Test
    void seq_isStrictlyMonotonic_perOwner_andIndependentAcrossOwners() {
        assertEquals(1, store.upsertWithNextSeq(OWNER, "+8801711000001", null, ContactStore.ACTIVE, true));
        assertEquals(2, store.upsertWithNextSeq(OWNER, "+8801711000002", null, ContactStore.INVITED, true));
        assertEquals(3, store.upsertWithNextSeq(OWNER, "+8801711000001", null, ContactStore.DELETED, true));
        assertEquals(1, store.upsertWithNextSeq("+8801711000088", "+8801711000001", null, ContactStore.ACTIVE, true));
        assertEquals(3, store.highSeq(OWNER));
        assertEquals(1, store.highSeq("+8801711000088"));
    }

    @Test
    void petname_preservedOnKeep_overwrittenOtherwise() {
        store.upsertWithNextSeq(OWNER, "+8801711000001", "Ali", ContactStore.ACTIVE, false);
        store.upsertWithNextSeq(OWNER, "+8801711000001", null, ContactStore.BLOCKED, true);
        assertEquals("Ali", store.find(OWNER, "+8801711000001").orElseThrow().petname(),
                "block must not clobber the petname");

        store.upsertWithNextSeq(OWNER, "+8801711000001", "Alice", ContactStore.ACTIVE, false);
        assertEquals("Alice", store.find(OWNER, "+8801711000001").orElseThrow().petname());

        store.upsertWithNextSeq(OWNER, "+8801711000001", null, ContactStore.ACTIVE, false);
        assertNull(store.find(OWNER, "+8801711000001").orElseThrow().petname(),
                "explicit null without keep clears it");
    }

    @Test
    void snapshot_hidesTombstones_deltaCarriesThem() {
        store.upsertWithNextSeq(OWNER, "+8801711000001", null, ContactStore.ACTIVE, true);
        store.upsertWithNextSeq(OWNER, "+8801711000002", null, ContactStore.ACTIVE, true);
        long cursor = store.highSeq(OWNER);
        store.upsertWithNextSeq(OWNER, "+8801711000001", null, ContactStore.DELETED, true);

        assertEquals(List.of("+8801711000002"),
                store.snapshot(OWNER).stream().map(ContactRow::contactE164).toList());

        List<ContactRow> delta = store.listSince(OWNER, cursor);
        assertEquals(1, delta.size());
        assertEquals(ContactStore.DELETED, delta.get(0).state());
        assertEquals(3, delta.get(0).seq());
    }

    @Test
    void concurrentAllocators_forTheSameOwner_neverDuplicateASeq() throws Exception {
        // prime the owner: the counter row exists, workers race on the bump
        store.upsertWithNextSeq(OWNER, "+8801711000000", null, ContactStore.ACTIVE, true);

        int workers = 12;
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        try {
            List<Callable<Long>> jobs = IntStream.range(1, workers + 1)
                    .mapToObj(i -> (Callable<Long>) () -> store.upsertWithNextSeq(
                            OWNER, "+88017110001" + String.format("%02d", i), null,
                            ContactStore.ACTIVE, true))
                    .toList();

            Set<Long> seqs = pool.invokeAll(jobs).stream().map(f -> {
                try { return f.get(); } catch (Exception e) { throw new RuntimeException(e); }
            }).collect(Collectors.toSet());

            assertEquals(workers, seqs.size(), "every allocation got a distinct seq");
            assertEquals(workers + 1, store.highSeq(OWNER), "high-water = primer + workers");
        } finally {
            pool.shutdownNow();
        }
    }
}

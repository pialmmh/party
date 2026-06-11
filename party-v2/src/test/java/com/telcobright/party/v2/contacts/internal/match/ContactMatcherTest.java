package com.telcobright.party.v2.contacts.internal.match;

import com.telcobright.party.v2.testkit.Beans;
import com.telcobright.party.v2.testkit.FakeFacadeDirectory;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** §6 phonebook match: only ACTIVE facades match; noise is skipped, never fatal. */
class ContactMatcherTest {

    private ContactMatcher matcher(FakeFacadeDirectory facades) {
        ContactMatcher m = new ContactMatcher();
        Beans.set(m, "facades", facades);
        return m;
    }

    @Test
    void activeFacadesMatch_othersAreNonUsers() {
        FakeFacadeDirectory facades = new FakeFacadeDirectory()
                .seed("+8801711000001", "active", "Alice")
                .seed("+8801711000002", "suspended", "Bob");

        ContactMatcher.SyncResult r = matcher(facades).match(List.of(
                "+8801711000001", "+8801711000002", "+8801711000003"));

        assertEquals(1, r.matches().size());
        assertEquals("8801711000001@localhost", r.matches().get(0).jid());
        assertEquals("Alice", r.matches().get(0).displayName());
        assertEquals(List.of("+8801711000002", "+8801711000003"), r.nonUsers());
    }

    @Test
    void unparseableNumbersAreSkipped_notFatal() {
        FakeFacadeDirectory facades = new FakeFacadeDirectory()
                .seed("+8801711000001", "active", "Alice");

        ContactMatcher.SyncResult r = matcher(facades).match(Arrays.asList(
                "garbage", "", null, "+8801711000001"));

        assertEquals(1, r.matches().size());
        assertTrue(r.nonUsers().isEmpty());
    }

    @Test
    void duplicatesAndAltFormats_normalizeToOneEntry() {
        FakeFacadeDirectory facades = new FakeFacadeDirectory()
                .seed("+8801711000001", "active", "Alice");

        ContactMatcher.SyncResult r = matcher(facades).match(List.of(
                "+8801711000001", "008801711000001"));

        assertEquals(1, r.matches().size(), "00-prefix normalizes to the same E.164");
    }

    @Test
    void emptyAndNullInput_yieldEmptyResult() {
        ContactMatcher.SyncResult r = matcher(new FakeFacadeDirectory()).match(null);
        assertTrue(r.matches().isEmpty());
        assertTrue(r.nonUsers().isEmpty());
    }
}

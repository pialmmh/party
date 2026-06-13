package com.telcobright.party.v2.contacts.internal.normalize;

import com.telcobright.party.v2.contacts.spi.Handle;
import com.telcobright.party.v2.contacts.spi.PartyDirectory.PersonRef;
import com.telcobright.party.v2.testkit.Beans;
import com.telcobright.party.v2.testkit.FakeFacadeDirectory;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Handle → person through the Odoo facade: only ACTIVE tel facades are users. */
class OdooPartyDirectoryTest {

    private OdooPartyDirectory directory(FakeFacadeDirectory facades) {
        OdooPartyDirectory d = new OdooPartyDirectory();
        Beans.set(d, "facades", facades);
        return d;
    }

    @Test
    void activeTelResolvesToPersonId() {
        FakeFacadeDirectory facades = new FakeFacadeDirectory().seed("+8801711000001", "active", "Alice");

        Optional<PersonRef> person = directory(facades).resolve(Handle.tel("+8801711000001"));

        assertTrue(person.isPresent());
        assertEquals("p:101", person.get().personId());   // FakeFacadeDirectory partnerId = 100 + id(1)
        assertEquals("Alice", person.get().displayName());
    }

    @Test
    void suspendedFacadeIsANonUser() {
        FakeFacadeDirectory facades = new FakeFacadeDirectory().seed("+8801711000002", "suspended", "Bob");
        assertTrue(directory(facades).resolve(Handle.tel("+8801711000002")).isEmpty());
    }

    @Test
    void unknownTelIsANonUser() {
        assertTrue(directory(new FakeFacadeDirectory()).resolve(Handle.tel("+8801711000009")).isEmpty());
    }

    @Test
    void emailIsNotResolvedYet() {
        assertTrue(directory(new FakeFacadeDirectory()).resolve(Handle.email("a@b.com")).isEmpty());
    }
}

package com.telcobright.party.v2.testkit;

import com.telcobright.party.v2.contacts.spi.Handle;
import com.telcobright.party.v2.contacts.spi.PartyDirectory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** In-memory PartyDirectory: seed users by handle value; everything else is a non-user. */
public final class FakePartyDirectory implements PartyDirectory {

    private final Map<String, PersonRef> byValue = new HashMap<>();
    public RuntimeException failWith;   // set to simulate a directory outage

    public FakePartyDirectory user(String handleValue, String personId, String displayName) {
        byValue.put(handleValue, new PersonRef(personId, displayName));
        return this;
    }

    @Override
    public Optional<PersonRef> resolve(Handle handle) {
        if (failWith != null) throw failWith;
        return Optional.ofNullable(byValue.get(handle.value()));
    }
}

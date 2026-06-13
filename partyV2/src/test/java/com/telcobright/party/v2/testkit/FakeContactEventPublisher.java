package com.telcobright.party.v2.testkit;

import com.telcobright.party.v2.contacts.publishes.ContactEvent;
import com.telcobright.party.v2.contacts.spi.ContactEventPublisher;

import java.util.ArrayList;
import java.util.List;

/** Records every published contact event, in order, for assertions. */
public final class FakeContactEventPublisher implements ContactEventPublisher {

    public final List<ContactEvent> events = new ArrayList<>();
    public final List<String> owners = new ArrayList<>();

    @Override
    public void publish(String ownerPersonId, ContactEvent event) {
        owners.add(ownerPersonId);
        events.add(event);
    }

    public ContactEvent last() {
        return events.get(events.size() - 1);
    }
}

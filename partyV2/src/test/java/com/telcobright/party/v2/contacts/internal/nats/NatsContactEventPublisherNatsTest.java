package com.telcobright.party.v2.contacts.internal.nats;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telcobright.party.v2.contacts.publishes.ContactCard;
import com.telcobright.party.v2.contacts.publishes.ContactEvent;
import com.telcobright.party.v2.contacts.spi.Handle;
import io.nats.client.Connection;
import io.nats.client.JetStreamManagement;
import io.nats.client.Nats;
import io.nats.client.api.MessageInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * NATS tier: the real publish to it_vm's JetStream (skipped if the broker is
 * unreachable). Uses a DEDICATED test stream so the live SL_CONTACTS is never
 * touched. Verifies the canonical JSON lands on the owner subject and that the
 * idempotent producer dedups a re-publish of the same fact.
 */
class NatsContactEventPublisherNatsTest {

    private static final String URL = "nats://10.10.185.1:4222";
    private static final String IT_STREAM = "SL_CONTACTS_IT";
    // DISJOINT from the live SL_CONTACTS subjects (sl.contacts.>): "sl.contacts.it." sits
    // UNDER that wildcard, so once the real stream exists JetStream rejects this test stream
    // as overlapping [10065]. "slit.contacts." shares no prefix token with "sl.contacts.".
    private static final String IT_PREFIX = "slit.contacts.";

    private final ObjectMapper json = new ObjectMapper();
    private NatsContactEventPublisher publisher;
    private Connection admin;

    private static boolean natsReachable() {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("10.10.185.1", 4222), 2000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private ContactsNatsConfig cfg() {
        return new ContactsNatsConfig() {
            @Override public boolean enabled() { return true; }
            @Override public String url() { return URL; }
            @Override public String stream() { return IT_STREAM; }
            @Override public String subjectPrefix() { return IT_PREFIX; }
            @Override public long maxAgeDays() { return 1; }
        };
    }

    @BeforeEach
    void setUp() throws Exception {
        Assumptions.assumeTrue(natsReachable(), "it_vm NATS not reachable — NATS tier skipped");
        admin = Nats.connect(URL);
        deleteItStream();                 // fresh
        publisher = new NatsContactEventPublisher(cfg(), json);
        publisher.start();                // ensures IT_STREAM
    }

    @AfterEach
    void tearDown() throws Exception {
        if (publisher != null) publisher.stop();
        if (admin != null) {
            deleteItStream();
            admin.close();
        }
    }

    @Test
    void publishesCanonicalEventToTheOwnerSubjectAndDedups() throws Exception {
        ContactCard card = new ContactCard("p:42", "Alice", "Ali", "vip",
                List.of(Handle.phone("+8801711000001")));
        ContactEvent event = ContactEvent.upsert("c:abc", "p:42", "phonebook", 7, card);

        publisher.publish("p:42", event);

        JetStreamManagement jsm = admin.jetStreamManagement();
        MessageInfo mi = jsm.getLastMessage(IT_STREAM, IT_PREFIX + "p_42");   // ":" sanitized to "_"
        JsonNode j = json.readTree(mi.getData());
        assertEquals("contactUpsert", j.get("type").asText());
        assertEquals("c:abc", j.get("contactId").asText());
        assertEquals("Alice", j.get("card").get("fullName").asText());
        assertEquals(3, j.get("card").get("handles").get(0).get("caps").asInt());

        long before = jsm.getStreamInfo(IT_STREAM).getStreamState().getMsgCount();
        publisher.publish("p:42", event);                                    // same Nats-Msg-Id
        long after = jsm.getStreamInfo(IT_STREAM).getStreamState().getMsgCount();
        assertEquals(before, after, "a duplicate Nats-Msg-Id must not add a second message");
    }

    private void deleteItStream() {
        try {
            admin.jetStreamManagement().deleteStream(IT_STREAM);
        } catch (Exception ignore) {
            // not there yet — fine
        }
    }
}

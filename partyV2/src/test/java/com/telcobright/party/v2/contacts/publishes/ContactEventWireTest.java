package com.telcobright.party.v2.contacts.publishes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telcobright.party.v2.contacts.spi.Handle;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks the CANONICAL contact-event wire JSON (architect §8 canon 2026-06-14,
 * = the d/f client parse shape). If a field name or encoding drifts, this fails
 * before it reaches NATS and the device.
 */
class ContactEventWireTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void contactUpsertSerializesToTheCanonicalShape() throws Exception {
        ContactCard card = new ContactCard("p:42", "Alice", "Ali", "vip",
                List.of(Handle.phone("+8801711000001"), Handle.email("a@b.com")));
        ContactEvent event = ContactEvent.upsert("c:abc", "p:42", "phonebook", 7, card);

        JsonNode j = om.readTree(om.writeValueAsString(event));

        assertEquals("contactUpsert", j.get("type").asText());
        assertEquals("c:abc", j.get("contactId").asText());
        assertEquals("p:42", j.get("personId").asText());
        assertEquals("phonebook", j.get("source").asText());
        assertEquals(7, j.get("version").asInt());
        assertFalse(j.has("ownerPersonId"), "owner is implied by the subject, never in the body");
        assertFalse(j.has("originId"), "optional reconcile key is OMITTED when the producer minted none");

        JsonNode c = j.get("card");
        assertEquals("p:42", c.get("uid").asText());
        assertEquals("Alice", c.get("fullName").asText());
        assertEquals("Ali", c.get("label").asText());
        assertEquals("vip", c.get("note").asText());
        assertFalse(c.has("name"), "renamed to fullName");
        assertFalse(c.has("petname"), "renamed to label");
        assertFalse(c.has("groups"), "dropped per the canon");
        assertFalse(c.has("photo"), "dropped per the canon");

        JsonNode phone = c.get("handles").get(0);
        assertEquals("phone", phone.get("kind").asText());
        assertEquals("+8801711000001", phone.get("value").asText());
        assertEquals(3, phone.get("caps").asInt());          // chat|voice bitset
        assertFalse(phone.has("capabilities"), "encoded as the caps bitset, not a string list");
        assertFalse(phone.has("tel"), "isPhone() must not serialize");

        JsonNode email = c.get("handles").get(1);
        assertEquals("email", email.get("kind").asText());
        assertEquals(0, email.get("caps").asInt());
    }

    @Test
    void contactUpsertEchoesOriginIdWhenPresent() throws Exception {
        ContactCard card = new ContactCard(null, "Alice", null, null, List.of(Handle.phone("+8801711000001")));
        ContactEvent event = ContactEvent.upsert("c:abc", null, "manual", 3, card, "o:dev-7");

        JsonNode j = om.readTree(om.writeValueAsString(event));

        assertEquals("o:dev-7", j.get("originId").asText());   // §8 RULING B — echoed for the device to reconcile
    }

    @Test
    void contactDeleteHasNoCardAndNoPerson() throws Exception {
        ContactEvent event = ContactEvent.delete("c:abc", "manual", 9);

        JsonNode j = om.readTree(om.writeValueAsString(event));

        assertEquals("contactDelete", j.get("type").asText());
        assertEquals("c:abc", j.get("contactId").asText());
        assertEquals("manual", j.get("source").asText());
        assertEquals(9, j.get("version").asInt());
        assertTrue(j.get("card").isNull());
        assertTrue(j.get("personId").isNull());
        assertFalse(j.has("originId"), "DELETE path unaffected (RULING B) — no reconcile key");
    }
}

package com.telcobright.party.v2.contacts.internal;

import com.telcobright.party.v2.contacts.api.emit.ContactsChanged;
import com.telcobright.party.v2.contacts.internal.ContactStore.ContactRow;
import com.telcobright.party.v2.model.E164;
import com.telcobright.party.v2.model.ProviderException;
import com.telcobright.party.v2.providers.odoo.OdooFacadeClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The contact-graph pipeline (frozen §6). Rows are one-directional
 * (owner → contact); mutuality is emergent, never enforced.
 */
@ApplicationScoped
public class ContactsService {

    private static final Logger LOG = Logger.getLogger(ContactsService.class);

    public record Match(String e164, String jid, String displayName) {}
    public record SyncResult(List<Match> matches, List<String> nonUsers) {}
    public record Delta(List<ContactRow> contacts, long nextCursor) {}

    @Inject ContactStore store;
    @Inject OdooFacadeClient odoo;
    @Inject InviteSender inviteSender;
    @Inject ContactsConfig cfg;
    @Inject Event<ContactsChanged> changed;

    @ConfigProperty(name = "party.v2.registration.xmpp.domain", defaultValue = "localhost")
    String xmppDomain;

    /** JID only for ACTIVE rows — a non-user (INVITED) contact has none. */
    public String jidIfActive(ContactRow row) {
        return ContactStore.ACTIVE.equals(row.state())
                ? E164.digits(row.contactE164()) + "@" + xmppDomain
                : null;
    }

    /** Read-only phonebook match — who of these numbers is on the app. */
    public SyncResult sync(List<String> numbers) {
        Set<String> normalized = normalizeLenient(numbers);
        Map<String, OdooFacadeClient.Facade> byE164 = facadesFor(List.copyOf(normalized));
        List<Match> matches = new ArrayList<>();
        List<String> nonUsers = new ArrayList<>();
        for (String e164 : normalized) {
            OdooFacadeClient.Facade f = byE164.get(e164);
            if (f != null && "active".equals(f.status())) {
                matches.add(new Match(f.e164(), f.jid(), f.displayName()));
            } else {
                nonUsers.add(e164);
            }
        }
        return new SyncResult(matches, nonUsers);
    }

    /** No cursor: full snapshot (no tombstones) + fresh cursor — the rebase path. */
    public Delta snapshot(String owner) {
        return new Delta(store.snapshot(owner), store.highSeq(owner));
    }

    /** Delta strictly after the presented cursor, tombstones included. */
    public Delta since(String owner, long cursor) {
        long high = store.highSeq(owner);
        if (cursor < 0 || cursor > high) {
            throw Denied.gone("stale or invalid cursor — refetch the snapshot");
        }
        List<ContactRow> rows = store.listSince(owner, cursor);
        return new Delta(rows, high);
    }

    /** PUT: ACTIVE if a facade exists, INVITED otherwise; BLOCKED stays BLOCKED (petname-only). */
    public ContactRow put(String owner, String contactPhone, String petname) {
        String contact = normalizeOrDeny(contactPhone);
        requireNotSelf(owner, contact);
        String state = resolvePutState(owner, contact);
        long seq = store.upsertWithNextSeq(owner, contact, petname, state, petname == null);
        emitChanged(owner, seq);
        return store.find(owner, contact).orElseThrow();
    }

    /** Tombstone — the row is kept (state DELETED), so deltas propagate the removal. */
    public void delete(String owner, String contactPhone) {
        String contact = normalizeOrDeny(contactPhone);
        if (store.find(owner, contact).isEmpty()) {
            return; // idempotent: deleting a never-contact is a no-op
        }
        long seq = store.upsertWithNextSeq(owner, contact, null, ContactStore.DELETED, true);
        emitChanged(owner, seq);
    }

    /** Blocking may target any number (WhatsApp shape), contact row or not. */
    public void block(String owner, String contactPhone) {
        String contact = normalizeOrDeny(contactPhone);
        requireNotSelf(owner, contact);
        long seq = store.upsertWithNextSeq(owner, contact, null, ContactStore.BLOCKED, true);
        emitChanged(owner, seq);
    }

    /** Unblock returns the row to ACTIVE/INVITED by facade existence; no-op if not blocked. */
    public void unblock(String owner, String contactPhone) {
        String contact = normalizeOrDeny(contactPhone);
        Optional<ContactRow> row = store.find(owner, contact);
        if (row.isEmpty() || !ContactStore.BLOCKED.equals(row.get().state())) {
            return;
        }
        String state = stateByFacadeExistence(contact);
        long seq = store.upsertWithNextSeq(owner, contact, null, state, true);
        emitChanged(owner, seq);
    }

    /** 202 fire-and-forget; dev mode logs (SMS gateway later — we ARE the operator). */
    public void invite(String owner, String toPhone) {
        String to = normalizeOrDeny(toPhone);
        try {
            inviteSender.invite(owner, to);
        } catch (IllegalStateException e) {
            LOG.error("invite delivery unavailable: " + e.getMessage());
            throw Denied.unavailable("invite delivery unavailable");
        }
    }

    // ── named steps ───────────────────────────────────────────────────────

    private Set<String> normalizeLenient(List<String> numbers) {
        Set<String> out = new LinkedHashSet<>();
        for (String n : numbers == null ? List.<String>of() : numbers) {
            try {
                out.add(E164.normalize(n));
            } catch (IllegalArgumentException ignored) {
                // unparseable phonebook noise — skip, don't fail the sync
            }
        }
        return out;
    }

    private Map<String, OdooFacadeClient.Facade> facadesFor(List<String> e164s) {
        try {
            Map<String, OdooFacadeClient.Facade> byE164 = new HashMap<>();
            for (OdooFacadeClient.Facade f : odoo.searchByE164In(e164s)) {
                byE164.put(f.e164(), f);
            }
            return byE164;
        } catch (ProviderException e) {
            LOG.error("facade lookup failed: " + e.getMessage());
            throw Denied.unavailable("contact match unavailable");
        }
    }

    private String resolvePutState(String owner, String contact) {
        Optional<ContactRow> existing = store.find(owner, contact);
        if (existing.isPresent() && ContactStore.BLOCKED.equals(existing.get().state())) {
            return ContactStore.BLOCKED; // PUT never silently unblocks
        }
        return stateByFacadeExistence(contact);
    }

    private String stateByFacadeExistence(String contact) {
        try {
            return odoo.findByE164(contact).isPresent() ? ContactStore.ACTIVE : ContactStore.INVITED;
        } catch (ProviderException e) {
            LOG.error("facade lookup failed: " + e.getMessage());
            throw Denied.unavailable("contact resolution unavailable");
        }
    }

    private static String normalizeOrDeny(String phone) {
        try {
            return E164.normalize(phone);
        } catch (IllegalArgumentException e) {
            throw Denied.badRequest("not a valid E.164 phone number");
        }
    }

    private static void requireNotSelf(String owner, String contact) {
        if (owner.equals(contact)) {
            throw Denied.badRequest("cannot add yourself");
        }
    }

    private void emitChanged(String owner, long seq) {
        LOG.infof("ContactsChanged e164=%s seq=%d", owner, seq);
        changed.fire(new ContactsChanged(owner, seq));
    }
}

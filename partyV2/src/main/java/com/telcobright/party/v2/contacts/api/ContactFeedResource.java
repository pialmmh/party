package com.telcobright.party.v2.contacts.api;

import com.telcobright.party.v2.contacts.internal.Denied;
import com.telcobright.party.v2.contacts.internal.OwnerResolver;
import com.telcobright.party.v2.contacts.internal.normalize.ContactNormalizer;
import com.telcobright.party.v2.contacts.internal.normalize.ContactNormalizer.IngestResult;
import com.telcobright.party.v2.contacts.publishes.ContactCard;
import com.telcobright.party.v2.contacts.spi.ContactEntryStore;
import com.telcobright.party.v2.contacts.spi.ContactEntryStore.Snapshot;
import com.telcobright.party.v2.contacts.spi.ContactSource;
import com.telcobright.party.v2.contacts.spi.Handle;
import com.telcobright.party.v2.contacts.spi.PartyDirectory;
import com.telcobright.party.v2.contacts.spi.RawContact;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/**
 * The event-driven contact feed (the evolved contacts API beside the frozen §6
 * graph). The client posts contacts here; the normalizer turns each into one
 * canonical event on the owner's per-account feed, and the snapshot is the
 * initial-sync read. Owner identity = the §2 device JWT (or the dev header),
 * resolved to the owner's personId.
 *
 *   POST /contacts/entry    { fullName, handles[], label?, note? } -> { contactId, version, changed }
 *   GET  /contacts/snapshot                                        -> { contacts[], cursor }
 *   POST /contacts/phonebook  (zip)  -> 202 { jobId }   // 🚧 next slice (async import)
 */
@Path("/contacts")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ContactFeedResource {

    @Inject ContactNormalizer normalizer;
    @Inject ContactEntryStore entries;
    @Inject OwnerResolver owner;
    @Inject PartyDirectory directory;

    public record AddContactRequest(String fullName, List<String> handles, String label, String note) {}

    public record AddContactResponse(String contactId, long version, boolean changed) {}

    /** A snapshot element: the canonical card flattened with its entry metadata (architect §8). */
    public record SnapshotCard(String contactId, String personId, String source, long version,
                               String uid, String fullName, String label, String note,
                               List<Handle> handles) {}

    public record SnapshotResponse(String owner, List<SnapshotCard> contacts, long cursor) {}

    @POST
    @Path("/entry")
    public AddContactResponse add(@HeaderParam("Authorization") String auth,
                                  @HeaderParam("X-SL-Account") String devAccount,
                                  AddContactRequest req) {
        if (req == null) throw Denied.badRequest("missing body");
        String me = ownerPersonId(auth, devAccount);
        RawContact raw = new RawContact(req.fullName(), req.handles(), req.label(), req.note());
        IngestResult result = normalizer.ingest(me, raw, ContactSource.MANUAL)
                .orElseThrow(() -> Denied.badRequest("no usable handle — give a phone number or an email"));
        return new AddContactResponse(result.contactId(), result.version(), result.changed());
    }

    @GET
    @Path("/snapshot")
    public SnapshotResponse snapshot(@HeaderParam("Authorization") String auth,
                                     @HeaderParam("X-SL-Account") String devAccount) {
        String me = ownerPersonId(auth, devAccount);
        Snapshot snap = entries.snapshot(me);
        List<SnapshotCard> contacts = snap.rows().stream().map(ContactFeedResource::toSnapshotCard).toList();
        // owner = the device's own personId, so it can then subscribe its live feed sl.contacts.<owner>
        return new SnapshotResponse(me, contacts, snap.cursor());
    }

    private static SnapshotCard toSnapshotCard(ContactEntryStore.SnapshotRow row) {
        ContactCard card = row.card();
        return new SnapshotCard(row.contactId(), row.personId(), row.source(), row.version(),
                card.uid(), card.fullName(), card.label(), card.note(), card.handles());
    }

    /** The owner's E.164 (frozen §6 resolver) → their global personId. */
    private String ownerPersonId(String auth, String devAccount) {
        String e164 = owner.resolve(auth, devAccount);
        return directory.resolve(Handle.phone(e164))
                .map(PartyDirectory.PersonRef::personId)
                .orElseThrow(() -> Denied.unauthorized("owner is not a provisioned person"));
    }
}

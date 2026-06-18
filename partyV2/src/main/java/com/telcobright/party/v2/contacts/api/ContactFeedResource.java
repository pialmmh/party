package com.telcobright.party.v2.contacts.api;

import com.telcobright.party.v2.contacts.internal.Denied;
import com.telcobright.party.v2.contacts.internal.OwnerResolver;
import com.telcobright.party.v2.contacts.internal.normalize.ContactNormalizer;
import com.telcobright.party.v2.contacts.internal.normalize.ContactNormalizer.IngestResult;
import com.telcobright.party.v2.contacts.publishes.ContactCard;
import com.telcobright.party.v2.contacts.spi.ContactEntryStore;
import com.telcobright.party.v2.contacts.spi.ContactSource;
import com.telcobright.party.v2.sync.SyncConfig;
import com.telcobright.party.v2.contacts.spi.Handle;
import com.telcobright.party.v2.contacts.spi.PartyDirectory;
import com.telcobright.party.v2.contacts.spi.RawContact;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/**
 * The event-driven contact feed (the evolved contacts API beside the frozen §6
 * graph). The client posts contacts here; the normalizer turns each into one
 * canonical event on the owner's per-account feed, and the snapshot is the
 * initial-sync read. Owner identity = the §2 device JWT (or the dev header),
 * resolved to the owner's personId.
 *
 *   POST   /contacts/entry    { fullName, handles[], label?, note?, originId? } -> { contactId, version, changed }
 *   DELETE /contacts/entry/{contactId}?source=manual|phonebook       -> { contactId, version, changed }  (tombstone)
 *   GET    /contacts/snapshot?cursor=&limit=  -> { owner, contacts[], cursor, nextCursor }  (paged at core.sync.batchSize)
 *   POST   /contacts/phonebook  (zip)  -> 202 { jobId }   // 🚧 NOT BUILT (deferred — client bulk wire-format unpinned)
 */
@Path("/contacts")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ContactFeedResource {

    @Inject ContactNormalizer normalizer;
    @Inject ContactEntryStore entries;
    @Inject OwnerResolver owner;
    @Inject PartyDirectory directory;
    @Inject SyncConfig sync;

    // originId = the OPTIONAL client-minted reconcile key (§8 RULING B); echoed on the published event.
    public record AddContactRequest(String fullName, List<String> handles, String label, String note,
                                    String originId) {}

    public record AddContactResponse(String contactId, long version, boolean changed) {}

    public record DeleteContactResponse(String contactId, long version, boolean changed) {}

    /** A snapshot element: the canonical card flattened with its entry metadata (architect §8). */
    public record SnapshotCard(String contactId, String personId, String source, long version,
                               String uid, String fullName, String label, String note,
                               List<Handle> handles) {}

    public record SnapshotResponse(String owner, List<SnapshotCard> contacts, long cursor, String nextCursor) {}

    @POST
    @Path("/entry")
    public AddContactResponse add(@HeaderParam("Authorization") String auth,
                                  @HeaderParam("X-SL-Account") String devAccount,
                                  AddContactRequest req) {
        if (req == null) throw Denied.badRequest("missing body");
        String me = ownerPersonId(auth, devAccount);
        RawContact raw = new RawContact(req.fullName(), req.handles(), req.label(), req.note(), req.originId());
        IngestResult result = normalizer.ingest(me, raw, ContactSource.MANUAL)
                .orElseThrow(() -> Denied.badRequest("no usable handle — give a phone number or an email"));
        return new AddContactResponse(result.contactId(), result.version(), result.changed());
    }

    @GET
    @Path("/snapshot")
    public SnapshotResponse snapshot(@HeaderParam("Authorization") String auth,
                                     @HeaderParam("X-SL-Account") String devAccount,
                                     @QueryParam("cursor") String pageCursor,
                                     @QueryParam("limit") Integer limit) {
        String me = ownerPersonId(auth, devAccount);
        // Paged at core.sync.batchSize (frozen §7): the common ≤batch owner gets one page
        // (nextCursor=null); a >batch owner pages by passing the previous nextCursor back.
        ContactEntryStore.Page page = entries.snapshotPage(me, pageCursor, pageSize(limit));
        List<SnapshotCard> contacts = page.rows().stream().map(ContactFeedResource::toSnapshotCard).toList();
        // owner = the device's own personId, so it can then subscribe its live feed sl.contacts.<owner>;
        // cursor = the WS resume seq; nextCursor != null means "more pages — fetch again".
        return new SnapshotResponse(me, contacts, page.cursor(), page.nextCursor());
    }

    /** Page size = the requested limit clamped to [1, batchSize]; absent = batchSize (§7). */
    private int pageSize(Integer requested) {
        int max = sync.batchSize();
        return requested == null ? max : Math.max(1, Math.min(requested, max));
    }

    @DELETE
    @Path("/entry/{contactId}")
    public DeleteContactResponse delete(@HeaderParam("Authorization") String auth,
                                        @HeaderParam("X-SL-Account") String devAccount,
                                        @PathParam("contactId") String contactId,
                                        @QueryParam("source") @DefaultValue("manual") String source) {
        String me = ownerPersonId(auth, devAccount);
        ContactSource src = parseSource(source);
        // Idempotent (frozen §8): a missing or already-tombstoned entry stores nothing and emits
        // nothing -> {changed:false}. A live entry is tombstoned -> ONE contactDelete on SL_CONTACTS.
        return normalizer.remove(me, contactId, src)
                .map(version -> new DeleteContactResponse(contactId, version, true))
                .orElseGet(() -> new DeleteContactResponse(contactId, 0L, false));
    }

    private static ContactSource parseSource(String source) {
        try {
            return ContactSource.from(source);
        } catch (IllegalArgumentException e) {
            throw Denied.badRequest("invalid source (use manual|phonebook): " + source);
        }
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

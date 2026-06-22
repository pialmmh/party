package com.telcobright.party.v2.threads.api;

import com.telcobright.party.v2.contacts.internal.Denied;
import com.telcobright.party.v2.contacts.internal.OwnerResolver;
import com.telcobright.party.v2.contacts.spi.Handle;
import com.telcobright.party.v2.contacts.spi.PartyDirectory;
import com.telcobright.party.v2.sync.SyncConfig;
import com.telcobright.party.v2.threads.internal.ThreadAction;
import com.telcobright.party.v2.threads.internal.ThreadService;
import com.telcobright.party.v2.threads.spi.ThreadEntryStore;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/**
 * The thread overlay feed — the 3rd NATS sync datatype beside contacts (frozen §8b).
 * The client posts one thread ACTION here; the service turns it into one canonical
 * {@link com.telcobright.party.v2.threads.publishes.ThreadEvent} on the owner's
 * per-account feed, and the snapshot is the initial-sync read. Owner identity =
 * the §2 device JWT (or the dev {@code X-SL-Account} header) → personId, exactly
 * like contacts (§8 account-identity: HTTP authenticates by E.164).
 *
 *   POST /threads/entry    { type, conversationId, originId?, readUpTo? } -> { conversationId, version, changed }
 *   GET  /threads/snapshot ?cursor=&limit=  -> { owner, threads[], cursor, nextCursor }   (paged at core.sync.batchSize)
 */
@Path("/threads")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ThreadFeedResource {

    @Inject ThreadService service;
    @Inject ThreadEntryStore entries;
    @Inject OwnerResolver owner;
    @Inject PartyDirectory directory;
    @Inject SyncConfig sync;

    // originId = the OPTIONAL client-minted reconcile key; echoed on the published event.
    public record ThreadActionRequest(String type, String conversationId, String originId, String readUpTo) {}

    public record ThreadActionResponse(String conversationId, long version, boolean changed) {}

    /** One thread overlay in a snapshot (tombstones included: deleted=true). */
    public record ThreadRow(String conversationId, boolean archived, boolean pinned, boolean muted,
                            boolean deleted, String readUpTo, long version) {}

    public record SnapshotResponse(String owner, List<ThreadRow> threads, long cursor, String nextCursor) {}

    @POST
    @Path("/entry")
    public ThreadActionResponse entry(@HeaderParam("Authorization") String auth,
                                      @HeaderParam("X-SL-Account") String devAccount,
                                      ThreadActionRequest req) {
        if (req == null) throw Denied.badRequest("missing body");
        if (req.conversationId() == null || req.conversationId().isBlank()) {
            throw Denied.badRequest("missing conversationId");
        }
        ThreadAction action;
        try {
            action = ThreadAction.from(req.type());
        } catch (IllegalArgumentException e) {
            throw Denied.badRequest("invalid type (use threadArchive|…|threadRead): " + req.type());
        }
        if (action.isRead() && (req.readUpTo() == null || req.readUpTo().isBlank())) {
            throw Denied.badRequest("threadRead requires readUpTo");
        }
        String me = ownerPersonId(auth, devAccount);
        ThreadService.Applied a = service.apply(me, action, req.conversationId(), req.originId(), req.readUpTo());
        return new ThreadActionResponse(a.conversationId(), a.version(), a.changed());
    }

    @GET
    @Path("/snapshot")
    public SnapshotResponse snapshot(@HeaderParam("Authorization") String auth,
                                     @HeaderParam("X-SL-Account") String devAccount,
                                     @QueryParam("cursor") String pageCursor,
                                     @QueryParam("limit") Integer limit) {
        String me = ownerPersonId(auth, devAccount);
        // Paged at core.sync.batchSize (frozen §7): a ≤batch owner gets one page (nextCursor=null).
        ThreadEntryStore.Page page = entries.snapshotPage(me, pageCursor, pageSize(limit));
        List<ThreadRow> threads = page.rows().stream().map(ThreadFeedResource::toRow).toList();
        // cursor = the WS resume seq; nextCursor != null means "more pages — fetch again".
        return new SnapshotResponse(me, threads, page.cursor(), page.nextCursor());
    }

    /** Page size = the requested limit clamped to [1, batchSize]; absent = batchSize (§7). */
    private int pageSize(Integer requested) {
        int max = sync.batchSize();
        return requested == null ? max : Math.max(1, Math.min(requested, max));
    }

    private static ThreadRow toRow(ThreadEntryStore.SnapshotRow r) {
        return new ThreadRow(r.conversationId(), r.archived(), r.pinned(), r.muted(),
                r.deleted(), r.readUpTo(), r.version());
    }

    /** The owner's E.164 (§6 resolver) → their global personId. */
    private String ownerPersonId(String auth, String devAccount) {
        String e164 = owner.resolve(auth, devAccount);
        return directory.resolve(Handle.phone(e164))
                .map(PartyDirectory.PersonRef::personId)
                .orElseThrow(() -> Denied.unauthorized("owner is not a provisioned person"));
    }
}

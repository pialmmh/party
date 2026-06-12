package com.telcobright.party.v2.contacts.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.telcobright.party.v2.contacts.internal.match.ContactMatcher;
import com.telcobright.party.v2.contacts.spi.ContactStore.ContactRow;
import com.telcobright.party.v2.contacts.internal.ContactsService;
import com.telcobright.party.v2.contacts.internal.Denied;
import com.telcobright.party.v2.contacts.internal.OwnerResolver;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * Frozen §6 contact graph (Quarkus root /api/v1). Owner identity = Bearer
 * device-JWT (jid claim) or the X-SL-Account dev header — never a query param.
 *
 *   POST   /contacts/sync { numbers[] }      -> { matches[], nonUsers[] }
 *   GET    /contacts[?since=cursor]          -> { contacts[], nextCursor }   // delta | snapshot; stale -> 410
 *   PUT    /contacts/{e164} { petname? }     -> { contact }                  // ACTIVE if facade else INVITED
 *   DELETE /contacts/{e164}                  -> 204                          // tombstone
 *   POST   /contacts/{e164}/block|/unblock   -> 204
 */
@Path("/contacts")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ContactsResource {

    @Inject ContactsService service;
    @Inject ContactMatcher matcher;
    @Inject OwnerResolver owner;

    public record SyncRequest(List<String> numbers) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ContactDto(String e164, String jid, String petname, String state, long seq) {}

    public record DeltaResponse(List<ContactDto> contacts, long nextCursor) {}

    public record PutRequest(String petname) {}

    public record PutResponse(ContactDto contact) {}

    @POST
    @Path("/sync")
    public ContactMatcher.SyncResult sync(@HeaderParam("Authorization") String auth,
                                          @HeaderParam("X-SL-Account") String devAccount,
                                          SyncRequest req) {
        owner.resolve(auth, devAccount); // authn only — sync reads no owner rows
        if (req == null) throw Denied.badRequest("missing body");
        return matcher.match(req.numbers());
    }

    @GET
    public DeltaResponse list(@HeaderParam("Authorization") String auth,
                              @HeaderParam("X-SL-Account") String devAccount,
                              @QueryParam("since") String since) {
        String me = owner.resolve(auth, devAccount);
        ContactsService.Delta delta = (since == null || since.isBlank())
                ? service.snapshot(me)
                : service.since(me, parseCursorOrGone(since));
        return new DeltaResponse(delta.contacts().stream().map(this::toDto).toList(),
                delta.nextCursor());
    }

    @PUT
    @Path("/{e164}")
    public PutResponse put(@HeaderParam("Authorization") String auth,
                           @HeaderParam("X-SL-Account") String devAccount,
                           @PathParam("e164") String e164,
                           PutRequest req) {
        String me = owner.resolve(auth, devAccount);
        ContactRow row = service.put(me, e164, req == null ? null : req.petname());
        return new PutResponse(toDto(row));
    }

    @DELETE
    @Path("/{e164}")
    public Response delete(@HeaderParam("Authorization") String auth,
                           @HeaderParam("X-SL-Account") String devAccount,
                           @PathParam("e164") String e164) {
        service.delete(owner.resolve(auth, devAccount), e164);
        return Response.noContent().build();
    }

    @POST
    @Path("/{e164}/block")
    public Response block(@HeaderParam("Authorization") String auth,
                          @HeaderParam("X-SL-Account") String devAccount,
                          @PathParam("e164") String e164) {
        service.block(owner.resolve(auth, devAccount), e164);
        return Response.noContent().build();
    }

    @POST
    @Path("/{e164}/unblock")
    public Response unblock(@HeaderParam("Authorization") String auth,
                            @HeaderParam("X-SL-Account") String devAccount,
                            @PathParam("e164") String e164) {
        service.unblock(owner.resolve(auth, devAccount), e164);
        return Response.noContent().build();
    }

    // ── internals ─────────────────────────────────────────────────────────

    private static long parseCursorOrGone(String since) {
        try {
            return Long.parseLong(since);
        } catch (NumberFormatException e) {
            throw Denied.gone("stale or invalid cursor — refetch the snapshot");
        }
    }

    private ContactDto toDto(ContactRow row) {
        return new ContactDto(row.contactE164(), service.jidIfActive(row),
                row.petname(), row.state(), row.seq());
    }
}

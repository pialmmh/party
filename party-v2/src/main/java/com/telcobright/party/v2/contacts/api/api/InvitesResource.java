package com.telcobright.party.v2.contacts.api.api;

import com.telcobright.party.v2.contacts.internal.ContactsService;
import com.telcobright.party.v2.contacts.internal.Denied;
import com.telcobright.party.v2.contacts.internal.OwnerResolver;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Frozen §6 invites:
 *
 *   POST /api/v1/invites { e164 } -> 202    // dev-mode logs; operator SMS gateway later
 */
@Path("/invites")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class InvitesResource {

    @Inject ContactsService service;
    @Inject OwnerResolver owner;

    public record InviteRequest(String e164) {}

    @POST
    public Response invite(@HeaderParam("Authorization") String auth,
                           @HeaderParam("X-SL-Account") String devAccount,
                           InviteRequest req) {
        String me = owner.resolve(auth, devAccount);
        if (req == null) throw Denied.badRequest("missing body");
        service.invite(me, req.e164());
        return Response.accepted().build();
    }
}

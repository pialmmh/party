package com.telcobright.party.api.resource;

import com.telcobright.party.master.entity.AuthUser;
import com.telcobright.party.master.entity.IpAccessRule;
import com.telcobright.party.master.entity.UiMenuPermission;
import com.telcobright.party.master.service.AuthUserService;
import com.telcobright.party.master.service.IpAccessRuleService;
import com.telcobright.party.master.service.UiMenuPermissionService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/tenants/{tenantId}")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthUserResource {

    @Inject AuthUserService users;
    @Inject IpAccessRuleService ipRules;
    @Inject UiMenuPermissionService menuPerms;

    public record PasswordRequest(String password) {}
    public record RolesRequest(List<Long> roleIds) {}
    public record IpRuleRequest(String ip, String permissionType) {}
    public record MenuPermRequest(String menuKey, String permissionLevel) {}

    @GET @Path("/users")
    public List<AuthUser> listAllByTenant(@PathParam("tenantId") Long tenantId) {
        return users.listByTenant(tenantId);
    }

    @GET @Path("/users/{id}")
    public AuthUser get(@PathParam("tenantId") Long tenantId, @PathParam("id") Long id) {
        return users.findById(tenantId, id);
    }

    @PATCH @Path("/users/{id}")
    public AuthUser update(@PathParam("tenantId") Long tenantId, @PathParam("id") Long id, AuthUser patch) {
        return users.update(tenantId, id, patch);
    }

    @POST @Path("/users/{id}/password")
    public void resetPassword(@PathParam("tenantId") Long tenantId, @PathParam("id") Long id, PasswordRequest r) {
        users.resetPassword(tenantId, id, r.password());
    }

    @POST @Path("/users/{id}/roles")
    public void setRoles(@PathParam("tenantId") Long tenantId, @PathParam("id") Long id, RolesRequest r) {
        users.replaceRoles(tenantId, id, r.roleIds());
    }

    @DELETE @Path("/users/{id}")
    public void delete(@PathParam("tenantId") Long tenantId, @PathParam("id") Long id) {
        users.delete(tenantId, id);
    }

    // ip rules
    @GET @Path("/users/{userId}/ip-rules")
    public List<IpAccessRule> listIpRules(@PathParam("tenantId") Long tenantId, @PathParam("userId") Long userId) {
        return ipRules.listForUser(tenantId, userId);
    }

    @POST @Path("/users/{userId}/ip-rules")
    public IpAccessRule addIpRule(@PathParam("tenantId") Long tenantId, @PathParam("userId") Long userId, IpRuleRequest r) {
        return ipRules.create(tenantId, userId, r.ip(), r.permissionType());
    }

    @DELETE @Path("/users/{userId}/ip-rules/{ruleId}")
    public void deleteIpRule(@PathParam("tenantId") Long tenantId,
                             @PathParam("userId") Long userId,
                             @PathParam("ruleId") Long ruleId) {
        ipRules.delete(tenantId, ruleId);
    }

    // menu permissions
    @GET @Path("/users/{userId}/menu-permissions")
    public List<UiMenuPermission> listMenuPerms(@PathParam("tenantId") Long tenantId, @PathParam("userId") Long userId) {
        return menuPerms.listForUser(tenantId, userId);
    }

    @PUT @Path("/users/{userId}/menu-permissions")
    public UiMenuPermission upsertMenuPerm(@PathParam("tenantId") Long tenantId,
                                           @PathParam("userId") Long userId,
                                           MenuPermRequest r) {
        return menuPerms.upsert(tenantId, userId, r.menuKey(), r.permissionLevel());
    }
}

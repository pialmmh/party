package com.telcobright.party.keycloak;

import org.keycloak.component.ComponentModel;
import org.keycloak.credential.CredentialInput;
import org.keycloak.credential.CredentialInputValidator;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.credential.PasswordCredentialModel;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.user.UserLookupProvider;
import org.keycloak.storage.user.UserQueryProvider;

import java.util.Map;
import java.util.stream.Stream;

/**
 * User Storage Provider that federates users from Party (v2 / stateless).
 *
 * Key idea: getUserByUsername does NOT make a remote call. It returns a "shell"
 * UserModel carrying only the username — that's enough for Keycloak to proceed to
 * credential validation. The shell is then populated by {@link #isValid} when
 * /v2/auth/validate returns a user record. By the time Keycloak issues a token,
 * the shell has email + displayName + roles attached.
 *
 * This lets Party stay stateless: it never has to expose a "find user without
 * password" endpoint, because Keycloak's flow always presents the password
 * before any profile data needs to be served.
 *
 * Caveat — if Keycloak is configured in NO_IMPORT federation mode and refreshes
 * a token outside of a password presentation, getUserById will return a shell
 * without profile data. Set the federation provider to IMPORT mode (or
 * UNSYNCED with cache) in the Keycloak realm so cached data survives.
 */
class PartyUserStorageProvider implements
        UserStorageProvider,
        UserLookupProvider,
        CredentialInputValidator,
        UserQueryProvider {

    private final KeycloakSession session;
    private final ComponentModel model;
    private final PartyClient client;

    PartyUserStorageProvider(KeycloakSession session, ComponentModel model, PartyClient client) {
        this.session = session;
        this.model = model;
        this.client = client;
    }

    // ── UserLookupProvider ────────────────────────────────────────────────

    @Override
    public UserModel getUserById(RealmModel realm, String keycloakId) {
        String externalUsername = StorageId.externalId(keycloakId);
        if (externalUsername == null || externalUsername.isBlank()) return null;
        return new PartyUserAdapter(session, realm, model, externalUsername);
    }

    @Override
    public UserModel getUserByUsername(RealmModel realm, String username) {
        if (username == null || username.isBlank()) return null;
        return new PartyUserAdapter(session, realm, model, username);
    }

    @Override
    public UserModel getUserByEmail(RealmModel realm, String email) {
        return getUserByUsername(realm, email);
    }

    // ── CredentialInputValidator ─────────────────────────────────────────

    @Override
    public boolean supportsCredentialType(String credentialType) {
        return PasswordCredentialModel.TYPE.equals(credentialType);
    }

    @Override
    public boolean isConfiguredFor(RealmModel realm, UserModel user, String credentialType) {
        return supportsCredentialType(credentialType);
    }

    @Override
    public boolean isValid(RealmModel realm, UserModel user, CredentialInput input) {
        if (!supportsCredentialType(input.getType())) return false;
        if (user == null || user.getUsername() == null) return false;

        PartyClient.ValidateResult res = client.validate(
                realm.getName(), user.getUsername(), input.getChallengeResponse());

        if (!res.valid) return false;

        // Attach the v2 profile to the same UserModel instance so subsequent reads
        // during this login round-trip (getEmail, getFirstName, role mappers) see it.
        if (res.user != null && user instanceof PartyUserAdapter pua) {
            pua.attachProfile(res.user);
        }
        return true;
    }

    // ── UserQueryProvider ────────────────────────────────────────────────

    @Override
    public int getUsersCount(RealmModel realm) {
        return 0;  // unknown — Party is stateless, no admin-side count available
    }

    @Override
    public Stream<UserModel> searchForUserStream(RealmModel realm, String search,
                                                 Integer firstResult, Integer maxResults) {
        // v2 has no admin search path (Party doesn't expose one).  Listing
        // users in the Keycloak admin console would require admin credentials
        // on the underlying repo (Odoo etc.) — out of scope for v0.
        return Stream.empty();
    }

    @Override
    public Stream<UserModel> searchForUserStream(RealmModel realm, Map<String, String> params,
                                                 Integer firstResult, Integer maxResults) {
        return Stream.empty();
    }

    @Override
    public Stream<UserModel> getGroupMembersStream(RealmModel realm, GroupModel group,
                                                   Integer firstResult, Integer maxResults) {
        return Stream.empty();
    }

    @Override
    public Stream<UserModel> searchForUserByUserAttributeStream(RealmModel realm,
                                                                String attrName, String attrValue) {
        return Stream.empty();
    }

    @Override
    public void close() { /* http client is long-lived; nothing to do per call */ }
}

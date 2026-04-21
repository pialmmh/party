package com.telcobright.party.keycloak;

import com.fasterxml.jackson.databind.JsonNode;
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

    // ---------- UserLookupProvider ----------

    @Override
    public UserModel getUserById(RealmModel realm, String keycloakId) {
        String externalId = StorageId.externalId(keycloakId);
        return client.findById(realm.getName(), externalId)
                .map(p -> toUser(realm, p))
                .orElse(null);
    }

    @Override
    public UserModel getUserByUsername(RealmModel realm, String username) {
        return client.findByUsername(realm.getName(), username)
                .map(p -> toUser(realm, p))
                .orElse(null);
    }

    @Override
    public UserModel getUserByEmail(RealmModel realm, String email) {
        return getUserByUsername(realm, email);
    }

    // ---------- CredentialInputValidator ----------

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
        return client.validateCredentials(realm.getName(), user.getUsername(), input.getChallengeResponse());
    }

    // ---------- UserQueryProvider ----------

    @Override
    public int getUsersCount(RealmModel realm) { return 0; /* unknown — Party doesn't count */ }

    @Override
    public Stream<UserModel> searchForUserStream(RealmModel realm, String search,
                                                 Integer firstResult, Integer maxResults) {
        int first = firstResult == null ? 0 : firstResult;
        int max = maxResults == null ? 20 : maxResults;
        return client.search(realm.getName(), search, first, max)
                .map(arr -> {
                    Stream.Builder<UserModel> b = Stream.builder();
                    if (arr.isArray()) arr.forEach(p -> b.add(toUser(realm, p)));
                    return b.build();
                })
                .orElse(Stream.empty());
    }

    @Override
    public Stream<UserModel> searchForUserStream(RealmModel realm, Map<String, String> params,
                                                 Integer firstResult, Integer maxResults) {
        String q = params.getOrDefault(UserModel.SEARCH, "");
        return searchForUserStream(realm, q, firstResult, maxResults);
    }

    @Override
    public Stream<UserModel> getGroupMembersStream(RealmModel realm, GroupModel group,
                                                   Integer firstResult, Integer maxResults) {
        return Stream.empty();
    }

    @Override
    public Stream<UserModel> searchForUserByUserAttributeStream(RealmModel realm, String attrName, String attrValue) {
        return Stream.empty();
    }

    // ---------- adapter ----------

    private UserModel toUser(RealmModel realm, JsonNode payload) {
        PartyUserAdapter adapter = new PartyUserAdapter(session, realm, model, payload);
        // Keycloak requires a storage-prefixed id; override via setId-style — AbstractUserAdapter handles this
        // by using storageModel.getId() + partyId via StorageId.keycloakId.
        return new PartyStorageIdWrapper(adapter, model.getId(), adapter.partyId());
    }

    @Override
    public void close() { /* http client is long-lived; nothing to do per call */ }
}

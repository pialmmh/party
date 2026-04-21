package com.telcobright.party.keycloak;

import org.keycloak.models.UserModel;
import org.keycloak.models.utils.UserModelDelegate;
import org.keycloak.storage.StorageId;

/**
 * Wraps {@link PartyUserAdapter} and overrides {@link #getId()} to produce
 * Keycloak's canonical {@code f:<providerId>:<partyId>} format.
 */
class PartyStorageIdWrapper extends UserModelDelegate {

    private final String keycloakId;

    PartyStorageIdWrapper(UserModel delegate, String providerId, String partyId) {
        super(delegate);
        this.keycloakId = new StorageId(providerId, partyId).getId();
    }

    @Override
    public String getId() { return keycloakId; }
}

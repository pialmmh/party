package com.telcobright.party.keycloak.internal;

import com.telcobright.party.keycloak.spi.PartyClient;
import org.junit.jupiter.api.Test;
import org.keycloak.credential.CredentialInput;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.credential.PasswordCredentialModel;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The provider behind a FAKE PartyClient + mocked KC models — no live Keycloak. */
class PartyUserStorageProviderTest {

    record Call(String realm, String login, String password) {}

    static final class FakePartyClient implements PartyClient {
        final List<Call> calls = new ArrayList<>();
        ValidateResult answer = ValidateResult.deny("unset", null);

        @Override public ValidateResult validate(String realmName, String login, String password) {
            calls.add(new Call(realmName, login, password));
            return answer;
        }
    }

    private final FakePartyClient party = new FakePartyClient();
    private final PartyUserStorageProvider provider =
            new PartyUserStorageProvider(null, null, party);

    private RealmModel realm(String name) {
        RealmModel r = mock(RealmModel.class);
        when(r.getName()).thenReturn(name);
        return r;
    }

    private UserModel user(String username) {
        UserModel u = mock(UserModel.class);
        when(u.getUsername()).thenReturn(username);
        return u;
    }

    private CredentialInput password(String secret) {
        CredentialInput in = mock(CredentialInput.class);
        when(in.getType()).thenReturn(PasswordCredentialModel.TYPE);
        when(in.getChallengeResponse()).thenReturn(secret);
        return in;
    }

    @Test
    void supportsOnlyPasswordCredentials() {
        assertTrue(provider.supportsCredentialType(PasswordCredentialModel.TYPE));
        assertFalse(provider.supportsCredentialType("otp"));
    }

    @Test
    void isValid_passesRealmLoginAndPasswordToParty() {
        party.answer = new PartyClient.ValidateResult(true, null, null, "t1");

        assertTrue(provider.isValid(realm("tenant-btcl-t1"), user("alice"), password("pw")));
        assertEquals(List.of(new Call("tenant-btcl-t1", "alice", "pw")), party.calls);
    }

    @Test
    void isValid_isFalse_whenPartyDenies() {
        party.answer = PartyClient.ValidateResult.deny("policy denied", "t1");
        assertFalse(provider.isValid(realm("master"), user("alice"), password("bad")));
    }

    @Test
    void isValid_rejectsNonPasswordCredentials_withoutCallingParty() {
        CredentialInput otp = mock(CredentialInput.class);
        when(otp.getType()).thenReturn("otp");

        assertFalse(provider.isValid(realm("master"), user("alice"), otp));
        assertTrue(party.calls.isEmpty());
    }

    @Test
    void isValid_rejectsMissingUsername_withoutCallingParty() {
        assertFalse(provider.isValid(realm("master"), user(null), password("pw")));
        assertTrue(party.calls.isEmpty());
    }
}

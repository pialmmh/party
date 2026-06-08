package com.telcobright.party.v2.contacts.internal.invite;

/**
 * Delivers an app invite to a non-user's phone. Dev mode logs it
 * ({@link LogInviteSender}); the operator's SMS gateway plugs in later.
 */
public interface InviteSender {

    void invite(String fromE164, String toE164);
}

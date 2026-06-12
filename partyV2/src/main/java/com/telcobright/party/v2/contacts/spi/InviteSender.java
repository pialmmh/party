package com.telcobright.party.v2.contacts.spi;

/**
 * Delivers an app invite to a non-user's phone. Dev mode logs it
 * (dev mode: the logging sender); the operator's SMS gateway plugs in later.
 */
public interface InviteSender {

    void invite(String fromE164, String toE164);
}

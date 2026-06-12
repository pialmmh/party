package com.telcobright.party.v2.registration.spi;

/**
 * Live-session kill port (frozen §2 central kill): end the CURRENT XMPP
 * session of user@host/resource right now. Best-effort by contract — the
 * revoked registry row already guarantees refresh refusal; this only
 * shortens the window. Production impl = ejabberd mod_http_api kick_session;
 * tests hand in a fake.
 */
public interface SessionKiller {

    void kickSession(String user, String host, String resource);
}

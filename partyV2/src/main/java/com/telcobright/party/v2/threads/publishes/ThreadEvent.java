package com.telcobright.party.v2.threads.publishes;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One canonical change to one conversation's thread overlay, dropped on the
 * owner's per-account feed — the CANONICAL thread-event wire JSON (frozen §8b,
 * = the client parse shape; d's ThreadEvent codec). Threads are the THIRD NATS
 * sync datatype after chat and contacts; the body is pass-through JSON exactly
 * like {@link com.telcobright.party.v2.contacts.publishes.ContactEvent}. Subject
 * {@code sl.threads.<ownerToken>} carries the owner, so the owner is NOT in the
 * body. The device drains its feed by {@code version} and applies each event
 * into its thread overlay store.
 *
 * <ul>
 *   <li>{@code type} = one of the 8 actions (archive/unarchive, pin/unpin,
 *       mute/unmute, delete, read). The flag pairs carry no extra payload — the
 *       {@code type} IS the new value.</li>
 *   <li>{@code version} is the owner's monotonic cursor; higher wins (LWW). It
 *       advances only on a real change.</li>
 *   <li>{@code readUpTo} is present ONLY on {@code threadRead} (MAX-by-value,
 *       version-independent); omitted on flag events.</li>
 *   <li>{@code originId} = the OPTIONAL client reconcile key: echoed from the
 *       write so the originating device matches this live event back to its
 *       optimistic row. OMITTED from the wire when null.</li>
 *   <li>{@code ts} = server publish time (epoch ms).</li>
 * </ul>
 */
public record ThreadEvent(
        String type,
        String conversationId,
        long version,
        @JsonInclude(JsonInclude.Include.NON_NULL) String originId,
        @JsonInclude(JsonInclude.Include.NON_NULL) String readUpTo,
        long ts) {

    public static final String ARCHIVE   = "threadArchive";
    public static final String UNARCHIVE = "threadUnarchive";
    public static final String PIN       = "threadPin";
    public static final String UNPIN     = "threadUnpin";
    public static final String MUTE      = "threadMute";
    public static final String UNMUTE    = "threadUnmute";
    public static final String DELETE    = "threadDelete";
    public static final String READ      = "threadRead";

    /** A flag change (archive/pin/mute/delete pairs) — no readUpTo. */
    public static ThreadEvent flag(String type, String conversationId, long version,
                                   String originId, long ts) {
        return new ThreadEvent(type, conversationId, version, originId, null, ts);
    }

    /** A read marker — carries readUpTo. */
    public static ThreadEvent read(String conversationId, long version,
                                   String originId, String readUpTo, long ts) {
        return new ThreadEvent(READ, conversationId, version, originId, readUpTo, ts);
    }
}

package com.telcobright.party.v2.threads.internal;

import com.telcobright.party.v2.threads.publishes.ThreadEvent;
import com.telcobright.party.v2.threads.spi.ThreadEntryStore.State;

/**
 * The 8 thread actions and how each one transforms a thread's overlay. The flag
 * pairs flip a boolean; {@code DELETE} sets the hide flag (hide-only, §8b O6);
 * {@code READ} advances the read marker MAX-by-value (monotonic — a read marker
 * never regresses). The {@code changed?} decision lives in the service (compare
 * the result against the current overlay).
 */
public enum ThreadAction {
    ARCHIVE(ThreadEvent.ARCHIVE),
    UNARCHIVE(ThreadEvent.UNARCHIVE),
    PIN(ThreadEvent.PIN),
    UNPIN(ThreadEvent.UNPIN),
    MUTE(ThreadEvent.MUTE),
    UNMUTE(ThreadEvent.UNMUTE),
    DELETE(ThreadEvent.DELETE),
    READ(ThreadEvent.READ);

    private final String wire;

    ThreadAction(String wire) { this.wire = wire; }

    public String wire() { return wire; }

    public boolean isRead() { return this == READ; }

    /** Parse the wire {@code type}; throws IllegalArgumentException on an unknown type. */
    public static ThreadAction from(String type) {
        for (ThreadAction a : values()) {
            if (a.wire.equals(type)) return a;
        }
        throw new IllegalArgumentException("unknown thread action: " + type);
    }

    /** Apply this action to the current overlay, returning the new overlay (version unchanged). */
    public State applyTo(State cur, String postedReadUpTo) {
        return switch (this) {
            case ARCHIVE   -> cur.withArchived(true);
            case UNARCHIVE -> cur.withArchived(false);
            case PIN       -> cur.withPinned(true);
            case UNPIN     -> cur.withPinned(false);
            case MUTE      -> cur.withMuted(true);
            case UNMUTE    -> cur.withMuted(false);
            case DELETE    -> cur.withDeleted(true);
            case READ      -> cur.withReadUpTo(maxReadUpTo(cur.readUpTo(), postedReadUpTo));
        };
    }

    /** MAX-by-value of two read markers ({@code <pos|tsMs>}); null = unset; non-numeric takes the posted. */
    static String maxReadUpTo(String current, String posted) {
        if (posted == null) return current;
        if (current == null) return posted;
        try {
            return Long.parseLong(posted) > Long.parseLong(current) ? posted : current;
        } catch (NumberFormatException e) {
            return posted;   // non-numeric marker: take the latest posted (never regress silently)
        }
    }
}

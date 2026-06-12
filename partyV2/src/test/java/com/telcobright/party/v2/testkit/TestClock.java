package com.telcobright.party.v2.testkit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Mutable test clock — advance() drives TTL/expiry deterministically. */
public final class TestClock extends Clock {

    private Instant now;

    public TestClock(Instant start) { this.now = start; }

    public static TestClock at(String iso) { return new TestClock(Instant.parse(iso)); }

    public void advance(Duration d) { now = now.plus(d); }

    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) { return this; }
    @Override public Instant instant() { return now; }
}

package com.telcobright.party.v2.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

/**
 * CDI producers for the shared runtime handles (HttpClient, Clock) so beans
 * INJECT them instead of locating/constructing their own — unit tests hand in
 * fakes (a recorded HttpClient, a fixed/mutable Clock) directly.
 */
@ApplicationScoped
public class RuntimeResources {

    @Produces
    @ApplicationScoped
    public HttpClient httpClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    @Produces
    @ApplicationScoped
    public Clock clock() {
        return Clock.systemUTC();
    }
}

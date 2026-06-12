package com.telcobright.party.v2.registration.spi;

/**
 * Delivers an OTP code to a phone. Dev mode logs it server-side
 * (dev mode: the logging sender); production plugs an SMS gateway here.
 */
public interface OtpSender {

    void send(String phone, String code);
}

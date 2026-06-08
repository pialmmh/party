package com.telcobright.party.v2.registration.internal.otp;

/**
 * Delivers an OTP code to a phone. Dev mode logs it server-side
 * ({@link LogOtpSender}); production plugs an SMS gateway here.
 */
public interface OtpSender {

    void send(String phone, String code);
}

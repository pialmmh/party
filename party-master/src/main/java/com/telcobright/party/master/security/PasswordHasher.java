package com.telcobright.party.master.security;

import at.favre.lib.crypto.bcrypt.BCrypt;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PasswordHasher {

    private static final int COST = 12;

    public String hash(String plain) {
        return BCrypt.withDefaults().hashToString(COST, plain.toCharArray());
    }

    public boolean verify(String plain, String hashed) {
        if (plain == null || hashed == null) return false;
        return BCrypt.verifyer().verify(plain.toCharArray(), hashed).verified;
    }
}

package com.telcobright.party.v2.model;

public record Role(String name, String source) {
    public static Role of(String name, String source) {
        return new Role(name, source);
    }
}

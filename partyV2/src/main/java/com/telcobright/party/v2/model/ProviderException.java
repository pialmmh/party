package com.telcobright.party.v2.model;

public class ProviderException extends RuntimeException {
    public ProviderException(String message) { super(message); }
    public ProviderException(String message, Throwable cause) { super(message, cause); }
}

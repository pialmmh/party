package com.telcobright.party.v2.policy;

/**
 * One atomic check. Stateless — no side effects beyond fields the caller
 * intentionally mutates on the input (e.g. attaching the resolved user
 * to {@link AuthContext}).
 */
public interface Rule<T> {

    String name();

    EvalResult execute(T input);
}

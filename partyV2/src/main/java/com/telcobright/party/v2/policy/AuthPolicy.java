package com.telcobright.party.v2.policy;

/**
 * Marker interface for policies that operate on {@link AuthContext}. Exists so
 * CDI can discover all auth policies as a homogeneous {@code Instance<AuthPolicy>}
 * without tripping over generic type erasure on {@code Policy<AuthContext>}.
 */
public interface AuthPolicy extends Policy<AuthContext> {
}

package com.telcobright.party.v2.policy;

/**
 * A named decision unit. Concrete policies are CDI beans and write {@link #execute}
 * as pure Java — any combination of injected {@link Rule}s with AND / OR / etc.
 *
 * Contract:
 *   - return {@link EvalResult#noMatch()}  -> policy doesn't apply; chain moves on
 *   - return {@link EvalResult#allow()}    -> match, permit; chain returns this
 *   - return {@link EvalResult#deny}       -> match, deny;   chain returns this
 *
 * {@link ApiChain} runs policies in YAML-declared order, Cisco-ACL style:
 * first {@code matched=true} wins.
 */
public interface Policy<T> {

    String name();

    EvalResult execute(T input);
}

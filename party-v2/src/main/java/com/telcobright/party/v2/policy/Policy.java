package com.telcobright.party.v2.policy;

import com.telcobright.party.v2.adapter.UserRepoAdapter;

/**
 * A single step in the request pipeline. Stateless — receives the tenant's
 * adapter at apply time so the same Policy instance is reusable across tenants.
 */
public interface Policy {

    String name();

    PolicyOutcome apply(PolicyContext ctx, UserRepoAdapter adapter);
}

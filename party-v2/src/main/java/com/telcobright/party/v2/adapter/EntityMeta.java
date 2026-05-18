package com.telcobright.party.v2.adapter;

import java.util.List;

/**
 * Schema metadata for one entity exposed by a {@link UserRepoAdapter}.
 *
 * The adapter discovers this by probing the underlying source at startup
 * (e.g. Odoo's ir.model.fields). It feeds the policy builder UI so authors
 * can reference real fields by name and type without hard-coding the schema.
 *
 * @param name      repo-side identifier (e.g. "res.users" for Odoo)
 * @param source    which adapter type produced this (e.g. "odoo")
 * @param fields    field list; empty if introspection was skipped or failed
 */
public record EntityMeta(
        String name,
        String source,
        List<FieldMeta> fields
) {
}

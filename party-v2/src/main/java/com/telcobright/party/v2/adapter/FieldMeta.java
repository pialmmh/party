package com.telcobright.party.v2.adapter;

/**
 * One field on an {@link EntityMeta}. Sourced from the underlying repo's
 * schema introspection (e.g. Odoo's ir.model.fields).
 *
 * @param name      field identifier on the repo side (e.g. "login")
 * @param type      repo-native type ("char", "integer", "many2one", "boolean", ...)
 * @param required  true if the repo says this field is mandatory
 * @param relation  for relational fields, the target entity name; otherwise null
 * @param label     human display label from the repo (may be null)
 */
public record FieldMeta(
        String name,
        String type,
        boolean required,
        String relation,
        String label
) {
}

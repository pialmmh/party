// Cedar policy text emitter.
//
// The canvas keeps each RuleNode's data as a CompiledRule.  This module walks a
// list of rules and emits valid Cedar source.  One-way today (canvas → text);
// parsing Cedar text back into rules is future work.
//
// Cedar grammar reference (informal):
//
//   @id("name")
//   permit | forbid (
//       principal[, principal-clause],
//       action[, action-clause],
//       resource[, resource-clause]
//   )
//   when   { expr }
//   unless { expr };
//
// We emit `when {}` only — `unless {}` is left for a future iteration.

export type Effect = 'permit' | 'forbid';

export type ConditionOp =
    | '==' | '!=' | '<' | '<=' | '>' | '>=' | 'in' | 'like';

export const CONDITION_OPS: ConditionOp[] = ['==', '!=', '<', '<=', '>', '>=', 'in', 'like'];

export type Condition = {
    lhs: string;   // free-form Cedar expression, e.g. "principal.email"
    op:  ConditionOp;
    rhs: string;   // value; formatRhs() decides quoting
};

export type CompiledRule = {
    id: string;
    name: string;
    effect: Effect;
    /** Empty string = no principal type constraint (matches any). */
    principalType: string;
    /** Empty string = no action constraint. */
    actionName: string;
    /** Empty string = no resource type constraint. */
    resourceType: string;
    conditions: Condition[];
};

/**
 * Emit Cedar source for an ordered list of rules.  Rules are written in the
 * order given, separated by blank lines.  Within a single rule, conditions
 * are joined by `&&` in the `when {}` block (AND semantics across the row).
 *
 * Order across rules matters at evaluation time: Cedar's evaluator takes the
 * set as a whole and applies its own "any permit AND no forbid" rule, so
 * the AND-ordering the user expects between policies in the chain is enforced
 * at the Party PolicyChain level — see PolicyChain.run().  Each `cedar:<name>`
 * chain entry compiles to one Cedar policy *set* that the evaluator handles
 * internally.
 */
export function compileToCedar(rules: CompiledRule[]): string {
    if (rules.length === 0) {
        return '// No rules defined yet. Click "+ Add rule" to start.\n';
    }
    return rules.map(emitOne).join('\n\n') + '\n';
}

function emitOne(r: CompiledRule): string {
    const lines: string[] = [];
    const safeName = r.name && r.name.trim() ? r.name.trim() : `rule-${r.id}`;
    lines.push(`@id("${escapeString(safeName)}")`);
    lines.push(`${r.effect} (`);
    lines.push(`    ${emitPrincipal(r)},`);
    lines.push(`    ${emitAction(r)},`);
    lines.push(`    ${emitResource(r)}`);
    if (r.conditions.length === 0) {
        lines.push(`);`);
        return lines.join('\n');
    }
    lines.push(`)`);
    lines.push(`when {`);
    lines.push(
        r.conditions
            .map(c => `    ${emitCondition(c)}`)
            .join(' &&\n')
    );
    lines.push(`};`);
    return lines.join('\n');
}

function emitPrincipal(r: CompiledRule): string {
    const t = r.principalType.trim();
    return t ? `principal is ${t}` : 'principal';
}

function emitAction(r: CompiledRule): string {
    const a = r.actionName.trim();
    return a ? `action == Action::"${escapeString(a)}"` : 'action';
}

function emitResource(r: CompiledRule): string {
    const t = r.resourceType.trim();
    return t ? `resource is ${t}` : 'resource';
}

function emitCondition(c: Condition): string {
    const lhs = c.lhs.trim() || 'principal';
    return `${lhs} ${c.op} ${formatRhs(c.rhs)}`;
}

/**
 * Decide how to render the RHS:
 *   - numeric literal    → as-is
 *   - boolean literal    → as-is
 *   - list literal `[...]` → as-is
 *   - entity literal `Type::"id"` → as-is
 *   - everything else    → quoted Cedar string
 * Empty string becomes `""`.
 */
export function formatRhs(v: string): string {
    const t = v.trim();
    if (t === '') return '""';
    if (/^-?\d+(\.\d+)?$/.test(t)) return t;
    if (t === 'true' || t === 'false') return t;
    if (t.startsWith('[') && t.endsWith(']')) return t;
    if (/^[A-Z][A-Za-z0-9_]*(::[A-Z][A-Za-z0-9_]*)*::"[^"]*"$/.test(t)) return t;
    return `"${escapeString(t)}"`;
}

function escapeString(s: string): string {
    return s.replace(/\\/g, '\\\\').replace(/"/g, '\\"');
}

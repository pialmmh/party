import { Handle, Position, NodeProps, useReactFlow } from '@xyflow/react';
import {
    Condition,
    CompiledRule,
    CONDITION_OPS,
    Effect,
} from './cedar';

export type RuleNodeData = Omit<CompiledRule, 'id'>;

const INPUT_STYLE: React.CSSProperties = {
    background: 'var(--bg)',
    border: '1px solid var(--border-soft)',
    color: 'var(--text)',
    padding: '3px 6px',
    borderRadius: 3,
    fontSize: 11,
    fontFamily: 'inherit',
    width: '100%',
};

const MONO_INPUT: React.CSSProperties = {
    ...INPUT_STYLE,
    fontFamily: 'ui-monospace, Menlo, Consolas, monospace',
};

export default function RuleNode({ id, data }: NodeProps) {
    const d = data as unknown as RuleNodeData;
    const { setNodes } = useReactFlow();

    function update(patch: Partial<RuleNodeData>) {
        setNodes(ns => ns.map(n =>
            n.id === id ? { ...n, data: { ...n.data, ...patch } } : n
        ));
    }

    function updateCondition(idx: number, patch: Partial<Condition>) {
        const next = d.conditions.map((c, i) => i === idx ? { ...c, ...patch } : c);
        update({ conditions: next });
    }

    function addCondition() {
        update({
            conditions: [...d.conditions, { lhs: 'principal.email', op: '==', rhs: 'alice@example.com' }],
        });
    }

    function removeCondition(idx: number) {
        update({ conditions: d.conditions.filter((_, i) => i !== idx) });
    }

    const accent = d.effect === 'permit' ? '#4ade80' : '#f87171';
    return (
        <div style={{
            background: 'var(--bg-elev)',
            border: `1px solid ${accent}`,
            borderRadius: 8,
            width: 320,
            boxShadow: '0 4px 12px rgba(0,0,0,0.25)',
            color: 'var(--text)',
            fontSize: 12,
        }}>
            <Handle
                type="target"
                position={Position.Left}
                id="in"
                style={{ background: accent, width: 8, height: 8, border: '2px solid var(--bg-elev)', left: -4 }}
            />

            {/* header */}
            <div style={{
                padding: '8px 12px',
                background: accent,
                color: '#0d0f12',
                borderTopLeftRadius: 7,
                borderTopRightRadius: 7,
                fontWeight: 600,
                display: 'flex',
                alignItems: 'center',
                gap: 8,
            }}>
                <select
                    value={d.effect}
                    onChange={e => update({ effect: e.target.value as Effect })}
                    style={{
                        background: 'rgba(0,0,0,0.2)',
                        color: '#0d0f12',
                        border: '1px solid rgba(0,0,0,0.3)',
                        padding: '2px 6px',
                        borderRadius: 3,
                        fontSize: 10,
                        textTransform: 'uppercase',
                        letterSpacing: 0.06,
                        fontWeight: 600,
                    }}
                >
                    <option value="permit">PERMIT</option>
                    <option value="forbid">FORBID</option>
                </select>
                <input
                    value={d.name}
                    onChange={e => update({ name: e.target.value })}
                    placeholder="rule name"
                    style={{
                        flex: 1,
                        background: 'rgba(0,0,0,0.15)',
                        color: '#0d0f12',
                        border: '1px solid rgba(0,0,0,0.3)',
                        padding: '2px 6px',
                        borderRadius: 3,
                        fontSize: 12,
                        fontWeight: 600,
                    }}
                />
            </div>

            {/* head (principal/action/resource) */}
            <div style={{ padding: '8px 12px 4px', display: 'grid', gridTemplateColumns: 'auto 1fr', gap: 6, alignItems: 'center' }}>
                <span style={{ color: 'var(--text-faint)', fontSize: 10, textTransform: 'uppercase', letterSpacing: 0.06 }}>
                    principal
                </span>
                <input
                    value={d.principalType}
                    onChange={e => update({ principalType: e.target.value })}
                    placeholder="(any)"
                    style={MONO_INPUT}
                />
                <span style={{ color: 'var(--text-faint)', fontSize: 10, textTransform: 'uppercase', letterSpacing: 0.06 }}>
                    action
                </span>
                <input
                    value={d.actionName}
                    onChange={e => update({ actionName: e.target.value })}
                    placeholder="(any)"
                    style={MONO_INPUT}
                />
                <span style={{ color: 'var(--text-faint)', fontSize: 10, textTransform: 'uppercase', letterSpacing: 0.06 }}>
                    resource
                </span>
                <input
                    value={d.resourceType}
                    onChange={e => update({ resourceType: e.target.value })}
                    placeholder="(any)"
                    style={MONO_INPUT}
                />
            </div>

            {/* divider */}
            <div style={{
                height: 1,
                background: 'var(--border)',
                margin: '6px 0',
            }} />

            {/* conditions */}
            <div style={{ padding: '0 12px 8px' }}>
                <div style={{
                    color: 'var(--text-faint)',
                    fontSize: 10,
                    textTransform: 'uppercase',
                    letterSpacing: 0.06,
                    marginBottom: 4,
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                }}>
                    <span>when (AND)</span>
                    <button
                        onClick={addCondition}
                        style={{
                            background: 'transparent',
                            color: 'var(--accent)',
                            border: '1px solid var(--accent)',
                            padding: '1px 6px',
                            borderRadius: 3,
                            fontSize: 10,
                            cursor: 'pointer',
                        }}
                    >
                        + add
                    </button>
                </div>
                {d.conditions.length === 0 && (
                    <div style={{ color: 'var(--text-faint)', fontStyle: 'italic', fontSize: 11 }}>
                        (none — matches whenever head matches)
                    </div>
                )}
                {d.conditions.map((c, i) => (
                    <div key={i} style={{
                        display: 'grid',
                        gridTemplateColumns: '1fr 60px 1fr 16px',
                        gap: 4,
                        marginBottom: 4,
                    }}>
                        <input
                            value={c.lhs}
                            onChange={e => updateCondition(i, { lhs: e.target.value })}
                            style={MONO_INPUT}
                            placeholder="principal.x"
                        />
                        <select
                            value={c.op}
                            onChange={e => updateCondition(i, { op: e.target.value as Condition['op'] })}
                            style={INPUT_STYLE}
                        >
                            {CONDITION_OPS.map(op => <option key={op} value={op}>{op}</option>)}
                        </select>
                        <input
                            value={c.rhs}
                            onChange={e => updateCondition(i, { rhs: e.target.value })}
                            style={MONO_INPUT}
                            placeholder="value"
                        />
                        <button
                            onClick={() => removeCondition(i)}
                            title="remove condition"
                            style={{
                                background: 'transparent',
                                color: 'var(--text-faint)',
                                border: '1px solid var(--border-soft)',
                                borderRadius: 3,
                                fontSize: 12,
                                lineHeight: 1,
                                cursor: 'pointer',
                                padding: 0,
                            }}
                        >
                            ×
                        </button>
                    </div>
                ))}
            </div>

            <Handle
                type="source"
                position={Position.Right}
                id="out"
                style={{ background: accent, width: 8, height: 8, border: '2px solid var(--bg-elev)', right: -4 }}
            />
        </div>
    );
}

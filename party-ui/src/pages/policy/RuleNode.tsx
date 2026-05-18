import { Handle, Position, NodeProps } from '@xyflow/react';

export type RuleNodeData = {
    name: string;
    effect: 'permit' | 'forbid';
    condition: string;
};

export default function RuleNode({ data }: NodeProps) {
    const d = data as unknown as RuleNodeData;
    const accent = d.effect === 'permit' ? '#4ade80' : '#f87171';
    return (
        <div style={{
            background: 'var(--bg-elev)',
            border: `1px solid ${accent}`,
            borderRadius: 8,
            width: 260,
            boxShadow: '0 4px 12px rgba(0,0,0,0.25)',
            color: 'var(--text)',
            fontSize: 12,
        }}>
            <Handle
                type="target"
                position={Position.Left}
                id="in"
                style={{
                    background: accent,
                    width: 8,
                    height: 8,
                    border: '2px solid var(--bg-elev)',
                    left: -4,
                }}
            />
            <div style={{
                padding: '8px 12px',
                background: accent,
                color: '#0d0f12',
                borderTopLeftRadius: 7,
                borderTopRightRadius: 7,
                fontWeight: 600,
            }}>
                <span style={{ opacity: 0.7, fontSize: 10, letterSpacing: 0.08, textTransform: 'uppercase' }}>
                    {d.effect} rule
                </span>
                <div>{d.name}</div>
            </div>
            <div style={{ padding: '8px 12px' }}>
                <div style={{ fontSize: 10, color: 'var(--text-faint)', textTransform: 'uppercase', letterSpacing: 0.06, marginBottom: 4 }}>
                    when (AND across inputs)
                </div>
                <div style={{
                    fontFamily: 'ui-monospace, Menlo, Consolas, monospace',
                    fontSize: 11,
                    color: 'var(--text-dim)',
                    minHeight: 24,
                }}>
                    {d.condition || '/* drop field connections here */'}
                </div>
            </div>
            <Handle
                type="source"
                position={Position.Right}
                id="out"
                style={{
                    background: accent,
                    width: 8,
                    height: 8,
                    border: '2px solid var(--bg-elev)',
                    right: -4,
                }}
            />
        </div>
    );
}

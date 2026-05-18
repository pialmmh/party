import { Handle, Position, NodeProps } from '@xyflow/react';

export type DecisionNodeData = {
    label: string;
};

export default function DecisionNode({ data }: NodeProps) {
    const d = data as unknown as DecisionNodeData;
    return (
        <div style={{
            background: '#0d0f12',
            border: '2px dashed var(--accent)',
            borderRadius: 8,
            padding: '12px 22px',
            color: 'var(--text)',
            boxShadow: '0 4px 12px rgba(0,0,0,0.25)',
            fontSize: 12,
            fontWeight: 600,
            letterSpacing: 0.05,
        }}>
            <Handle
                type="target"
                position={Position.Left}
                id="in"
                style={{
                    background: 'var(--accent)',
                    width: 10,
                    height: 10,
                    border: '2px solid var(--bg)',
                    left: -5,
                }}
            />
            <span style={{ fontSize: 10, color: 'var(--text-faint)', textTransform: 'uppercase', letterSpacing: 0.08, marginRight: 8 }}>
                output
            </span>
            {d.label}
        </div>
    );
}

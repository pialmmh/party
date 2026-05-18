import { Handle, Position, NodeProps } from '@xyflow/react';
import { FieldMeta } from '../../api/partyClient';

export type EntityNodeData = {
    label: string;
    source: string;
    fields: FieldMeta[];
    role: 'principal' | 'resource';
};

/**
 * Renders one entity from the tenant vocabulary as a node with a source handle
 * per field. Drag a handle to wire that field into a condition or rule node.
 */
export default function EntityNode({ data }: NodeProps) {
    const d = data as unknown as EntityNodeData;
    const accent = d.role === 'principal' ? '#4a90e2' : '#a78bfa';
    return (
        <div style={{
            background: 'var(--bg-elev)',
            border: `1px solid ${accent}`,
            borderRadius: 8,
            minWidth: 200,
            boxShadow: '0 4px 12px rgba(0,0,0,0.25)',
            fontSize: 12,
            color: 'var(--text)',
        }}>
            <div style={{
                padding: '8px 12px',
                background: 'linear-gradient(180deg, ' + accent + ' 0%, ' + accent + 'cc 100%)',
                color: '#fff',
                borderTopLeftRadius: 7,
                borderTopRightRadius: 7,
                fontWeight: 600,
                fontSize: 12,
            }}>
                <span style={{ opacity: 0.85, fontSize: 10, letterSpacing: 0.08, textTransform: 'uppercase' }}>
                    {d.role}
                </span>
                <div>{d.label}</div>
            </div>
            <div style={{ padding: '4px 0 8px' }}>
                {d.fields.length === 0 && (
                    <div style={{ padding: '6px 12px', color: 'var(--text-faint)', fontStyle: 'italic' }}>
                        no fields introspected
                    </div>
                )}
                {d.fields.map(f => (
                    <div key={f.name} style={{
                        position: 'relative',
                        padding: '4px 14px 4px 12px',
                        display: 'flex',
                        justifyContent: 'space-between',
                        gap: 8,
                        alignItems: 'center',
                    }}>
                        <span style={{ fontFamily: 'ui-monospace, Menlo, Consolas, monospace' }}>{f.name}</span>
                        <span style={{ color: 'var(--text-faint)', fontSize: 11 }}>{f.type}</span>
                        <Handle
                            type="source"
                            position={Position.Right}
                            id={f.name}
                            style={{
                                background: accent,
                                width: 8,
                                height: 8,
                                border: '2px solid var(--bg-elev)',
                                right: -4,
                            }}
                        />
                    </div>
                ))}
            </div>
        </div>
    );
}

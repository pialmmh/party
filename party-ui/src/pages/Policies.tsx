import { useCallback, useEffect, useMemo, useState } from 'react';
import {
    ReactFlow,
    Background,
    Controls,
    MiniMap,
    addEdge,
    applyNodeChanges,
    applyEdgeChanges,
    type Node,
    type Edge,
    type Connection,
    type NodeChange,
    type EdgeChange,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';

import { partyApi, EntityMeta } from '../api/partyClient';
import EntityNode, { EntityNodeData } from './policy/EntityNode';
import RuleNode, { RuleNodeData } from './policy/RuleNode';
import DecisionNode, { DecisionNodeData } from './policy/DecisionNode';

const TENANT = 't1';

const nodeTypes = {
    entity:   EntityNode,
    rule:     RuleNode,
    decision: DecisionNode,
};

function buildInitialGraph(entities: EntityMeta[]): { nodes: Node[]; edges: Edge[] } {
    // Lay out vocabulary on the left, rules in the middle, decision on the right.
    const nodes: Node[] = [];
    entities.forEach((e, i) => {
        nodes.push({
            id: `entity-${e.name}`,
            type: 'entity',
            position: { x: 20, y: 40 + i * 240 },
            data: {
                label: e.name,
                source: e.source,
                fields: e.fields,
                role: i === 0 ? 'principal' : 'resource',
            } satisfies EntityNodeData,
            draggable: true,
        });
    });

    nodes.push({
        id: 'rule-1',
        type: 'rule',
        position: { x: 360, y: 80 },
        data: {
            name: 'basic-auth',
            effect: 'permit',
            condition: 'principal authenticated by adapter',
        } satisfies RuleNodeData,
        draggable: true,
    });

    nodes.push({
        id: 'decision',
        type: 'decision',
        position: { x: 720, y: 110 },
        data: { label: 'allow / deny' } satisfies DecisionNodeData,
        draggable: true,
    });

    const edges: Edge[] = [
        {
            id: 'e-rule-decision',
            source: 'rule-1',
            target: 'decision',
            sourceHandle: 'out',
            targetHandle: 'in',
            animated: true,
            style: { stroke: 'var(--accent)' },
        },
    ];
    return { nodes, edges };
}

type Loaded =
    | { kind: 'loading' }
    | { kind: 'ok' }
    | { kind: 'error'; message: string };

export default function Policies() {
    const [state, setState] = useState<Loaded>({ kind: 'loading' });
    const [nodes, setNodes] = useState<Node[]>([]);
    const [edges, setEdges] = useState<Edge[]>([]);
    const [cedarPreview, setCedarPreview] = useState<string>('');

    async function load() {
        setState({ kind: 'loading' });
        try {
            const entities = await partyApi.entities(TENANT);
            const { nodes: n, edges: e } = buildInitialGraph(entities);
            setNodes(n);
            setEdges(e);
            setCedarPreview(stubCedar());
            setState({ kind: 'ok' });
        } catch (e) {
            setState({ kind: 'error', message: e instanceof Error ? e.message : String(e) });
        }
    }

    useEffect(() => { void load(); }, []);

    const onNodesChange = useCallback(
        (changes: NodeChange[]) => setNodes(ns => applyNodeChanges(changes, ns)),
        []
    );
    const onEdgesChange = useCallback(
        (changes: EdgeChange[]) => setEdges(es => applyEdgeChanges(changes, es)),
        []
    );
    const onConnect = useCallback(
        (conn: Connection) =>
            setEdges(es => addEdge({ ...conn, animated: true, style: { stroke: 'var(--accent)' } }, es)),
        []
    );

    const stats = useMemo(() => ({
        nodes: nodes.length,
        edges: edges.length,
    }), [nodes, edges]);

    return (
        <>
            <div className="page-header">
                <div>
                    <h1>Policies</h1>
                    <div className="lede">
                        Visual builder for the per-tenant policy chain. Drag fields from the
                        vocabulary on the left into rules; rules connect to the output. Policies
                        evaluate in order with <strong>AND</strong> semantics — the first reject
                        ends the chain.
                    </div>
                </div>
                <button onClick={load}>Reset</button>
            </div>

            {state.kind === 'loading' && (
                <div className="panel"><div className="panel-body muted">Loading vocabulary…</div></div>
            )}

            {state.kind === 'error' && (
                <div className="panel">
                    <div className="panel-header">
                        <h2>Backend unreachable</h2>
                        <span className="badge warn">offline</span>
                    </div>
                    <div className="panel-body muted">
                        <code>{state.message}</code>
                    </div>
                </div>
            )}

            {state.kind === 'ok' && (
                <>
                    <div className="panel" style={{ padding: 0 }}>
                        <div className="panel-header">
                            <h2>Canvas</h2>
                            <span className="badge accent">
                                {stats.nodes} nodes · {stats.edges} edges
                            </span>
                        </div>
                        <div style={{ height: 560, background: 'var(--bg)', position: 'relative' }}>
                            <ReactFlow
                                nodes={nodes}
                                edges={edges}
                                onNodesChange={onNodesChange}
                                onEdgesChange={onEdgesChange}
                                onConnect={onConnect}
                                nodeTypes={nodeTypes}
                                fitView
                                fitViewOptions={{ padding: 0.15 }}
                                proOptions={{ hideAttribution: true }}
                            >
                                <Background gap={16} size={1} color="var(--border)" />
                                <Controls
                                    style={{ background: 'var(--bg-elev)', border: '1px solid var(--border)' }}
                                />
                                <MiniMap
                                    style={{ background: 'var(--bg-elev)', border: '1px solid var(--border)' }}
                                    nodeColor={n => (n.type === 'rule' ? '#4ade80' :
                                                     n.type === 'decision' ? '#4a90e2' : '#a78bfa')}
                                />
                            </ReactFlow>
                        </div>
                    </div>

                    <div className="panel">
                        <div className="panel-header">
                            <h2>Cedar preview</h2>
                            <span className="badge">scaffold — compiler is future work</span>
                        </div>
                        <div className="panel-body">
                            <pre style={{
                                background: 'var(--bg)',
                                border: '1px solid var(--border)',
                                padding: 12,
                                borderRadius: 4,
                                fontSize: 12,
                                margin: 0,
                                overflowX: 'auto',
                                fontFamily: 'ui-monospace, Menlo, Consolas, monospace',
                            }}>{cedarPreview}</pre>
                        </div>
                    </div>

                    <div className="panel">
                        <div className="panel-header">
                            <h2>Chain order &amp; semantics</h2>
                        </div>
                        <div className="panel-body">
                            <ol className="muted" style={{ margin: 0, paddingLeft: 18 }}>
                                <li>Policies run in <code>order</code> ascending from the chain config.</li>
                                <li>Each policy returns <code>PASS</code> or <code>REJECT</code> with a reason.</li>
                                <li>First <code>REJECT</code> ends the chain — auth is denied (AND logic).</li>
                                <li>All <code>PASS</code> → auth is granted, claims from the chain are emitted.</li>
                            </ol>
                        </div>
                    </div>
                </>
            )}
        </>
    );
}

function stubCedar(): string {
    return `// Generated from the canvas above. Compiler not yet implemented —
// this is a static preview until the AST writer lands.
permit (
    principal,
    action == Action::"login",
    resource
)
when {
    // basic-auth policy delegates credential check to the tenant adapter.
    // All policies in the chain must pass (AND).
    true
};`;
}

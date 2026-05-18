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
import { compileToCedar, CompiledRule, Effect } from './policy/cedar';

const TENANT = 't1';

const nodeTypes = {
    entity:   EntityNode,
    rule:     RuleNode,
    decision: DecisionNode,
};

const seedRule = (id: string, effect: Effect, position: { x: number; y: number }): Node => ({
    id,
    type: 'rule',
    position,
    data: {
        name: effect === 'permit' ? 'basic-auth' : 'block-bad-actors',
        effect,
        principalType: 'User',
        actionName: 'login',
        resourceType: '',
        conditions: effect === 'permit'
            ? [{ lhs: 'principal.active', op: '==', rhs: 'true' }]
            : [],
    } satisfies RuleNodeData,
    draggable: true,
});

function buildInitialGraph(entities: EntityMeta[]): { nodes: Node[]; edges: Edge[] } {
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

    nodes.push(seedRule('rule-1', 'permit', { x: 380, y: 60 }));

    nodes.push({
        id: 'decision',
        type: 'decision',
        position: { x: 820, y: 140 },
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
    const [copyHint, setCopyHint] = useState<string | null>(null);

    async function load() {
        setState({ kind: 'loading' });
        try {
            const entities = await partyApi.entities(TENANT);
            const { nodes: n, edges: e } = buildInitialGraph(entities);
            setNodes(n);
            setEdges(e);
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

    function addRule(effect: Effect) {
        const ruleNodes = nodes.filter(n => n.type === 'rule');
        const id = `rule-${ruleNodes.length + 1}-${Date.now().toString(36)}`;
        const y = 60 + ruleNodes.length * 280;
        const newNode = seedRule(id, effect, { x: 380, y });
        setNodes(ns => [...ns, newNode]);
        setEdges(es => [
            ...es,
            {
                id: `e-${id}-decision`,
                source: id,
                target: 'decision',
                sourceHandle: 'out',
                targetHandle: 'in',
                animated: true,
                style: { stroke: 'var(--accent)' },
            },
        ]);
    }

    /** Rule nodes in canvas Y-order — that's the visual chain order. */
    const compiledRules = useMemo<CompiledRule[]>(() => {
        return nodes
            .filter(n => n.type === 'rule')
            .slice()
            .sort((a, b) => a.position.y - b.position.y)
            .map(n => {
                const d = n.data as unknown as RuleNodeData;
                return { id: n.id, ...d };
            });
    }, [nodes]);

    const cedarText = useMemo(() => compileToCedar(compiledRules), [compiledRules]);

    async function copyToClipboard() {
        try {
            await navigator.clipboard.writeText(cedarText);
            setCopyHint('copied');
        } catch {
            setCopyHint('copy failed');
        }
        setTimeout(() => setCopyHint(null), 1500);
    }

    function downloadFile() {
        const blob = new Blob([cedarText], { type: 'text/plain;charset=utf-8' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `policies-${TENANT}.cedar`;
        a.click();
        URL.revokeObjectURL(url);
    }

    const stats = useMemo(() => ({
        nodes: nodes.length,
        edges: edges.length,
        rules: compiledRules.length,
    }), [nodes, edges, compiledRules]);

    return (
        <>
            <div className="page-header">
                <div>
                    <h1>Policies</h1>
                    <div className="lede">
                        Visual builder for the per-tenant policy chain. Policies evaluate in
                        order (top-to-bottom on the canvas) with <strong>AND</strong> semantics —
                        the first reject ends the chain.
                    </div>
                </div>
                <div style={{ display: 'flex', gap: 8 }}>
                    <button onClick={() => addRule('permit')}>+ Permit rule</button>
                    <button onClick={() => addRule('forbid')}>+ Forbid rule</button>
                    <button onClick={load}>Reset</button>
                </div>
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
                                {stats.rules} rule{stats.rules === 1 ? '' : 's'} · {stats.nodes} nodes · {stats.edges} edges
                            </span>
                        </div>
                        <div style={{ height: 620, background: 'var(--bg)', position: 'relative' }}>
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
                                    nodeColor={n => (n.type === 'rule'
                                        ? (((n.data as unknown as RuleNodeData)?.effect === 'forbid')
                                            ? '#f87171' : '#4ade80')
                                        : n.type === 'decision' ? '#4a90e2' : '#a78bfa')}
                                />
                            </ReactFlow>
                        </div>
                    </div>

                    <div className="panel">
                        <div className="panel-header">
                            <h2>Cedar output</h2>
                            <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                                {copyHint && <span className="badge good">{copyHint}</span>}
                                <button onClick={copyToClipboard}>Copy</button>
                                <button onClick={downloadFile}>Download .cedar</button>
                            </div>
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
                                whiteSpace: 'pre',
                            }}>{cedarText}</pre>
                        </div>
                    </div>

                    <div className="panel">
                        <div className="panel-header">
                            <h2>Chain order &amp; semantics</h2>
                        </div>
                        <div className="panel-body">
                            <ol className="muted" style={{ margin: 0, paddingLeft: 18 }}>
                                <li>Rules compile to Cedar policies in Y-order on the canvas (top first).</li>
                                <li>Each rule emits <code>{'@id(...) permit|forbid (...) when { ... };'}</code> — conditions joined by <code>&amp;&amp;</code>.</li>
                                <li>Party's <code>PolicyChain</code> evaluates entries in <code>order</code>; first <code>REJECT</code> ends auth (AND across the chain).</li>
                                <li>Within Cedar itself, the standard rule applies: at least one <code>permit</code> matches AND no <code>forbid</code> matches.</li>
                            </ol>
                        </div>
                    </div>
                </>
            )}
        </>
    );
}

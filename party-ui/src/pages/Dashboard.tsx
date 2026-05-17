import { useEffect, useState } from 'react';
import { partyApi, AdapterHealth } from '../api/partyClient';

type Loaded =
    | { kind: 'loading' }
    | { kind: 'ok'; rows: AdapterHealth[] }
    | { kind: 'error'; message: string };

export default function Dashboard() {
    const [state, setState] = useState<Loaded>({ kind: 'loading' });

    async function load() {
        setState({ kind: 'loading' });
        try {
            const rows = await partyApi.adapterHealth();
            setState({ kind: 'ok', rows });
        } catch (e) {
            setState({ kind: 'error', message: e instanceof Error ? e.message : String(e) });
        }
    }

    useEffect(() => { void load(); }, []);

    const tenantCount = state.kind === 'ok' ? new Set(state.rows.map(r => r.tenantId)).size : '—';
    const adapterCount = state.kind === 'ok' ? state.rows.length : '—';
    const healthyCount = state.kind === 'ok'
        ? state.rows.filter(r => r.status === 'good').length
        : '—';
    const overall = state.kind === 'ok'
        ? (state.rows.every(r => r.status === 'good') ? 'good'
            : state.rows.some(r => r.status === 'good') ? 'warn' : 'bad')
        : 'warn';

    return (
        <>
            <div className="page-header">
                <div>
                    <h1>Dashboard</h1>
                    <div className="lede">
                        {state.kind === 'error'
                            ? 'Backend unreachable — showing static layout.'
                            : 'Live view of operator btcl / dev.'}
                    </div>
                </div>
                <button className="primary" onClick={load}>Refresh</button>
            </div>

            <div className="cards">
                <div className="card">
                    <div className="label">Tenants</div>
                    <div className="value">{tenantCount}</div>
                    <div className="sub">configured in profile yml</div>
                </div>
                <div className="card">
                    <div className="label">Adapters</div>
                    <div className="value">{adapterCount}</div>
                    <div className="sub">one per tenant</div>
                </div>
                <div className="card">
                    <div className="label">Adapter health</div>
                    <div className="value" style={{ fontSize: 18 }}>
                        <span className={'status-dot ' + overall} />
                        {state.kind === 'loading' ? 'checking…'
                            : state.kind === 'error' ? 'unreachable'
                            : `${healthyCount} / ${adapterCount} ok`}
                    </div>
                    <div className="sub">cheap RPC probe</div>
                </div>
                <div className="card">
                    <div className="label">Policy chain</div>
                    <div className="value">1</div>
                    <div className="sub">basic-auth (default)</div>
                </div>
            </div>

            <div className="panel">
                <div className="panel-header">
                    <h2>Auth flow</h2>
                    <span className="badge accent">configured</span>
                </div>
                <div className="panel-body">
                    <p className="muted" style={{ margin: 0 }}>
                        <code>App</code> → <code>APISIX</code> → <code>Keycloak</code> → <code>Party SPI</code> →
                        {' '}<strong>Party policy chain</strong> → <code>UserRepoAdapter</code> (odoo) →
                        decision returned up the chain.
                    </p>
                    <p className="muted" style={{ marginBottom: 0, marginTop: 12 }}>
                        Each request flows through the configured policy list. The first policy
                        is the default <code>basic-auth</code> — it resolves the user via the
                        tenant's configured adapter and checks the password. Additional policies
                        (Cedar) plug in after.
                    </p>
                </div>
            </div>

            {state.kind === 'error' && (
                <div className="panel">
                    <div className="panel-header">
                        <h2>Backend status</h2>
                        <span className="badge warn">offline</span>
                    </div>
                    <div className="panel-body">
                        <p className="muted" style={{ marginTop: 0 }}>
                            Party API not reachable at <code>{partyApi.base}</code>. Start it with:
                        </p>
                        <pre style={{
                            background: 'var(--bg)',
                            border: '1px solid var(--border)',
                            padding: 10,
                            borderRadius: 4,
                            fontSize: 12,
                            overflowX: 'auto',
                        }}>
{`PARTY_OPERATOR_NAME=btcl PARTY_OPERATOR_PROFILE=dev \\
  mvn -pl party-api -am quarkus:dev`}
                        </pre>
                    </div>
                </div>
            )}

            {state.kind === 'ok' && (
                <div className="panel">
                    <div className="panel-header">
                        <h2>Per-tenant adapter status</h2>
                    </div>
                    <div className="panel-body flush">
                        <table>
                            <thead>
                                <tr>
                                    <th style={{ width: 90 }}>Tenant</th>
                                    <th style={{ width: 120 }}>Type</th>
                                    <th style={{ width: 110 }}>Status</th>
                                    <th>Target</th>
                                </tr>
                            </thead>
                            <tbody>
                                {state.rows.map(r => (
                                    <tr key={`${r.tenantId}-${r.type}`}>
                                        <td><code>{r.tenantId}</code></td>
                                        <td><span className="badge accent">{r.type}</span></td>
                                        <td>
                                            <span className={'status-dot ' + r.status} />
                                            {r.status}
                                        </td>
                                        <td className="muted">
                                            {r.target ? <code>{r.target}</code> : <span className="faint">—</span>}
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                </div>
            )}
        </>
    );
}

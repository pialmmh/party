import { useEffect, useState } from 'react';
import { partyApi, AdapterHealth } from '../api/partyClient';

type Loaded =
    | { kind: 'loading' }
    | { kind: 'ok'; rows: AdapterHealth[] }
    | { kind: 'error'; message: string };

function dotFor(status: string): string {
    switch (status.toLowerCase()) {
        case 'good':    return 'good';
        case 'warn':    return 'warn';
        case 'bad':     return 'bad';
        default:        return 'warn';
    }
}

export default function Adapters() {
    const [state, setState] = useState<Loaded>({ kind: 'loading' });

    async function load() {
        setState({ kind: 'loading' });
        try {
            const rows = await partyApi.adapterHealth();
            setState({ kind: 'ok', rows });
        } catch (e) {
            const msg = e instanceof Error ? e.message : String(e);
            setState({ kind: 'error', message: msg });
        }
    }

    useEffect(() => { void load(); }, []);

    return (
        <>
            <div className="page-header">
                <div>
                    <h1>Adapters</h1>
                    <div className="lede">
                        Live adapter health, fetched from
                        {' '}<code>{partyApi.base}/v2/health/adapters</code>.
                    </div>
                </div>
                <button onClick={load}>Refresh</button>
            </div>

            {state.kind === 'loading' && (
                <div className="panel">
                    <div className="panel-body muted">Loading…</div>
                </div>
            )}

            {state.kind === 'error' && (
                <div className="panel">
                    <div className="panel-header">
                        <h2>Backend unreachable</h2>
                        <span className="badge warn">offline</span>
                    </div>
                    <div className="panel-body">
                        <p className="muted" style={{ marginTop: 0 }}>
                            Could not reach the Party API. Start it with:
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
                        <p className="muted" style={{ fontSize: 12 }}>
                            Error: <code>{state.message}</code>
                        </p>
                    </div>
                </div>
            )}

            {state.kind === 'ok' && (
                <>
                    <div className="cards">
                        {state.rows.map(r => (
                            <div key={`${r.tenantId}-${r.type}`} className="card">
                                <div className="label">{r.tenantId} · {r.type}</div>
                                <div className="value" style={{ fontSize: 18 }}>
                                    <span className={'status-dot ' + dotFor(r.status)} />
                                    {r.status}
                                </div>
                                <div className="sub">{r.target ?? 'no target configured'}</div>
                            </div>
                        ))}
                    </div>

                    <div className="panel">
                        <div className="panel-header">
                            <h2>Details</h2>
                            <span className="badge accent">{state.rows.length} configured</span>
                        </div>
                        <div className="panel-body flush">
                            <table>
                                <thead>
                                    <tr>
                                        <th style={{ width: 90 }}>Tenant</th>
                                        <th style={{ width: 120 }}>Type</th>
                                        <th style={{ width: 110 }}>Status</th>
                                        <th>Target</th>
                                        <th>Error</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {state.rows.map(r => (
                                        <tr key={`${r.tenantId}-${r.type}-row`}>
                                            <td><code>{r.tenantId}</code></td>
                                            <td><span className="badge accent">{r.type}</span></td>
                                            <td>
                                                <span className={'status-dot ' + dotFor(r.status)} />
                                                {r.status}
                                            </td>
                                            <td className="muted">
                                                {r.target ? <code>{r.target}</code> : <span className="faint">—</span>}
                                            </td>
                                            <td className="muted">
                                                {r.error ?? <span className="faint">—</span>}
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    </div>
                </>
            )}
        </>
    );
}

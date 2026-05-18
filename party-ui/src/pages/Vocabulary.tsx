import { useEffect, useState } from 'react';
import { partyApi, EntityMeta } from '../api/partyClient';

type Loaded =
    | { kind: 'loading' }
    | { kind: 'ok'; rows: EntityMeta[] }
    | { kind: 'error'; message: string };

const TENANT = 't1';

export default function Vocabulary() {
    const [state, setState] = useState<Loaded>({ kind: 'loading' });

    async function load() {
        setState({ kind: 'loading' });
        try {
            const rows = await partyApi.entities(TENANT);
            setState({ kind: 'ok', rows });
        } catch (e) {
            setState({ kind: 'error', message: e instanceof Error ? e.message : String(e) });
        }
    }

    useEffect(() => { void load(); }, []);

    return (
        <>
            <div className="page-header">
                <div>
                    <h1>Vocabulary</h1>
                    <div className="lede">
                        Entities exposed by tenant <code>{TENANT}</code>'s repo adapter.
                        Discovered by introspecting the source at startup. Used as the
                        field vocabulary for the policy builder.
                    </div>
                </div>
                <button onClick={load}>Refresh</button>
            </div>

            {state.kind === 'loading' && (
                <div className="panel"><div className="panel-body muted">Loading…</div></div>
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

            {state.kind === 'ok' && state.rows.length === 0 && (
                <div className="panel">
                    <div className="panel-header">
                        <h2>No entities configured</h2>
                    </div>
                    <div className="panel-body muted">
                        Add an <code>entities</code> list under your adapter config in{' '}
                        <code>profile-dev.yml</code>:
                        <pre style={{
                            background: 'var(--bg)',
                            border: '1px solid var(--border)',
                            padding: 10,
                            borderRadius: 4,
                            fontSize: 12,
                            marginTop: 10,
                            overflowX: 'auto',
                        }}>{`party:
  v2:
    tenants:
      t1:
        odoo:
          entities:
            - res.users
            - res.groups
            - res.partner`}</pre>
                    </div>
                </div>
            )}

            {state.kind === 'ok' && state.rows.length > 0 && (
                <>
                    {state.rows.some(r => r.fields.length === 0) && (
                        <div className="panel">
                            <div className="panel-header">
                                <h2>Introspection incomplete</h2>
                                <span className="badge warn">no admin creds</span>
                            </div>
                            <div className="panel-body muted">
                                Some entities have empty field lists because the adapter
                                couldn't authenticate to the source for introspection.
                                Set <code>admin-user</code> + <code>admin-password</code>{' '}
                                in the adapter config and restart.
                            </div>
                        </div>
                    )}

                    {state.rows.map(e => (
                        <div key={e.name} className="panel">
                            <div className="panel-header">
                                <h2>{e.name}</h2>
                                <span className="badge accent">
                                    {e.source} · {e.fields.length} field{e.fields.length === 1 ? '' : 's'}
                                </span>
                            </div>
                            {e.fields.length === 0 ? (
                                <div className="panel-body muted">
                                    No fields introspected.
                                </div>
                            ) : (
                                <div className="panel-body flush">
                                    <table>
                                        <thead>
                                            <tr>
                                                <th style={{ width: 220 }}>Name</th>
                                                <th style={{ width: 140 }}>Type</th>
                                                <th style={{ width: 100 }}>Required</th>
                                                <th style={{ width: 200 }}>Relation</th>
                                                <th>Label</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            {e.fields.map(f => (
                                                <tr key={f.name}>
                                                    <td><code>{f.name}</code></td>
                                                    <td><span className="badge">{f.type}</span></td>
                                                    <td>{f.required ? 'yes' : <span className="faint">—</span>}</td>
                                                    <td>{f.relation ? <code>{f.relation}</code> : <span className="faint">—</span>}</td>
                                                    <td className="muted">{f.label ?? <span className="faint">—</span>}</td>
                                                </tr>
                                            ))}
                                        </tbody>
                                    </table>
                                </div>
                            )}
                        </div>
                    ))}
                </>
            )}
        </>
    );
}

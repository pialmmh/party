import { useState } from 'react';
import { partyApi, ValidateResponse } from '../api/partyClient';

type Loaded =
    | { kind: 'idle' }
    | { kind: 'loading' }
    | { kind: 'done'; resp: ValidateResponse }
    | { kind: 'error'; message: string };

export default function Users() {
    const [tenant, setTenant] = useState('t1');
    const [login, setLogin] = useState('');
    const [password, setPassword] = useState('');
    const [state, setState] = useState<Loaded>({ kind: 'idle' });

    async function submit(e: React.FormEvent) {
        e.preventDefault();
        setState({ kind: 'loading' });
        try {
            const resp = await partyApi.validate({ tenantId: tenant, login, password });
            setState({ kind: 'done', resp });
        } catch (err) {
            const msg = err instanceof Error ? err.message : String(err);
            setState({ kind: 'error', message: msg });
        }
    }

    return (
        <>
            <div className="page-header">
                <div>
                    <h1>Users</h1>
                    <div className="lede">
                        Validate credentials end-to-end through Party's policy chain. The
                        request hits <code>POST /v2/auth/validate</code> → first policy
                        (<code>basic-auth</code>) → tenant's <code>UserRepoAdapter</code>.
                    </div>
                </div>
            </div>

            <div className="panel">
                <div className="panel-header">
                    <h2>Validate credentials</h2>
                    <span className="badge">live call</span>
                </div>
                <div className="panel-body">
                    <form onSubmit={submit} className="form-section">
                        <div className="form-row">
                            <label htmlFor="tenant">Tenant</label>
                            <input id="tenant"
                                   value={tenant}
                                   onChange={e => setTenant(e.target.value)}
                                   placeholder="t1" />
                        </div>
                        <div className="form-row">
                            <label htmlFor="login">Login</label>
                            <input id="login"
                                   autoComplete="username"
                                   value={login}
                                   onChange={e => setLogin(e.target.value)}
                                   placeholder="admin or user@example.com" />
                        </div>
                        <div className="form-row">
                            <label htmlFor="password">Password</label>
                            <input id="password"
                                   type="password"
                                   autoComplete="current-password"
                                   value={password}
                                   onChange={e => setPassword(e.target.value)} />
                        </div>
                        <div className="form-row">
                            <label />
                            <div>
                                <button type="submit"
                                        className="primary"
                                        disabled={state.kind === 'loading'}>
                                    {state.kind === 'loading' ? 'Validating…' : 'Validate'}
                                </button>
                            </div>
                        </div>
                    </form>
                </div>
            </div>

            {state.kind === 'done' && (
                <div className="panel">
                    <div className="panel-header">
                        <h2>Result</h2>
                        <span className={state.resp.valid ? 'badge good' : 'badge warn'}>
                            {state.resp.valid ? 'valid' : 'invalid'}
                        </span>
                    </div>
                    <div className="panel-body">
                        <div className="form-section">
                            <div className="form-row">
                                <label>Decision</label>
                                <div>
                                    <span className={'status-dot ' + (state.resp.valid ? 'good' : 'bad')} />
                                    {state.resp.valid ? 'PASS' : 'REJECT'}
                                    {state.resp.policyName && (
                                        <span className="faint" style={{ marginLeft: 10 }}>
                                            (policy: <code>{state.resp.policyName}</code>)
                                        </span>
                                    )}
                                </div>
                            </div>
                            {state.resp.reason && (
                                <div className="form-row">
                                    <label>Reason</label>
                                    <div className="muted">{state.resp.reason}</div>
                                </div>
                            )}
                            {state.resp.user && (
                                <>
                                    <div className="form-row">
                                        <label>External ID</label>
                                        <div><code>{state.resp.user.externalId}</code></div>
                                    </div>
                                    <div className="form-row">
                                        <label>Display name</label>
                                        <div>{state.resp.user.displayName ?? <span className="faint">—</span>}</div>
                                    </div>
                                    <div className="form-row">
                                        <label>Email</label>
                                        <div>{state.resp.user.email ?? <span className="faint">—</span>}</div>
                                    </div>
                                    <div className="form-row">
                                        <label>Active</label>
                                        <div>{state.resp.user.active ? 'yes' : 'no'}</div>
                                    </div>
                                    <div className="form-row">
                                        <label>Roles</label>
                                        <div>
                                            {state.resp.user.roles.length === 0
                                                ? <span className="faint">none</span>
                                                : state.resp.user.roles.map(r => (
                                                    <span key={r} className="badge accent" style={{ marginRight: 4 }}>{r}</span>
                                                ))}
                                        </div>
                                    </div>
                                </>
                            )}
                        </div>
                    </div>
                </div>
            )}

            {state.kind === 'error' && (
                <div className="panel">
                    <div className="panel-header">
                        <h2>Request failed</h2>
                        <span className="badge warn">network</span>
                    </div>
                    <div className="panel-body muted">
                        <code>{state.message}</code>
                    </div>
                </div>
            )}

            <div className="panel">
                <div className="panel-header">
                    <h2>Resolution path</h2>
                </div>
                <div className="panel-body flush">
                    <table>
                        <thead>
                            <tr>
                                <th style={{ width: 50 }}>#</th>
                                <th>Step</th>
                                <th>Actor</th>
                            </tr>
                        </thead>
                        <tbody>
                            <tr><td>1</td><td>UI submits validate request</td><td className="muted">Browser</td></tr>
                            <tr><td>2</td><td>Party builds policy context, looks up tenant's adapter</td><td className="muted">Party API · TenantRegistry</td></tr>
                            <tr><td>3</td><td><code>basic-auth</code> policy calls adapter.authenticate()</td><td className="muted">PolicyChain · BasicAuthPolicy</td></tr>
                            <tr><td>4</td><td>OdooAdapter sends JSON-RPC <code>common.authenticate</code></td><td className="muted">OdooUserRepoAdapter</td></tr>
                            <tr><td>5</td><td>On uid &gt; 0, fetches profile via <code>execute_kw res.users.read</code></td><td className="muted">OdooUserRepoAdapter</td></tr>
                            <tr><td>6</td><td>Result returned, no Party-side persistence</td><td className="muted">Party API</td></tr>
                        </tbody>
                    </table>
                </div>
            </div>
        </>
    );
}

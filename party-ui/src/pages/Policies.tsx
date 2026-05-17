type Policy = {
    name: string;
    kind: 'basic-auth' | 'cedar' | 'rate-limit' | 'custom';
    enabled: boolean;
    order: number;
    summary: string;
};

const chain: Policy[] = [
    {
        name: 'basic-auth',
        kind: 'basic-auth',
        enabled: true,
        order: 10,
        summary:
            'Default. Resolves the user via the tenant adapter and verifies the password. ' +
            'Rejects on bad credentials.',
    },
];

export default function Policies() {
    return (
        <>
            <div className="page-header">
                <div>
                    <h1>Policies</h1>
                    <div className="lede">
                        Each request runs through this chain in order. The default chain has{' '}
                        <strong>basic-auth</strong> only; richer policies (Cedar) plug in after.
                    </div>
                </div>
                <button>+ Add policy</button>
            </div>

            <div className="panel">
                <div className="panel-header">
                    <h2>Chain</h2>
                    <span className="badge">configured in profile-dev.yml</span>
                </div>
                <div className="panel-body flush">
                    <table>
                        <thead>
                            <tr>
                                <th style={{ width: 60 }}>Order</th>
                                <th style={{ width: 160 }}>Name</th>
                                <th style={{ width: 120 }}>Kind</th>
                                <th style={{ width: 110 }}>State</th>
                                <th>Summary</th>
                            </tr>
                        </thead>
                        <tbody>
                            {chain.map(p => (
                                <tr key={p.name}>
                                    <td><code>{p.order}</code></td>
                                    <td><strong>{p.name}</strong></td>
                                    <td><span className="badge accent">{p.kind}</span></td>
                                    <td>
                                        <span className={'status-dot ' + (p.enabled ? 'good' : 'warn')} />
                                        {p.enabled ? 'enabled' : 'disabled'}
                                    </td>
                                    <td className="muted">{p.summary}</td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            </div>

            <div className="panel">
                <div className="panel-header">
                    <h2>How the chain runs</h2>
                </div>
                <div className="panel-body">
                    <ol className="muted" style={{ margin: 0, paddingLeft: 18 }}>
                        <li>Party API receives the request from Keycloak SPI or APISIX.</li>
                        <li>Party builds a <code>PolicyContext</code> (tenant, action, claims).</li>
                        <li>Each enabled policy in the chain runs in <code>order</code> ascending.</li>
                        <li>First policy to reject ends the chain. All pass → request allowed.</li>
                    </ol>
                </div>
            </div>
        </>
    );
}

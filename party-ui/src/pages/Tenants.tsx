type Row = {
    id: string;
    name: string;
    repoType: 'odoo' | 'routesphere' | 'ldap' | 'custom';
    target: string;
    status: 'active' | 'draining' | 'readonly';
};

const rows: Row[] = [
    { id: 't1', name: 'BTCL primary', repoType: 'odoo', target: 'http://10.10.199.41:7170 · platform', status: 'active' },
];

export default function Tenants() {
    return (
        <>
            <div className="page-header">
                <div>
                    <h1>Tenants</h1>
                    <div className="lede">
                        Each tenant maps to one user repository adapter. The mapping lives in
                        the profile YAML.
                    </div>
                </div>
                <button className="primary">+ New tenant</button>
            </div>

            <div className="panel">
                <div className="panel-body flush">
                    <table>
                        <thead>
                            <tr>
                                <th style={{ width: 80 }}>ID</th>
                                <th>Name</th>
                                <th style={{ width: 130 }}>Repo type</th>
                                <th>Target</th>
                                <th style={{ width: 110 }}>Status</th>
                            </tr>
                        </thead>
                        <tbody>
                            {rows.map(r => (
                                <tr key={r.id}>
                                    <td><code>{r.id}</code></td>
                                    <td>{r.name}</td>
                                    <td><span className="badge accent">{r.repoType}</span></td>
                                    <td className="muted"><code>{r.target}</code></td>
                                    <td>
                                        <span className={'status-dot ' + (r.status === 'active' ? 'good' : 'warn')} />
                                        {r.status}
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            </div>
        </>
    );
}

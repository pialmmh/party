import { useLocation } from 'react-router-dom';

const titles: Record<string, string> = {
    '/':         'Dashboard',
    '/tenants':  'Tenants',
    '/users':    'Users',
    '/adapters': 'Adapters',
    '/policies': 'Policies',
    '/settings': 'Settings',
};

export default function TopBar() {
    const loc = useLocation();
    const title = titles[loc.pathname] ?? 'Party';
    return (
        <header className="topbar">
            <div className="crumbs">
                <span className="faint">btcl</span> / <span className="faint">dev</span> / <strong>{title}</strong>
            </div>
            <div className="tenant-pill">
                <span className="dot" />
                tenant: <strong style={{ color: 'var(--text)', marginLeft: 4 }}>t1</strong>
            </div>
        </header>
    );
}

import { NavLink } from 'react-router-dom';

type Item = { to: string; label: string; icon: string };

const items: Item[] = [
    { to: '/',           label: 'Dashboard',  icon: '◇' },
    { to: '/tenants',    label: 'Tenants',    icon: '⌂' },
    { to: '/users',      label: 'Users',      icon: '☺' },
    { to: '/adapters',   label: 'Adapters',   icon: '⇄' },
    { to: '/vocabulary', label: 'Vocabulary', icon: '▦' },
    { to: '/policies',   label: 'Policies',   icon: '⚑' },
    { to: '/settings',   label: 'Settings',   icon: '⚙' },
];

export default function Sidebar() {
    return (
        <aside className="sidebar">
            <div className="sidebar-header">
                <div className="brand">Party</div>
                <div className="subtitle">Identity &amp; Authz</div>
            </div>
            <nav className="sidebar-nav">
                {items.map(it => (
                    <NavLink key={it.to} to={it.to} end={it.to === '/'}>
                        <span className="icon">{it.icon}</span>
                        <span>{it.label}</span>
                    </NavLink>
                ))}
            </nav>
            <div className="sidebar-footer">
                v0.1.0 · dev
            </div>
        </aside>
    );
}

import { Routes, Route, Navigate } from 'react-router-dom';
import Shell from './layout/Shell';
import Dashboard from './pages/Dashboard';
import Tenants from './pages/Tenants';
import Users from './pages/Users';
import Adapters from './pages/Adapters';
import Vocabulary from './pages/Vocabulary';
import Policies from './pages/Policies';
import Settings from './pages/Settings';

export default function App() {
    return (
        <Routes>
            <Route element={<Shell />}>
                <Route index element={<Dashboard />} />
                <Route path="tenants" element={<Tenants />} />
                <Route path="users" element={<Users />} />
                <Route path="adapters" element={<Adapters />} />
                <Route path="vocabulary" element={<Vocabulary />} />
                <Route path="policies" element={<Policies />} />
                <Route path="settings" element={<Settings />} />
            </Route>
            <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
    );
}

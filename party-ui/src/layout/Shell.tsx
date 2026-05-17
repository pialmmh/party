import { Outlet } from 'react-router-dom';
import Sidebar from './Sidebar';
import TopBar from './TopBar';

export default function Shell() {
    return (
        <div className="app">
            <Sidebar />
            <div className="main">
                <TopBar />
                <main className="content">
                    <Outlet />
                </main>
            </div>
        </div>
    );
}

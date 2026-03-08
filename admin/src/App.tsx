import { BrowserRouter, Routes, Route, NavLink, Navigate, useLocation } from 'react-router-dom';
import { useState, useEffect } from 'react';
import { AuthProvider, useAuth } from './context/AuthContext';
import useWebSocket from './hooks/useWebSocket';
import LoginPage from './pages/LoginPage';
import CommandPage from './pages/CommandPage';
import AnalyticsPage from './pages/AnalyticsPage';
import BlockchainPage from './pages/BlockchainPage';
import UsersPage from './pages/UsersPage';
import HospitalsPage from './pages/HospitalsPage';
import SettingsPage from './pages/SettingsPage';
import LogsPage from './pages/LogsPage';
import ErrorBoundary from './components/ErrorBoundary';

const ProtectedRoute = ({ children }) => {
    const { user, loading } = useAuth();
    const location = useLocation();

    if (loading) return <div style={{ color: 'var(--color-text-muted)', padding: 32 }}>Loading session...</div>;
    if (!user) return <Navigate to="/login" state={{ from: location }} replace />;

    if (user.role !== 'command_center') {
        return (
            <div className="login-page" style={{ flexDirection: 'column', gap: 16 }}>
                <div className="login-card" style={{ textAlign: 'center' }}>
                    <div style={{ fontSize: 24, marginBottom: 12, color: 'var(--color-critical)' }}>ACCESS DENIED</div>
                    <h2 style={{ color: 'var(--color-critical)', fontSize: 18, fontWeight: 700, marginBottom: 8 }}>Access Denied</h2>
                    <p style={{ color: 'var(--color-text-muted)', fontSize: 13, marginBottom: 20 }}>
                        This portal is restricted to Command Center administrators.
                    </p>
                    <button className="btn btn-primary" onClick={() => { localStorage.clear(); window.location.href = '/login'; }}>
                        Sign out & return to login
                    </button>
                </div>
            </div>
        );
    }

    return children;
};

function AppContent() {
    const { user, logout } = useAuth();
    const { connected, on } = useWebSocket();
    const [theme, setTheme] = useState(localStorage.getItem('aerovhyn-theme') || 'dark');

    useEffect(() => {
        document.documentElement.setAttribute('data-theme', theme);
        localStorage.setItem('aerovhyn-theme', theme);
        // Remove no-transition guard
        requestAnimationFrame(() => document.documentElement.classList.remove('no-transition'));
    }, [theme]);

    const toggleTheme = () => setTheme(t => t === 'dark' ? 'light' : 'dark');

    const navItems = user && user.role === 'command_center'
        ? [
            { to: '/command', label: 'Command' },
            { to: '/analytics', label: 'Analytics' },
            { to: '/blockchain', label: 'Audit Trail' },
            { to: '/hospitals', label: 'Hospitals' },
            { to: '/users', label: 'Users' },
            { to: '/settings', label: 'Settings' },
            { to: '/logs', label: 'Logs' },
        ]
        : [];

    return (
        <div className="app-layout">
            {user && user.role === 'command_center' && (
                <nav className="app-nav">
                    <div className="nav-brand">
                        <div className="nav-brand-dot" />
                        AEROVHYN
                        <span className="nav-brand-tag">ADMIN</span>
                    </div>

                    <div className="nav-links">
                        {navItems.map(({ to, label }) => (
                            <NavLink key={to} to={to}
                                className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}>
                                {label}
                            </NavLink>
                        ))}
                    </div>

                    <div className="nav-right">
                        <div className={`nav-ws-status ${connected ? 'connected' : 'disconnected'}`}>
                            <div className="nav-ws-dot" />
                            {connected ? 'LIVE' : 'OFFLINE'}
                        </div>

                        <button className="theme-btn" onClick={toggleTheme} title="Toggle theme">
                            {theme === 'dark' ? 'Light' : 'Dark'}
                        </button>

                        <span className="nav-user">{user.full_name || user.username}</span>
                        <button className="nav-btn" onClick={logout}>Sign out</button>
                    </div>
                </nav>
            )}

            <main style={{ flex: 1 }}>
                <Routes>
                    <Route path="/login" element={user ? <Navigate to="/command" replace /> : <LoginPage />} />
                    <Route path="/" element={user ? <Navigate to="/command" replace /> : <Navigate to="/login" replace />} />
                    <Route path="/command" element={<ProtectedRoute><CommandPage ws={{ connected, on }} /></ProtectedRoute>} />
                    <Route path="/analytics" element={<ProtectedRoute><AnalyticsPage /></ProtectedRoute>} />
                    <Route path="/blockchain" element={<ProtectedRoute><BlockchainPage /></ProtectedRoute>} />
                    <Route path="/hospitals" element={<ProtectedRoute><HospitalsPage ws={{ connected, on }} /></ProtectedRoute>} />
                    <Route path="/users" element={<ProtectedRoute><UsersPage /></ProtectedRoute>} />
                    <Route path="/settings" element={<ProtectedRoute><SettingsPage /></ProtectedRoute>} />
                    <Route path="/logs" element={<ProtectedRoute><LogsPage /></ProtectedRoute>} />
                    <Route path="*" element={<Navigate to="/" replace />} />
                </Routes>
            </main>
        </div>
    );
}

export default function App() {
    return (
        <ErrorBoundary>
            <BrowserRouter>
                <AuthProvider>
                    <AppContent />
                </AuthProvider>
            </BrowserRouter>
        </ErrorBoundary>
    );
}

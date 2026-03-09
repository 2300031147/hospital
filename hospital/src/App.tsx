import { BrowserRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { useState, useEffect } from 'react';
import { AuthProvider, useAuth } from './context/AuthContext';
import useWebSocket from './hooks/useWebSocket';
import LoginPage from './pages/LoginPage';
import HospitalPage from './pages/HospitalPage';

const ProtectedRoute = ({ children }) => {
    const { user, loading } = useAuth();
    const location = useLocation();

    if (loading) return <div style={{ color: 'var(--color-text-muted)', padding: 32 }}>Loading session...</div>;
    if (!user) return <Navigate to="/login" state={{ from: location }} replace />;

    if (user.role !== 'hospital_admin') {
        return (
            <div className="login-page" style={{ flexDirection: 'column', gap: 16 }}>
                <div className="login-card" style={{ textAlign: 'center' }}>
                    <div style={{ fontSize: 40, marginBottom: 12, fontWeight: 800 }}>X</div>
                    <h2 style={{ color: 'var(--color-critical)', fontSize: 18, fontWeight: 700, marginBottom: 8 }}>Access Denied</h2>
                    <p style={{ color: 'var(--color-text-muted)', fontSize: 13, marginBottom: 20 }}>
                        This portal is restricted to Hospital Administrators.
                    </p>
                    <button className="btn btn-primary" onClick={() => { 
                        localStorage.removeItem('hosp_token'); 
                        localStorage.removeItem('hosp_role'); 
                        localStorage.removeItem('hosp_hospital_id'); 
                        localStorage.removeItem('hosp_username'); 
                        localStorage.removeItem('hosp_full_name'); 
                        window.location.href = '/login'; 
                    }}>
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
    const [alerts, setAlerts] = useState([]);

    useEffect(() => {
        document.documentElement.setAttribute('data-theme', theme);
        localStorage.setItem('aerovhyn-theme', theme);
        requestAnimationFrame(() => document.documentElement.classList.remove('no-transition'));
    }, [theme]);

    const toggleTheme = () => setTheme(t => t === 'dark' ? 'light' : 'dark');

    // Listen for real-time alerts
    useEffect(() => {
        if (!on) return;
        const unsubs = [
            on('handoff_alert', (data) => {
                const h = data.handoff;
                if (h && user?.hospital_id && h.hospital_id === user.hospital_id) {
                    setAlerts(prev => [{ id: Date.now(), type: 'handoff', text: `Incoming patient! AMB-${h.ambulance_id} — ${h.severity?.level?.toUpperCase()} — ETA ${h.eta_minutes?.toFixed(0)} min` }, ...prev].slice(0, 5));
                }
            }),
            on('patient_accepted', (data) => {
                setAlerts(prev => [{ id: Date.now(), type: 'accept', text: `Patient #${data.ambulance_id} accepted — bed locked` }, ...prev].slice(0, 5));
            }),
            on('alert', (data) => {
                setAlerts(prev => [{ id: Date.now(), type: 'info', text: data.message }, ...prev].slice(0, 5));
            }),
        ];
        return () => unsubs.forEach(u => u?.());
    }, [on, user]);

    return (
        <div className="app-layout">
            {user && user.role === 'hospital_admin' && (
                <nav className="app-nav">
                    <div className="nav-brand">
                        <div className="nav-brand-dot" style={{ background: 'var(--color-success)' }} />
                        AEROVHYN
                        <span className="nav-brand-tag" style={{ background: 'var(--color-success-dim)', color: 'var(--color-success)' }}>HOSPITAL</span>
                    </div>

                    <div style={{ flex: 1 }} />

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

            {/* Alerts Banner */}
            {alerts.length > 0 && (
                <div style={{ padding: '8px 20px', display: 'flex', flexDirection: 'column', gap: 4 }}>
                    {alerts.map(a => (
                        <div key={a.id} style={{
                            padding: '8px 14px', borderRadius: 'var(--radius-sm)', fontSize: 12, fontWeight: 500,
                            display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                            background: a.type === 'handoff' ? 'var(--color-critical-dim)' : a.type === 'accept' ? 'var(--color-success-dim)' : 'var(--color-accent-dim)',
                            color: a.type === 'handoff' ? 'var(--color-critical)' : a.type === 'accept' ? 'var(--color-success)' : 'var(--color-accent)',
                        }}>
                            <span>{a.text}</span>
                            <button onClick={() => setAlerts(prev => prev.filter(x => x.id !== a.id))}
                                style={{ background: 'none', border: 'none', color: 'inherit', cursor: 'pointer', fontSize: 14 }}>✕</button>
                        </div>
                    ))}
                </div>
            )}

            <main style={{ flex: 1 }}>
                <Routes>
                    <Route path="/login" element={user ? <Navigate to="/hospital" replace /> : <LoginPage />} />
                    <Route path="/" element={user ? <Navigate to="/hospital" replace /> : <Navigate to="/login" replace />} />
                    <Route path="/hospital" element={
                        <ProtectedRoute>
                            <HospitalPage ws={{ connected, on }} user={user} />
                        </ProtectedRoute>
                    } />
                    <Route path="*" element={<Navigate to="/" replace />} />
                </Routes>
            </main>
        </div>
    );
}

export default function App() {
    return (
        <BrowserRouter>
            <AuthProvider>
                <AppContent />
            </AuthProvider>
        </BrowserRouter>
    );
}

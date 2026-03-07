import { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { loginUser } from '../services/api';

export default function LoginPage() {
    const { login } = useAuth();
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');
        setLoading(true);
        try {
            const data = await loginUser(username, password);
            if (data.role !== 'hospital_admin') {
                setError('Access restricted to Hospital Administrators.');
                setLoading(false);
                return;
            }
            login(data); // Use AuthContext login() — handles localStorage + state
        } catch (err) {
            setError(err.message || 'Authentication failed');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="login-page">
            <div className="login-card fade-in">
                <div className="login-brand">
                    <div className="login-brand-icon" style={{ background: 'var(--color-success-dim)', borderColor: 'var(--color-success)', color: 'var(--color-success)' }}>+</div>
                    <div className="login-brand-name">AEROVHYN</div>
                    <div className="login-brand-sub">Hospital Administration Portal</div>
                </div>

                {error && (
                    <div className="login-error">
                        <span className="login-error-text">Error: {error}</span>
                    </div>
                )}

                <form onSubmit={handleSubmit}>
                    <div style={{ marginBottom: 14 }}>
                        <label className="label">Username</label>
                        <input className="input" type="text" value={username} onChange={e => setUsername(e.target.value)}
                            placeholder="hosp1" autoFocus required />
                    </div>
                    <div style={{ marginBottom: 6 }}>
                        <label className="label">Password</label>
                        <input className="input" type="password" value={password} onChange={e => setPassword(e.target.value)}
                            placeholder="••••••••" required />
                    </div>
                    <button className="login-btn" style={{ background: 'var(--color-success)' }} type="submit" disabled={loading}>
                        {loading ? 'Authenticating...' : 'Sign in to Hospital Portal'}
                    </button>
                </form>

                <div className="login-footer">
                    <div className="login-footer-badges">
                        <span className="login-footer-badge">AES-256</span>
                        <span className="login-footer-badge">JWT</span>
                        <span className="login-footer-badge">RBAC</span>
                    </div>
                    <div className="login-footer-text">AEROVHYN v2.1 — Zero-Trust Architecture</div>
                </div>
            </div>
        </div>
    );
}

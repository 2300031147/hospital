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
            if (data.role !== 'command_center') {
                setError('Access restricted to Command Center administrators.');
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
                    <div className="login-brand-icon">+</div>
                    <div className="login-brand-name">AEROVHYN</div>
                    <div className="login-brand-sub">Command Center — Admin Portal</div>
                </div>

                {error && (
                    <div className="login-error">
                        <span className="login-error-text">{error}</span>
                    </div>
                )}

                <form onSubmit={handleSubmit}>
                    <div style={{ marginBottom: 14 }}>
                        <label className="label">Username</label>
                        <input className="input" type="text" value={username} onChange={e => setUsername(e.target.value)}
                            placeholder="admin" autoFocus required />
                    </div>
                    <div style={{ marginBottom: 6 }}>
                        <label className="label">Password</label>
                        <input className="input" type="password" value={password} onChange={e => setPassword(e.target.value)}
                            placeholder="••••••••" required />
                    </div>
                    <button className="login-btn" type="submit" disabled={loading}>
                        {loading ? 'Authenticating...' : 'Sign in to Command Center'}
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

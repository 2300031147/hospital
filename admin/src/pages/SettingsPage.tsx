import { useState, useEffect } from 'react';
import toast from 'react-hot-toast';
import { getSettings, updateSettings } from '../services/api';

export default function SettingsPage() {
    const [settings, setSettings] = useState({ distance_weight: 0.2, readiness_weight: 0.5, severity_match_weight: 0.3, max_routing_distance_km: 30 });
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [saved, setSaved] = useState(false);

    useEffect(() => {
        (async () => {
            try { setSettings(await getSettings()); } catch (e) { console.error(e); }
            finally { setLoading(false); }
        })();
    }, []);

    const handleSave = async () => {
        setSaving(true);
        try {
            await updateSettings(settings);
            setSaved(true);
            setTimeout(() => setSaved(false), 3000);
        } catch (e) { toast.error(e.message); }
        finally { setSaving(false); }
    };

    const total = settings.distance_weight + settings.readiness_weight + settings.severity_match_weight;

    if (loading) return <div className="page fade-in"><p style={{ color: 'var(--color-text-muted)' }}>Loading settings...</p></div>;

    return (
        <div className="page fade-in">
            <div className="page-header">
                <h1 className="page-title">System Configuration</h1>
                <p className="page-subtitle">Routing engine parameters and system preferences</p>
            </div>

            <div className="grid grid-2">
                {/* Routing Weights */}
                <div className="card">
                    <div className="card-header">
                        <span className="card-title">Routing Weights</span>
                        <span className="badge" style={{
                            background: Math.abs(total - 1) < 0.01 ? 'var(--color-success-dim)' : 'var(--color-warning-dim)',
                            color: Math.abs(total - 1) < 0.01 ? 'var(--color-success)' : 'var(--color-warning)',
                        }}>
                            Total: {(total * 100).toFixed(0)}%
                        </span>
                    </div>

                    {[
                        { key: 'distance_weight', label: 'Distance Weight', color: 'var(--color-info)', icon: '' },
                        { key: 'readiness_weight', label: 'Readiness Weight', color: 'var(--color-accent)', icon: '' },
                        { key: 'severity_match_weight', label: 'Severity Match Weight', color: 'var(--color-warning)', icon: '' },
                    ].map(({ key, label, color, icon }) => (
                        <div key={key} style={{ marginBottom: 16 }}>
                            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6 }}>
                                <span style={{ fontSize: 12, fontWeight: 500, color: 'var(--color-text-secondary)' }}>{icon} {label}</span>
                                <span style={{ fontSize: 13, fontWeight: 700, color, fontFamily: 'var(--font-mono)' }}>
                                    {(settings[key] * 100).toFixed(0)}%
                                </span>
                            </div>
                            <input type="range" min="0" max="1" step="0.05" value={settings[key]}
                                onChange={e => setSettings(s => ({ ...s, [key]: parseFloat(e.target.value) }))}
                                style={{ width: '100%', accentColor: color }} />
                        </div>
                    ))}
                </div>

                {/* Distance + Actions */}
                <div className="card">
                    <div className="card-title" style={{ marginBottom: 16 }}>Other Parameters</div>

                    <div style={{ marginBottom: 20 }}>
                        <label className="label">Max Routing Distance (km)</label>
                        <input className="input" type="number" step="1" value={settings.max_routing_distance_km}
                            onChange={e => setSettings(s => ({ ...s, max_routing_distance_km: Number(e.target.value) }))} />
                        <div style={{ fontSize: 10, color: 'var(--color-text-muted)', marginTop: 4 }}>
                            Hospitals beyond this distance will be excluded from routing
                        </div>
                    </div>

                    <button className="btn btn-primary" style={{ width: '100%', justifyContent: 'center' }}
                        onClick={handleSave} disabled={saving}>
                        {saving ? 'Saving...' : saved ? 'Saved!' : 'Save Configuration'}
                    </button>

                    {saved && (
                        <div style={{ marginTop: 8, padding: '8px 12px', background: 'var(--color-success-dim)', borderRadius: 'var(--radius-sm)', fontSize: 12, color: 'var(--color-success)', fontWeight: 600 }}>
                            Configuration saved and broadcast to all connected clients.
                        </div>
                    )}

                    <div style={{ marginTop: 24, padding: 16, background: 'var(--color-bg-tertiary)', borderRadius: 'var(--radius-md)', border: '1px solid var(--color-border)' }}>
                        <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--color-text-muted)', marginBottom: 8, letterSpacing: 0.5 }}>SYSTEM INFO</div>
                        {[
                            { label: 'Version', value: 'AEROVHYN v2.1.0' },
                            { label: 'Backend', value: 'Python FastAPI + SQLite' },
                            { label: 'Auth', value: 'JWT + RBAC + Rate Limiting' },
                            { label: 'Encryption', value: 'AES-256 / bcrypt' },
                            { label: 'Audit', value: 'SHA-256 Blockchain' },
                        ].map(({ label, value }) => (
                            <div key={label} style={{ display: 'flex', justifyContent: 'space-between', padding: '3px 0', fontSize: 11 }}>
                                <span style={{ color: 'var(--color-text-muted)' }}>{label}</span>
                                <span style={{ color: 'var(--color-text-secondary)', fontFamily: 'var(--font-mono)' }}>{value}</span>
                            </div>
                        ))}
                    </div>
                </div>
            </div>
        </div>
    );
}

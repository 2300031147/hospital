import { useState, useEffect, useCallback } from 'react';
import { getHospitals, getAmbulances } from '../services/api';
import LiveMap from '../components/LiveMap';

export default function CommandPage({ ws }) {
    const [hospitals, setHospitals] = useState([]);
    const [ambulances, setAmbulances] = useState([]);
    const [alerts, setAlerts] = useState([]);
    const [loading, setLoading] = useState(true);

    const fetchData = useCallback(async () => {
        try {
            const [h, a] = await Promise.all([getHospitals(), getAmbulances()]);
            setHospitals(h);
            setAmbulances(a);
        } catch (e) { console.error(e); }
        finally { setLoading(false); }
    }, []);

    useEffect(() => { fetchData(); }, [fetchData]);


    // Real-time WebSocket updates
    useEffect(() => {
        if (!ws?.on) return;
        const unsubs = [
            ws.on('ambulance_routed', (data) => {
                setAlerts(prev => [{ id: Date.now(), type: 'dispatch', text: `AMB-${data.ambulance_id} dispatched to ${data.hospital_name}` }, ...prev].slice(0, 8));
                fetchData();
            }),
            ws.on('reroute', (data) => {
                setAlerts(prev => [{ id: Date.now(), type: 'reroute', text: `AMB-${data.ambulance_id} rerouted to ${data.to_hospital_name}` }, ...prev].slice(0, 8));
                fetchData();
            }),
            ws.on('hospital_update', () => fetchData()),
            ws.on('alert', (data) => {
                setAlerts(prev => [{ id: Date.now(), type: 'alert', text: data.message }, ...prev].slice(0, 8));
            }),
        ];
        return () => unsubs.forEach(u => u?.());
    }, [ws, fetchData]);

    const sevColor = { critical: 'var(--color-critical)', high: '#ff6b35', medium: 'var(--color-warning)', low: 'var(--color-success)' };

    if (loading) return <div className="page fade-in"><p style={{ color: 'var(--color-text-muted)' }}>Loading command center...</p></div>;

    const enRouteCount = ambulances.filter(a => a.status === 'en_route').length;
    const activeCount = ambulances.filter(a => a.status !== 'idle').length;
    const avgUtil = hospitals.length > 0 ? (hospitals.reduce((s, h) => s + (h.max_capacity > 0 ? h.current_load / h.max_capacity : 0), 0) / hospitals.length * 100) : 0;
    const criticalHospitals = hospitals.filter(h => h.max_capacity > 0 && (h.current_load / h.max_capacity) > 0.85).length;

    return (
        <div className="page fade-in">
            <div className="page-header">
                <h1 className="page-title">Command Center</h1>
                <p className="page-subtitle">Real-time monitoring of ambulance fleet and hospital network</p>
            </div>

            {/* Live Alerts Feed */}
            {alerts.length > 0 && (
                <div style={{ marginBottom: 16, display: 'flex', flexDirection: 'column', gap: 4 }}>
                    {alerts.map(a => (
                        <div key={a.id} style={{
                            padding: '8px 14px', borderRadius: 'var(--radius-sm)', fontSize: 12, fontWeight: 500,
                            display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                            background: a.type === 'dispatch' ? 'var(--color-info-dim)' : a.type === 'reroute' ? 'var(--color-warning-dim)' : 'var(--color-accent-dim)',
                            color: a.type === 'dispatch' ? 'var(--color-info)' : a.type === 'reroute' ? 'var(--color-warning)' : 'var(--color-accent)',
                        }}>
                            <span>{a.text}</span>
                            <button onClick={() => setAlerts(prev => prev.filter(x => x.id !== a.id))}
                                style={{ background: 'none', border: 'none', color: 'inherit', cursor: 'pointer', fontSize: 14 }}>×</button>
                        </div>
                    ))}
                </div>
            )}

            {/* KPI Cards */}
            <div className="grid grid-4" style={{ marginBottom: 20 }}>
                <div className="stat-card">
                    <div className="stat-label">Active Ambulances</div>
                    <div className="stat-value" style={{ color: 'var(--color-info)' }}>{activeCount}</div>
                </div>
                <div className="stat-card">
                    <div className="stat-label">En Route</div>
                    <div className="stat-value" style={{ color: 'var(--color-warning)' }}>{enRouteCount}</div>
                </div>
                <div className="stat-card">
                    <div className="stat-label">Avg Utilization</div>
                    <div className="stat-value" style={{ color: avgUtil > 85 ? 'var(--color-critical)' : avgUtil > 60 ? 'var(--color-warning)' : 'var(--color-success)' }}>
                        {avgUtil.toFixed(0)}%
                    </div>
                </div>
                <div className="stat-card">
                    <div className="stat-label">Critical Facilities</div>
                    <div className="stat-value" style={{ color: criticalHospitals > 0 ? 'var(--color-critical)' : 'var(--color-success)' }}>
                        {criticalHospitals}
                    </div>
                </div>
            </div>

            {/* Live Map Visualization */}
            <div className="card" style={{ marginBottom: 20, padding: 0, overflow: 'hidden' }}>
                <div className="card-header" style={{ padding: '14px 20px', borderBottom: '1px solid var(--color-border)', margin: 0 }}>
                    <span className="card-title">Live Dispatch Map</span>
                    <span className={`nav-ws-status ${ws?.connected ? 'connected' : 'disconnected'}`} style={{ fontSize: 9 }}>
                        <span className="nav-ws-dot" /> {ws?.connected ? 'REAL-TIME' : 'OFFLINE'}
                    </span>
                </div>
                <LiveMap hospitals={hospitals} ambulances={ambulances} />
            </div>

            <div className="grid grid-2">
                {/* Ambulance Fleet */}
                <div className="card">
                    <div className="card-header">
                        <span className="card-title">Fleet Status</span>
                        <span className="badge badge-info">{ambulances.length} UNITS</span>
                    </div>
                    {ambulances.length === 0 ? (
                        <p style={{ color: 'var(--color-text-muted)', fontSize: 13, textAlign: 'center', padding: 20 }}>No ambulances registered</p>
                    ) : (
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                            {ambulances.slice(0, 10).map(a => (
                                <div key={a.id} style={{
                                    padding: '10px 14px', borderRadius: 'var(--radius-sm)',
                                    background: 'var(--color-bg-tertiary)', border: '1px solid var(--color-border)',
                                    display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                                }}>
                                    <div>
                                        <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--color-text-primary)' }}>
                                            {a.name || `AMB-${a.id}`}
                                        </div>
                                        <div style={{ fontSize: 11, color: 'var(--color-text-muted)', marginTop: 2 }}>
                                            {a.lat?.toFixed(4)}, {a.lon?.toFixed(4)}
                                        </div>
                                    </div>
                                    <div style={{ textAlign: 'right' }}>
                                        <span className={`badge ${a.status === 'en_route' ? 'badge-warning' :
                                            a.status === 'accepted' ? 'badge-success' :
                                                a.status === 'idle' ? 'badge-info' : ''
                                            }`}>
                                            {a.status?.toUpperCase()}
                                        </span>
                                        {a.patient_severity && a.patient_severity !== 'unknown' && (
                                            <div style={{ fontSize: 10, color: sevColor[a.patient_severity] || 'var(--color-text-muted)', marginTop: 2, fontWeight: 600 }}>
                                                {a.patient_severity?.toUpperCase()}
                                            </div>
                                        )}
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </div>

                {/* Recent Alerts History */}
                <div className="card">
                    <div className="card-header">
                        <span className="card-title">Activity Feed</span>
                        <span className="badge badge-info">{alerts.length} EVENTS</span>
                    </div>
                    {alerts.length === 0 ? (
                        <p style={{ color: 'var(--color-text-muted)', fontSize: 13, textAlign: 'center', padding: 20 }}>
                            No recent activity — waiting for dispatches
                        </p>
                    ) : (
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                            {alerts.map(a => (
                                <div key={a.id} style={{
                                    padding: '8px 12px', borderRadius: 'var(--radius-sm)',
                                    background: 'var(--color-bg-tertiary)', borderLeft: `3px solid ${a.type === 'dispatch' ? 'var(--color-info)' :
                                        a.type === 'reroute' ? 'var(--color-warning)' : 'var(--color-accent)'
                                        }`,
                                    fontSize: 12, color: 'var(--color-text-secondary)',
                                }}>
                                    <div style={{ fontWeight: 500, color: 'var(--color-text-primary)' }}>{a.text}</div>
                                    <div style={{ fontSize: 10, color: 'var(--color-text-muted)', marginTop: 2 }}>
                                        {a.type?.toUpperCase()} · Just now
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </div>
            </div>

            {/* Hospital Overview Table */}
            <div className="card" style={{ marginTop: 16 }}>
                <div className="card-header">
                    <span className="card-title">Hospital Network Status</span>
                    <span className="badge badge-success">{hospitals.length} FACILITIES</span>
                </div>
                <div className="table-wrap">
                    <table>
                        <thead>
                            <tr>
                                <th>Hospital</th>
                                <th>Status</th>
                                <th>ICU Beds</th>
                                <th>Ventilators</th>
                                <th>Load</th>
                                <th>Utilization</th>
                            </tr>
                        </thead>
                        <tbody>
                            {hospitals.map(h => {
                                const utilPct = h.max_capacity > 0 ? ((h.current_load / h.max_capacity) * 100) : 0;
                                const utilColor = utilPct > 85 ? 'var(--color-critical)' : utilPct > 60 ? 'var(--color-warning)' : 'var(--color-success)';
                                return (
                                    <tr key={h.id}>
                                        <td style={{ fontWeight: 600, color: 'var(--color-text-primary)' }}>{h.name}</td>
                                        <td><span className={`badge ${h.status === 'active' ? 'badge-success' : 'badge-critical'}`}>{h.status?.toUpperCase()}</span></td>
                                        <td>{h.icu_beds} / {h.total_icu_beds}</td>
                                        <td>{h.ventilators} / {h.total_ventilators}</td>
                                        <td>{h.current_load} / {h.max_capacity}</td>
                                        <td>
                                            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                                                <div className="progress-bar" style={{ flex: 1, maxWidth: 80 }}>
                                                    <div className="progress-fill" style={{ width: `${Math.min(utilPct, 100)}%`, background: utilColor }} />
                                                </div>
                                                <span style={{ fontSize: 11, fontWeight: 600, color: utilColor, fontFamily: 'var(--font-mono)' }}>
                                                    {utilPct.toFixed(0)}%
                                                </span>
                                            </div>
                                        </td>
                                    </tr>
                                );
                            })}
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    );
}

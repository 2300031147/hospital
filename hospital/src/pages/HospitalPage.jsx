import { useState, useEffect, useCallback } from 'react';
import toast from 'react-hot-toast';
import { getHospital, getAmbulances, acknowledgeHandoff, acceptPatient, releaseBed, updateHospital } from '../services/api';

export default function HospitalPage({ ws, user }) {
    const [hospital, setHospital] = useState(null);
    const [ambulances, setAmbulances] = useState([]);
    const [handoffs, setHandoffs] = useState([]);
    const [loading, setLoading] = useState(true);

    // Edit Modal State
    const [showEdit, setShowEdit] = useState(false);
    const [editData, setEditData] = useState({});
    const [saving, setSaving] = useState(false);

    const hospitalId = user?.hospital_id;

    const fetchData = useCallback(async () => {
        if (!hospitalId) return;
        try {
            const [h, a] = await Promise.all([getHospital(hospitalId), getAmbulances()]);
            setHospital(h);
            // Filter ambulances heading to this hospital
            setAmbulances(a.filter(amb => amb.destination_hospital_id === hospitalId && amb.status !== 'idle'));
        } catch (e) { console.error(e); }
        finally { setLoading(false); }
    }, [hospitalId]);

    useEffect(() => { fetchData(); }, [fetchData]);


    // WebSocket real-time updates
    useEffect(() => {
        if (!ws?.on) return;
        const unsubs = [
            ws.on('handoff_alert', (data) => {
                const h = data.handoff;
                if (h && h.hospital_id === hospitalId) {
                    setHandoffs(prev => {
                        if (prev.find(x => x.ambulance_id === h.ambulance_id)) return prev;
                        return [h, ...prev];
                    });
                    fetchData();
                }
            }),
            ws.on('hospital_update', () => fetchData()),
            ws.on('ambulance_routed', (data) => { if (data.hospital_id === hospitalId) fetchData(); }),
            ws.on('patient_accepted', () => fetchData()),
            ws.on('bed_released', () => fetchData()),
            ws.on('location_update', (data) => {
                setAmbulances(prev => prev.map(a =>
                    a.id === data.ambulance_id ? { ...a, lat: data.lat, lon: data.lon } : a
                ));
            }),
        ];
        return () => unsubs.forEach(u => u?.());
    }, [ws, hospitalId, fetchData]);

    const handleAcknowledge = async (hId) => {
        try { await acknowledgeHandoff(hId); } catch (e) { toast.error(e.message); }
    };

    const handleAccept = async (ambId) => {
        try {
            await acceptPatient(hospitalId, ambId);
            setHandoffs(prev => prev.filter(h => h.ambulance_id !== ambId));
            fetchData();
        } catch (e) { toast.error(e.message); }
    };

    const handleRelease = async () => {
        try { await releaseBed(hospitalId); fetchData(); } catch (e) { toast.error(e.message); }
    };

    const handleEditClick = () => {
        setEditData({
            max_capacity: hospital.max_capacity,
            total_icu_beds: hospital.total_icu_beds,
            total_ventilators: hospital.total_ventilators,
        });
        setShowEdit(true);
    };

    const handleSaveDetails = async (e) => {
        e.preventDefault();
        setSaving(true);
        try {
            await updateHospital(hospitalId, editData);
            setShowEdit(false);
            fetchData();
        } catch (err) {
            toast.error(err.message || 'Failed to update details');
        } finally {
            setSaving(false);
        }
    };

    const sevColors = { critical: '#ff3b3b', high: '#ff6b35', medium: '#ffaa00', low: '#00cc66' };

    const DonutGauge = ({ current, total, color, label, size = 90 }) => {
        const pct = total > 0 ? current / total : 0;
        const r = size * 0.38, cx = size / 2, cy = size / 2, circ = 2 * Math.PI * r;
        return (
            <div style={{ textAlign: 'center' }}>
                <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`}>
                    <circle cx={cx} cy={cy} r={r} fill="none" stroke="var(--color-bg-tertiary)" strokeWidth="7" />
                    <circle cx={cx} cy={cy} r={r} fill="none" stroke={color} strokeWidth="7"
                        strokeDasharray={`${pct * circ} ${circ}`} strokeLinecap="round"
                        transform={`rotate(-90 ${cx} ${cy})`} style={{ transition: 'stroke-dasharray 0.5s ease' }} />
                    <text x={cx} y={cy - 4} textAnchor="middle" fill="var(--color-text-primary)" fontSize="18" fontWeight="800">{current}</text>
                    <text x={cx} y={cy + 12} textAnchor="middle" fill="var(--color-text-muted)" fontSize="9">/{total}</text>
                </svg>
                <div style={{ fontSize: 10, color: 'var(--color-text-muted)', fontWeight: 600 }}>{label}</div>
            </div>
        );
    };

    if (loading) return <div className="page fade-in"><p style={{ color: 'var(--color-text-muted)' }}>Loading hospital data...</p></div>;
    if (!hospital) return <div className="page"><p style={{ color: 'var(--color-critical)' }}>Hospital not found (ID: {hospitalId})</p></div>;

    const utilPct = hospital.max_capacity > 0 ? (hospital.current_load / hospital.max_capacity * 100) : 0;
    const utilColor = utilPct > 85 ? 'var(--color-critical)' : utilPct > 60 ? 'var(--color-warning)' : 'var(--color-success)';

    return (
        <div className="page fade-in">
            <div className="page-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div>
                    <h1 className="page-title">{hospital.name}</h1>
                    <p className="page-subtitle">Hospital ID #{hospital.id} · Real-time bed & patient management</p>
                </div>
                <button className="btn btn-secondary" onClick={handleEditClick}>
                    Edit Details
                </button>
            </div>

            {/* Status Cards */}
            <div className="grid grid-4" style={{ marginBottom: 20 }}>
                <div className="stat-card">
                    <div className="stat-label">Utilization</div>
                    <div className="stat-value" style={{ color: utilColor }}>{utilPct.toFixed(0)}%</div>
                    <div className="progress-bar" style={{ marginTop: 6 }}>
                        <div className="progress-fill" style={{ width: `${Math.min(utilPct, 100)}%`, background: utilColor }} />
                    </div>
                </div>
                <div className="stat-card">
                    <div className="stat-label">Current Load</div>
                    <div className="stat-value">{hospital.current_load}<span style={{ fontSize: 14, color: 'var(--color-text-muted)' }}>/{hospital.max_capacity}</span></div>
                </div>
                <div className="stat-card">
                    <div className="stat-label">Reserved Beds</div>
                    <div className="stat-value" style={{ color: 'var(--color-warning)' }}>{hospital.soft_reserve || 0}</div>
                </div>
                <div className="stat-card">
                    <div className="stat-label">Incoming</div>
                    <div className="stat-value" style={{ color: 'var(--color-info)' }}>{ambulances.filter(a => a.status === 'en_route').length}</div>
                </div>
            </div>

            <div className="grid grid-2">
                {/* ICU + Ventilator Gauges */}
                <div className="card">
                    <div className="card-header">
                        <span className="card-title">Resource Status</span>
                        <span className={`badge ${hospital.status === 'active' ? 'badge-success' : 'badge-critical'}`}>{hospital.status?.toUpperCase()}</span>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-around', marginBottom: 16 }}>
                        <DonutGauge current={hospital.icu_beds} total={hospital.total_icu_beds} color="var(--color-info)" label="ICU BEDS AVAILABLE" />
                        <DonutGauge current={hospital.ventilators} total={hospital.total_ventilators} color="var(--color-accent)" label="VENTILATORS AVAILABLE" />
                    </div>
                    {hospital.specialists?.length > 0 && (
                        <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap', marginBottom: 12 }}>
                            {hospital.specialists.map(s => (
                                <span key={s} className="badge badge-info" style={{ fontSize: 9 }}>{s}</span>
                            ))}
                        </div>
                    )}
                    <div style={{ display: 'flex', gap: 8 }}>
                        <button className="btn btn-secondary btn-sm" onClick={handleRelease} disabled={!hospital.soft_reserve}>
                            Release Reserved Bed
                        </button>
                        <button className="btn btn-secondary btn-sm" onClick={() => handleAcknowledge(hospitalId)}>
                            Send Acknowledgement
                        </button>
                    </div>
                </div>

                {/* Incoming Ambulances */}
                <div className="card">
                    <div className="card-header">
                        <span className="card-title">Incoming Ambulances</span>
                        <span className="badge badge-info">{ambulances.length}</span>
                    </div>
                    {ambulances.length === 0 ? (
                        <p style={{ color: 'var(--color-text-muted)', fontSize: 13, textAlign: 'center', padding: 20 }}>No incoming ambulances</p>
                    ) : (
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                            {ambulances.map(a => {
                                const sev = a.patient_severity;
                                return (
                                    <div key={a.id} style={{
                                        padding: '12px 14px', borderRadius: 'var(--radius-sm)',
                                        background: 'var(--color-bg-tertiary)', border: '1px solid var(--color-border)',
                                        borderLeft: `3px solid ${sevColors[sev] || 'var(--color-text-muted)'}`,
                                    }}>
                                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                            <div>
                                                <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--color-text-primary)' }}>
                                                    {a.name || `AMB-${a.id}`}
                                                </div>
                                                <div style={{ fontSize: 11, color: 'var(--color-text-muted)', marginTop: 2 }}>
                                                    {a.lat?.toFixed(4)}, {a.lon?.toFixed(4)} · ETA: {a.eta_minutes?.toFixed(0)} min
                                                </div>
                                            </div>
                                            <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                                                <span className={`badge ${a.status === 'en_route' ? 'badge-warning' :
                                                    a.status === 'accepted' ? 'badge-success' : ''
                                                    }`}>{a.status?.toUpperCase()}</span>
                                                {a.status === 'en_route' && (
                                                    <button className="btn btn-primary btn-sm" onClick={() => handleAccept(a.id)}>
                                                        Accept
                                                    </button>
                                                )}
                                            </div>
                                        </div>
                                        {sev && sev !== 'unknown' && (
                                            <div style={{ marginTop: 6, display: 'flex', gap: 6, alignItems: 'center' }}>
                                                <div style={{ width: 6, height: 6, borderRadius: '50%', background: sevColors[sev] }} />
                                                <span style={{ fontSize: 11, fontWeight: 600, color: sevColors[sev] }}>{sev.toUpperCase()}</span>
                                            </div>
                                        )}
                                    </div>
                                );
                            })}
                        </div>
                    )}
                </div>
            </div>

            {/* Pending Handoffs */}
            {handoffs.length > 0 && (
                <div className="card" style={{ marginTop: 16 }}>
                    <div className="card-header">
                        <span className="card-title">Pending Handoff Alerts</span>
                        <span className="badge badge-critical">{handoffs.length} PENDING</span>
                    </div>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                        {handoffs.map(h => (
                            <div key={h.ambulance_id} style={{
                                padding: 16, borderRadius: 'var(--radius-md)',
                                background: 'var(--color-critical-dim)', border: '1px solid rgba(255,59,59,0.2)',
                            }}>
                                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                                    <div>
                                        <div style={{ fontSize: 14, fontWeight: 700, color: 'var(--color-text-primary)', marginBottom: 4 }}>
                                            Ambulance #{h.ambulance_id} — {h.severity?.level?.toUpperCase()}
                                        </div>
                                        <div style={{ fontSize: 12, color: 'var(--color-text-muted)', marginBottom: 8 }}>
                                            ETA: {h.eta_minutes?.toFixed(0)} min · Bed reserved: {h.bed_reserved ? 'Yes' : 'No'}
                                        </div>
                                        {/* Vitals */}
                                        <div style={{ display: 'flex', gap: 16, fontSize: 12 }}>
                                            {[
                                                { label: 'HR', value: h.vitals?.heart_rate, unit: 'BPM', color: 'var(--color-critical)' },
                                                { label: 'SpO₂', value: h.vitals?.spo2, unit: '%', color: 'var(--color-info)' },
                                                { label: 'BP', value: h.vitals?.systolic_bp, unit: 'mmHg', color: 'var(--color-warning)' },
                                            ].map(v => (
                                                <div key={v.label} style={{ textAlign: 'center' }}>
                                                    <div style={{ fontSize: 10, color: 'var(--color-text-muted)' }}>{v.label}</div>
                                                    <div style={{ fontSize: 16, fontWeight: 800, color: v.color, fontFamily: 'var(--font-mono)' }}>{v.value || '--'}</div>
                                                    <div style={{ fontSize: 9, color: 'var(--color-text-muted)' }}>{v.unit}</div>
                                                </div>
                                            ))}
                                        </div>
                                        {/* Prep instructions */}
                                        {h.prep_instructions?.length > 0 && (
                                            <div style={{ marginTop: 8 }}>
                                                <div style={{ fontSize: 10, color: 'var(--color-text-muted)', fontWeight: 600, marginBottom: 4 }}>PREP INSTRUCTIONS</div>
                                                <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
                                                    {h.prep_instructions.map((inst, i) => (
                                                        <span key={i} className="badge badge-warning" style={{ fontSize: 9 }}>{inst}</span>
                                                    ))}
                                                </div>
                                            </div>
                                        )}
                                    </div>
                                    <div style={{ display: 'flex', flexDirection: 'column', gap: 6, minWidth: 100 }}>
                                        <button className="btn btn-primary btn-sm" onClick={() => handleAccept(h.ambulance_id)}>Accept Patient</button>
                                        <button className="btn btn-secondary btn-sm" onClick={() => handleAcknowledge(hospitalId)}>Acknowledge</button>
                                    </div>
                                </div>
                            </div>
                        ))}
                    </div>
                </div>
            )}

            {/* Edit Settings Modal */}
            {showEdit && (
                <div style={{
                    position: 'fixed', top: 0, left: 0, right: 0, bottom: 0,
                    background: 'rgba(0,0,0,0.7)', backdropFilter: 'blur(4px)',
                    display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000
                }}>
                    <div className="card fade-in" style={{ width: 450, padding: 30, background: 'var(--color-bg-secondary)' }}>
                        <div style={{ marginBottom: 24 }}>
                            <h2 style={{ fontSize: 20, fontWeight: 700, margin: 0 }}>Update Hospital Details</h2>
                            <p style={{ color: 'var(--color-text-muted)', fontSize: 13, marginTop: 4 }}>
                                Adjust your total capacity limits to update the dispatch network.
                            </p>
                        </div>

                        <form onSubmit={handleSaveDetails}>
                            <div className="form-group" style={{ marginBottom: 16 }}>
                                <label className="label">Total Bed Capacity</label>
                                <input className="input" type="number" min="0" required
                                    value={editData.max_capacity}
                                    onChange={e => setEditData({ ...editData, max_capacity: parseInt(e.target.value) || 0 })}
                                />
                                <span style={{ fontSize: 11, color: 'var(--color-text-muted)', marginTop: 4, display: 'block' }}>
                                    Current Load: {hospital.current_load} beds occupied
                                </span>
                            </div>

                            <div className="grid grid-2" style={{ marginBottom: 24 }}>
                                <div className="form-group">
                                    <label className="label">Total ICU Beds</label>
                                    <input className="input" type="number" min="0" required
                                        value={editData.total_icu_beds}
                                        onChange={e => setEditData({ ...editData, total_icu_beds: parseInt(e.target.value) || 0 })}
                                    />
                                </div>
                                <div className="form-group">
                                    <label className="label">Total Ventilators</label>
                                    <input className="input" type="number" min="0" required
                                        value={editData.total_ventilators}
                                        onChange={e => setEditData({ ...editData, total_ventilators: parseInt(e.target.value) || 0 })}
                                    />
                                </div>
                            </div>

                            <div style={{ display: 'flex', gap: 12, justifyContent: 'flex-end', marginTop: 32 }}>
                                <button type="button" className="btn btn-secondary" onClick={() => setShowEdit(false)} disabled={saving}>
                                    Cancel
                                </button>
                                <button type="submit" className="btn btn-primary" disabled={saving}>
                                    {saving ? 'Saving...' : 'Save Details'}
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
}

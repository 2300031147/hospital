import { useState, useEffect, useCallback } from 'react';
import toast from 'react-hot-toast';
import { getHospitals, createHospital, deleteHospital, simulateOverload } from '../services/api';

export default function HospitalsPage({ ws }) {
    const [hospitals, setHospitals] = useState([]);
    const [loading, setLoading] = useState(true);
    const [showCreate, setShowCreate] = useState(false);
    const [form, setForm] = useState({ name: '', lat: 17.43, lon: 78.45, icu_beds: 5, total_icu_beds: 10, ventilators: 3, total_ventilators: 6, specialists: [], current_load: 0, max_capacity: 100, equipment_score: 0.85, status: 'active' });

    const fetchData = useCallback(async () => {
        try { setHospitals(await getHospitals()); } catch (e) { console.error(e); }
        finally { setLoading(false); }
    }, []);

    useEffect(() => { fetchData(); }, [fetchData]);

    useEffect(() => {
        if (!ws?.on) return;
        const unsub = ws.on('hospital_update', fetchData);
        return unsub;
    }, [ws, fetchData]);

    const handleCreate = async () => {
        try {
            await createHospital(form);
            setShowCreate(false);
            fetchData();
        } catch (e) { toast.error(e.message); }
    };

    const handleDelete = async (id, name) => {
        if (!window.confirm(`Delete ${name}? This cannot be undone.`)) return;
        try { await deleteHospital(id); fetchData(); } catch (e) { toast.error(e.message); }
    };

    const handleOverload = async (id) => {
        try { await simulateOverload(id); fetchData(); } catch (e) { toast.error(e.message); }
    };

    const DonutGauge = ({ current, total, color, label }) => {
        const pct = total > 0 ? current / total : 0;
        const r = 28, cx = 36, cy = 36, circ = 2 * Math.PI * r;
        return (
            <div style={{ textAlign: 'center' }}>
                <svg width="72" height="72" viewBox="0 0 72 72">
                    <circle cx={cx} cy={cy} r={r} fill="none" stroke="var(--color-bg-tertiary)" strokeWidth="6" />
                    <circle cx={cx} cy={cy} r={r} fill="none" stroke={color} strokeWidth="6"
                        strokeDasharray={`${pct * circ} ${circ}`} strokeLinecap="round"
                        transform={`rotate(-90 ${cx} ${cy})`} style={{ transition: 'stroke-dasharray 0.5s ease' }} />
                    <text x={cx} y={cy - 3} textAnchor="middle" fill="var(--color-text-primary)" fontSize="13" fontWeight="800">{current}</text>
                    <text x={cx} y={cy + 10} textAnchor="middle" fill="var(--color-text-muted)" fontSize="8">/{total}</text>
                </svg>
                <div style={{ fontSize: 9, color: 'var(--color-text-muted)', fontWeight: 600, marginTop: 2 }}>{label}</div>
            </div>
        );
    };

    if (loading) return <div className="page fade-in"><p style={{ color: 'var(--color-text-muted)' }}>Loading hospitals...</p></div>;

    return (
        <div className="page fade-in">
            <div className="page-header" style={{ display: 'flex', justifyContent: 'space-between' }}>
                <div>
                    <h1 className="page-title">Hospital Management</h1>
                    <p className="page-subtitle">{hospitals.length} facilities in network</p>
                </div>
                <button className="btn btn-primary" onClick={() => setShowCreate(!showCreate)}>
                    {showCreate ? 'Cancel' : '+ Add Hospital'}
                </button>
            </div>

            {showCreate && (
                <div className="card" style={{ marginBottom: 16 }}>
                    <div className="card-title" style={{ marginBottom: 12 }}>New Hospital</div>
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 10 }}>
                        <div style={{ gridColumn: 'span 3' }}>
                            <label className="label">Name</label>
                            <input className="input" value={form.name} onChange={e => setForm(f => ({ ...f, name: e.target.value }))} placeholder="Hospital name" />
                        </div>
                        {[
                            { k: 'lat', l: 'Latitude' }, { k: 'lon', l: 'Longitude' }, { k: 'icu_beds', l: 'ICU Beds' },
                            { k: 'total_icu_beds', l: 'Total ICU' }, { k: 'ventilators', l: 'Ventilators' }, { k: 'total_ventilators', l: 'Total Vent' },
                            { k: 'max_capacity', l: 'Max Capacity' }, { k: 'equipment_score', l: 'Equipment Score' },
                        ].map(({ k, l }) => (
                            <div key={k}>
                                <label className="label">{l}</label>
                                <input className="input" type="number" step="any" value={form[k]} onChange={e => setForm(f => ({ ...f, [k]: Number(e.target.value) }))} />
                            </div>
                        ))}
                    </div>
                    <button className="btn btn-primary" style={{ marginTop: 12 }} onClick={handleCreate}>Create Hospital</button>
                </div>
            )}

            <div className="grid grid-2">
                {hospitals.map(h => {
                    const utilPct = h.max_capacity > 0 ? (h.current_load / h.max_capacity * 100) : 0;
                    const utilColor = utilPct > 85 ? 'var(--color-critical)' : utilPct > 60 ? 'var(--color-warning)' : 'var(--color-success)';
                    return (
                        <div className="card" key={h.id}>
                            <div className="card-header">
                                <span className="card-title">{h.name}</span>
                                <span className={`badge ${h.status === 'active' ? 'badge-success' : 'badge-critical'}`}>{h.status?.toUpperCase()}</span>
                            </div>

                            <div style={{ display: 'flex', justifyContent: 'space-around', marginBottom: 16 }}>
                                <DonutGauge current={h.icu_beds} total={h.total_icu_beds} color="var(--color-info)" label="ICU BEDS" />
                                <DonutGauge current={h.ventilators} total={h.total_ventilators} color="var(--color-accent)" label="VENTILATORS" />
                            </div>

                            <div style={{ marginBottom: 8 }}>
                                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
                                    <span style={{ fontSize: 11, color: 'var(--color-text-muted)' }}>Utilization</span>
                                    <span style={{ fontSize: 11, fontWeight: 700, color: utilColor, fontFamily: 'var(--font-mono)' }}>{utilPct.toFixed(0)}%</span>
                                </div>
                                <div className="progress-bar">
                                    <div className="progress-fill" style={{ width: `${Math.min(utilPct, 100)}%`, background: utilColor }} />
                                </div>
                                <div style={{ fontSize: 10, color: 'var(--color-text-muted)', marginTop: 3 }}>
                                    {h.current_load} / {h.max_capacity} capacity · {h.soft_reserve || 0} reserved
                                </div>
                            </div>

                            {h.specialists?.length > 0 && (
                                <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap', marginBottom: 12 }}>
                                    {h.specialists.map(s => (
                                        <span key={s} className="badge badge-info" style={{ fontSize: 9 }}>{s}</span>
                                    ))}
                                </div>
                            )}

                            <div style={{ display: 'flex', gap: 6 }}>
                                <button className="btn btn-secondary btn-sm" onClick={() => handleOverload(h.id)}>Simulate Overload</button>
                                <button className="btn btn-danger btn-sm" onClick={() => handleDelete(h.id, h.name)}>Delete</button>
                            </div>
                        </div>
                    );
                })}
            </div>
        </div>
    );
}

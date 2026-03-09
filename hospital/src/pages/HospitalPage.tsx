import { useState, useEffect, useCallback } from 'react';
import toast from 'react-hot-toast';
import { getHospital, getAmbulances, acknowledgeHandoff, acceptPatient, releaseBed, updateHospital } from '../services/api';
import BedPanel from '../components/BedPanel';
import PatientQueue from '../components/PatientQueue';
import HandoffList from '../components/HandoffList';
import HospitalPageSkeleton from '../components/HospitalPageSkeleton';
import { Ticker } from '../components/cyber/Ticker';
import { LoadBar } from '../components/cyber/LoadBar';

export default function HospitalPage({ ws, user }) {
    const [hospital, setHospital] = useState(null);
    const [ambulances, setAmbulances] = useState([]);
    const [handoffs, setHandoffs] = useState([]);
    const [loading, setLoading] = useState(true);

    // Edit Modal State
    const [showEdit, setShowEdit] = useState(false);
    const [editData, setEditData] = useState<any>({});
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
                    setAmbulances(prev => {
                        if (prev.find(x => x.id === h.ambulance_id)) return prev;
                        return [...prev, { id: h.ambulance_id, status: 'en_route', destination_hospital_id: hospitalId }];
                    });
                }
            }),
            ws.on('hospital_update', (data) => {
                if (data.hospital_id === hospitalId) setHospital(prev => ({ ...prev, ...data }));
            }),
            ws.on('ambulance_routed', (data) => {
                if (data.hospital_id === hospitalId) {
                    setAmbulances(prev => {
                        if (prev.find(a => a.id === data.ambulance_id)) return prev;
                        return [...prev, { id: data.ambulance_id, status: 'en_route', destination_hospital_id: hospitalId }];
                    });
                }
            }),
            ws.on('patient_accepted', (data) => {
                if (data.hospital_id === hospitalId) {
                    setHospital(prev => ({ ...prev, current_load: prev.current_load + 1, icu_beds: Math.max(0, prev.icu_beds - 1), soft_reserve: Math.max(0, (prev.soft_reserve || 0) - 1) }));
                    setAmbulances(prev => prev.map(a => a.id === data.ambulance_id ? { ...a, status: 'accepted' } : a));
                }
            }),
            ws.on('bed_released', (data) => {
                if (data.hospital_id === hospitalId) {
                    setHospital(prev => ({ ...prev, icu_beds: data.icu_beds, soft_reserve: data.soft_reserve }));
                }
            }),
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
            setAmbulances(prev => prev.map(a => a.id === ambId ? { ...a, status: 'accepted' } : a));
            if (hospital) setHospital({ ...hospital, current_load: hospital.current_load + 1, icu_beds: Math.max(0, hospital.icu_beds - 1), soft_reserve: Math.max(0, (hospital.soft_reserve || 0) - 1) });
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

    if (loading) return <HospitalPageSkeleton />;
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
                    <div className="stat-value" style={{ color: utilColor, fontFamily: 'var(--font-mono)' }}><Ticker value={utilPct} decimals={0} suffix="%" /></div>
                    <div style={{ marginTop: 6 }}><LoadBar value={hospital.current_load} max={hospital.max_capacity} /></div>
                </div>
                <div className="stat-card">
                    <div className="stat-label">Current Load</div>
                    <div className="stat-value" style={{ fontFamily: 'var(--font-mono)' }}><Ticker value={hospital.current_load} /><span style={{ fontSize: 14, color: 'var(--color-text-muted)' }}>/{hospital.max_capacity}</span></div>
                </div>
                <div className="stat-card">
                    <div className="stat-label">Reserved Beds</div>
                    <div className="stat-value" style={{ color: 'var(--color-warning)', fontFamily: 'var(--font-mono)' }}><Ticker value={hospital.soft_reserve || 0} /></div>
                </div>
                <div className="stat-card">
                    <div className="stat-label">Incoming</div>
                    <div className="stat-value" style={{ color: 'var(--color-info)', fontFamily: 'var(--font-mono)' }}><Ticker value={ambulances.filter((a: any) => a.status === 'en_route').length} /></div>
                </div>
            </div>

            <div className="grid grid-2">
                <BedPanel
                    hospital={hospital}
                    handleRelease={handleRelease}
                    handleAcknowledge={handleAcknowledge}
                />
                <PatientQueue
                    ambulances={ambulances}
                    handleAccept={handleAccept}
                />
            </div>

            {/* Pending Handoffs */}
            <HandoffList
                handoffs={handoffs}
                handleAccept={handleAccept}
                handleAcknowledge={handleAcknowledge}
                hospitalId={hospitalId}
            />

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

export default function HandoffList({ handoffs, handleAccept, handleAcknowledge, hospitalId }) {
    if (handoffs.length === 0) return null;

    return (
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
    );
}

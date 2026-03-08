const sevColors = { critical: '#ff3b3b', high: '#ff6b35', medium: '#ffaa00', low: '#00cc66' };

export default function PatientQueue({ ambulances, handleAccept }) {
    return (
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
    );
}

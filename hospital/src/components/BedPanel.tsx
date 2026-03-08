import DonutGauge from './DonutGauge';

export default function BedPanel({ hospital, handleRelease, handleAcknowledge }) {
    return (
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
                <button className="btn btn-secondary btn-sm" onClick={() => handleAcknowledge(hospital.id)}>
                    Send Acknowledgement
                </button>
            </div>
        </div>
    );
}

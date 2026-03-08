export default function HospitalPageSkeleton() {
    return (
        <div className="page fade-in" style={{ opacity: 0.6, pointerEvents: 'none' }}>
            <div className="page-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div>
                    <div style={{ width: 300, height: 32, backgroundColor: 'var(--color-bg-tertiary)', borderRadius: 4, marginBottom: 8 }} className="pulse" />
                    <div style={{ width: 400, height: 16, backgroundColor: 'var(--color-bg-tertiary)', borderRadius: 4 }} className="pulse" />
                </div>
                <div style={{ width: 120, height: 38, backgroundColor: 'var(--color-bg-tertiary)', borderRadius: 6 }} className="pulse" />
            </div>

            <div className="grid grid-4" style={{ marginBottom: 20 }}>
                {[1, 2, 3, 4].map(k => (
                    <div key={k} className="stat-card">
                        <div style={{ width: '60%', height: 14, backgroundColor: 'var(--color-bg-tertiary)', borderRadius: 4, marginBottom: 12 }} className="pulse" />
                        <div style={{ width: '40%', height: 28, backgroundColor: 'var(--color-bg-tertiary)', borderRadius: 4 }} className="pulse" />
                    </div>
                ))}
            </div>

            <div className="grid grid-2">
                <div className="card" style={{ height: 260 }}>
                    <div className="card-header">
                        <div style={{ width: 140, height: 18, backgroundColor: 'var(--color-bg-tertiary)', borderRadius: 4 }} className="pulse" />
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-around', marginTop: 30 }}>
                        <div style={{ width: 90, height: 90, borderRadius: '50%', backgroundColor: 'var(--color-bg-tertiary)' }} className="pulse" />
                        <div style={{ width: 90, height: 90, borderRadius: '50%', backgroundColor: 'var(--color-bg-tertiary)' }} className="pulse" />
                    </div>
                </div>
                <div className="card" style={{ height: 260 }}>
                    <div className="card-header">
                        <div style={{ width: 160, height: 18, backgroundColor: 'var(--color-bg-tertiary)', borderRadius: 4 }} className="pulse" />
                    </div>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 12, marginTop: 16 }}>
                        {[1, 2].map(k => (
                            <div key={k} style={{ height: 60, backgroundColor: 'var(--color-bg-tertiary)', borderRadius: 6 }} className="pulse" />
                        ))}
                    </div>
                </div>
            </div>
        </div>
    );
}

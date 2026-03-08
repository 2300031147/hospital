import { useState, useEffect } from 'react';
import { getAnalytics } from '../services/api';

export default function AnalyticsPage() {
    const [data, setData] = useState<any>(null);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        const load = async () => {
            try { setData(await getAnalytics()); } catch (e) { console.error(e); }
            finally { setLoading(false); }
        };
        load();
    }, []);

    if (loading) return <div className="page fade-in"><p style={{ color: 'var(--color-text-muted)' }}>Loading analytics...</p></div>;
    if (!data) return <div className="page"><p style={{ color: 'var(--color-critical)' }}>Failed to load analytics</p></div>;

    const sevEntries = Object.entries(data.severity_distribution || {});
    const sevColors: any = { critical: '#ff3b3b', high: '#ff6b35', medium: '#ffaa00', low: '#00cc66', unknown: '#555' };
    const totalSev = sevEntries.reduce((s, [, v]) => s + (v as number), 0) || 1;

    // SVG donut data
    let cumulAngle = 0;
    const donutSlices = sevEntries.map(([level, count]) => {
        const pct = (count as number) / totalSev;
        const startAngle = cumulAngle;
        cumulAngle += pct * 360;
        return { level, count, pct, startAngle, endAngle: cumulAngle, color: sevColors[level] || '#555' };
    });

    const polarToXY = (cx: number, cy: number, r: number, deg: number) => {
        const rad = (deg - 90) * Math.PI / 180;
        return { x: cx + r * Math.cos(rad), y: cy + r * Math.sin(rad) };
    };

    return (
        <div className="page fade-in">
            <div className="page-header">
                <h1 className="page-title">Analytics Dashboard</h1>
                <p className="page-subtitle">Real-time system metrics and performance data</p>
            </div>

            {/* Stat Cards */}
            <div className="grid grid-4" style={{ marginBottom: 20 }}>
                {[
                    { label: 'Total Dispatches', value: data.total_dispatches, icon: '' },
                    { label: 'Total Reroutes', value: data.total_reroutes, icon: '' },
                    { label: 'Avg Score', value: data.avg_score != null ? (data.avg_score * 100).toFixed(1) : 'N/A', icon: '' },
                    { label: 'Reroute Rate', value: `${data.reroute_rate}%`, icon: '' },
                ].map(({ label, value, icon }) => (
                    <div className="stat-card" key={label}>
                        <div className="stat-label">{icon} {label}</div>
                        <div className="stat-value">{value}</div>
                    </div>
                ))}
            </div>

            <div className="grid grid-2">
                {/* Severity Distribution Chart */}
                <div className="card">
                    <div className="card-header">
                        <span className="card-title">Severity Distribution</span>
                        <span className="badge badge-info">{totalSev} CASES</span>
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 24 }}>
                        <svg width="140" height="140" viewBox="0 0 140 140">
                            {donutSlices.map((s, i) => {
                                if (s.pct === 0) return null;
                                const r = 55;
                                const cx = 70, cy = 70;
                                const start = polarToXY(cx, cy, r, s.startAngle);
                                const end = polarToXY(cx, cy, r, s.endAngle);
                                const largeArc = s.pct > 0.5 ? 1 : 0;
                                const d = `M ${start.x} ${start.y} A ${r} ${r} 0 ${largeArc} 1 ${end.x} ${end.y} L ${cx} ${cy} Z`;
                                return <path key={i} d={d} fill={s.color} opacity="0.85" />;
                            })}
                            <circle cx="70" cy="70" r="35" fill="var(--color-bg-secondary)" />
                            <text x="70" y="66" textAnchor="middle" fill="var(--color-text-primary)" fontSize="18" fontWeight="800">{totalSev}</text>
                            <text x="70" y="82" textAnchor="middle" fill="var(--color-text-muted)" fontSize="9" fontWeight="600">TOTAL</text>
                        </svg>
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                            {donutSlices.map(s => (
                                <div key={s.level} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                                    <div style={{ width: 8, height: 8, borderRadius: '50%', background: s.color }} />
                                    <span style={{ fontSize: 12, color: 'var(--color-text-secondary)', fontWeight: 500 }}>
                                        {s.level.charAt(0).toUpperCase() + s.level.slice(1)}: {s.count as number} ({(s.pct * 100).toFixed(0)}%)
                                    </span>
                                </div>
                            ))}
                        </div>
                    </div>
                </div>

                {/* Hospital Utilization */}
                <div className="card">
                    <div className="card-header">
                        <span className="card-title">Hospital Utilization</span>
                    </div>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                        {(data.hospital_utilization || []).map(h => {
                            const color = h.load_pct > 85 ? 'var(--color-critical)' : h.load_pct > 60 ? 'var(--color-warning)' : 'var(--color-success)';
                            return (
                                <div key={h.id}>
                                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
                                        <span style={{ fontSize: 12, color: 'var(--color-text-secondary)', fontWeight: 500 }}>{h.name}</span>
                                        <span style={{ fontSize: 11, fontWeight: 700, color, fontFamily: 'var(--font-mono)' }}>{h.load_pct}%</span>
                                    </div>
                                    <div className="progress-bar">
                                        <div className="progress-fill" style={{ width: `${Math.min(h.load_pct, 100)}%`, background: color }} />
                                    </div>
                                    <div style={{ display: 'flex', gap: 12, marginTop: 3, fontSize: 10, color: 'var(--color-text-muted)' }}>
                                        <span>ICU: {h.icu_available}/{h.icu_total}</span>
                                        <span>Reserved: {h.reserved}</span>
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                </div>
            </div>
        </div>
    );
}

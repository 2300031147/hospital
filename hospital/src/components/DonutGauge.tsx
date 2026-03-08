export default function DonutGauge({ current, total, color, label, size = 90 }) {
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
}

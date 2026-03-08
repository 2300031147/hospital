export function LoadBar({ value, max }: { value: number, max: number }) {
    const pct = Math.min((value / max) * 100, 100);
    const barColor = pct > 85 ? "#ef4444" : pct > 65 ? "#f59e0b" : "#22c55e";

    return (
        <div style={{ width: "100%", height: 4, background: "rgba(255,255,255,0.08)", borderRadius: 2, overflow: "hidden" }}>
            <div style={{ width: `${pct}%`, height: "100%", background: barColor, borderRadius: 2, transition: "width 0.6s ease" }} />
        </div>
    );
}

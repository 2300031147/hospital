import { useState, useEffect, useCallback, useRef } from 'react';
import { getHospitals, getAmbulances } from '../services/api';
import { Ticker } from '../components/cyber/Ticker';
import { LoadBar } from '../components/cyber/LoadBar';
import { MapDot } from '../components/cyber/MapDot';

export default function CommandPage({ ws }: { ws: any }) {
    const [hospitals, setHospitals] = useState<any[]>([]);
    const [ambulances, setAmbulances] = useState<any[]>([]);
    const [alerts, setAlerts] = useState<any[]>([]);
    const [loading, setLoading] = useState(true);

    const fetchData = useCallback(async () => {
        try {
            const [h, a] = await Promise.all([getHospitals(), getAmbulances()]);
            setHospitals(h);
            setAmbulances(a);
        } catch (e) { console.error(e); }
        finally { setLoading(false); }
    }, []);

    // Debounce WS-triggered fetches — rapid-fire events (routed + reroute + hospital_update)
    // would otherwise hammer the API. Coalesce into a single call within 500 ms.
    const debounceTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
    const debouncedFetch = useCallback(() => {
        if (debounceTimer.current) clearTimeout(debounceTimer.current);
        debounceTimer.current = setTimeout(fetchData, 500);
    }, [fetchData]);

    useEffect(() => { fetchData(); }, [fetchData]);

    useEffect(() => {
        if (!ws?.on) return;
        const unsubs = [
            ws.on('ambulance_routed', (data: any) => {
                setAlerts(p => [{ id: Date.now(), type: 'DISPATCH', text: `Unit ${data.ambulance_id} deployed to ${data.hospital_name}` }, ...p].slice(0, 10));
                debouncedFetch();
            }),
            ws.on('reroute', (data: any) => {
                setAlerts(p => [{ id: Date.now(), type: 'REROUTE', text: `Unit ${data.ambulance_id} diverted to ${data.to_hospital_name}` }, ...p].slice(0, 10));
                debouncedFetch();
            }),
            ws.on('hospital_update', () => debouncedFetch()),
            ws.on('alert', (data: any) => {
                setAlerts(p => [{ id: Date.now(), type: 'SYS_WARN', text: data.message }, ...p].slice(0, 10));
            }),
        ];
        return () => {
            unsubs.forEach((u: any) => u?.());
            if (debounceTimer.current) clearTimeout(debounceTimer.current);
        };
    }, [ws, debouncedFetch]);

    // Map bounds mapping -> abstract relative SVG coordinates
    // Hyderabad approx box
    const MIN_LAT = 17.35;
    const MAX_LAT = 17.50;
    const MIN_LON = 78.35;
    const MAX_LON = 78.55;

    const toSvgCoords = (lat: number, lon: number, width: number, height: number) => {
        const x = ((lon - MIN_LON) / (MAX_LON - MIN_LON)) * width;
        const y = ((MAX_LAT - lat) / (MAX_LAT - MIN_LAT)) * height;
        return { x: x || 0, y: y || 0 };
    };

    if (loading) return (
        <div style={{ height: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'var(--color-bg-primary)', color: 'var(--color-accent)' }}>
            <div style={{ fontFamily: 'var(--font-mono)' }}>INITIALIZING COMMAND CENTER...</div>
        </div>
    );

    const activeUnits = ambulances.filter(a => a.status !== 'idle').length;
    const criticalHospitals = hospitals.filter(h => h.max_capacity > 0 && (h.current_load / h.max_capacity) > 0.85).length;

    return (
        <div style={{ height: '100vh', display: 'flex', flexDirection: 'column', background: 'var(--color-bg-primary)', color: 'var(--color-text-primary)', overflow: 'hidden' }}>
            {/* Header */}
            <header style={{ height: 60, borderBottom: '1px solid var(--color-border)', display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 24px', background: 'var(--color-bg-secondary)' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
                    <div style={{ width: 12, height: 12, borderRadius: '50%', background: ws?.connected ? 'var(--color-accent)' : 'var(--color-critical)', boxShadow: ws?.connected ? '0 0 10px var(--color-accent)' : 'none' }} />
                    <h1 style={{ fontSize: 18, fontWeight: 700, letterSpacing: '0.1em', fontFamily: 'var(--font)' }}>AEROVHYN // COMMAND</h1>
                </div>
                <div style={{ display: 'flex', gap: 32, fontFamily: 'var(--font-mono)', fontSize: 12 }}>
                    <div>ACTIVE UNITS: <span style={{ color: 'var(--color-accent)', fontWeight: 600 }}><Ticker value={activeUnits} /></span></div>
                    <div>CRITICAL SECURE: <span style={{ color: criticalHospitals > 0 ? 'var(--color-critical)' : 'var(--color-success)', fontWeight: 600 }}><Ticker value={criticalHospitals} /></span></div>
                    <div>SYSTEM: <span style={{ color: 'var(--color-accent)' }}>ONLINE</span></div>
                </div>
            </header>

            {/* Main Layout */}
            <div style={{ flex: 1, display: 'grid', gridTemplateColumns: '320px 1fr 340px', gap: 1, background: 'var(--color-border)' }}>

                {/* Left Panel: Hospitals */}
                <aside style={{ background: 'var(--color-bg-primary)', padding: 20, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 16 }}>
                    <h2 style={{ fontSize: 13, fontFamily: 'var(--font-mono)', color: 'var(--color-text-muted)', borderBottom: '1px solid var(--color-border)', paddingBottom: 8, letterSpacing: '0.05em' }}>
                        FACILITY NETWORK
                    </h2>
                    {hospitals.map(h => {
                        const isCritical = h.max_capacity > 0 && (h.current_load / h.max_capacity) > 0.85;
                        return (
                            <div key={h.id} style={{ padding: 12, border: `1px solid ${isCritical ? 'var(--color-critical-dim)' : 'var(--color-border)'}`, borderRadius: 'var(--radius-sm)', background: 'var(--color-bg-secondary)' }}>
                                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13, fontWeight: 600, marginBottom: 8 }}>
                                    <span>{h.name}</span>
                                    <span style={{ color: isCritical ? 'var(--color-critical)' : 'var(--color-accent)', fontFamily: 'var(--font-mono)' }}>{h.current_load}/{h.max_capacity}</span>
                                </div>
                                <LoadBar value={h.current_load} max={h.max_capacity} />
                                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11, color: 'var(--color-text-muted)', marginTop: 8, fontFamily: 'var(--font-mono)' }}>
                                    <span>ICU: {h.icu_beds} AVAIL</span>
                                    <span>VENT: {h.ventilators}</span>
                                </div>
                            </div>
                        );
                    })}
                </aside>

                {/* Center Panel: SVG Radarmap */}
                <main style={{ background: 'var(--color-bg-tertiary)', position: 'relative', overflow: 'hidden', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                    {/* Scanline overlay */}
                    <div style={{ position: 'absolute', inset: 0, background: 'repeating-linear-gradient(0deg, transparent, transparent 2px, rgba(59, 130, 246, 0.05) 3px, rgba(59, 130, 246, 0.05) 3px)', pointerEvents: 'none', zIndex: 10 }} />

                    <svg width="100%" height="100%" viewBox="0 0 1000 800" preserveAspectRatio="none" style={{ position: 'absolute', inset: 0 }}>
                        {/* Grid lines */}
                        <defs>
                            <pattern id="grid" width="40" height="40" patternUnits="userSpaceOnUse">
                                <path d="M 40 0 L 0 0 0 40" fill="none" stroke="rgba(59, 130, 246, 0.08)" strokeWidth="1" />
                            </pattern>
                        </defs>
                        <rect width="100%" height="100%" fill="url(#grid)" />

                        {/* Map Nodes */}
                        {hospitals.map(h => {
                            if (!h.lat || !h.lon) return null;
                            const coords = toSvgCoords(h.lat, h.lon, 1000, 800);
                            return <MapDot key={`h-${h.id}`} x={coords.x} y={coords.y} color="var(--color-accent)" label={h.name} size={6} pulse />;
                        })}

                        {ambulances.map(a => {
                            if (!a.lat || !a.lon) return null;
                            const coords = toSvgCoords(a.lat, a.lon, 1000, 800);
                            const isTransporting = ['en_route', 'accepted'].includes(a.status);
                            const color = isTransporting ? 'var(--color-warning)' : 'var(--color-info)';
                            return <MapDot key={`a-${a.id}`} x={coords.x} y={coords.y} color={color} label={`AMB-${a.id}`} size={isTransporting ? 5 : 4} pulse={isTransporting} />;
                        })}
                    </svg>

                    {/* Target Reticle visual */}
                    <div style={{ position: 'absolute', top: '50%', left: '50%', width: 400, height: 400, transform: 'translate(-50%, -50%)', border: '1px solid rgba(59, 130, 246, 0.2)', borderRadius: '50%', pointerEvents: 'none' }} />
                    <div style={{ position: 'absolute', top: '50%', left: '50%', width: 200, height: 200, transform: 'translate(-50%, -50%)', border: '1px dashed rgba(59, 130, 246, 0.3)', borderRadius: '50%', pointerEvents: 'none' }} />
                </main>

                {/* Right Panel: Live Comms & Units */}
                <aside style={{ background: 'var(--color-bg-primary)', display: 'flex', flexDirection: 'column' }}>
                    <div style={{ flex: 1, padding: 20, overflowY: 'auto', borderBottom: '1px solid var(--color-border)' }}>
                        <h2 style={{ fontSize: 13, fontFamily: 'var(--font-mono)', color: 'var(--color-text-muted)', borderBottom: '1px solid var(--color-border)', paddingBottom: 8, letterSpacing: '0.05em', marginBottom: 16 }}>
                            FLEET TELEMETRY
                        </h2>
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                            {ambulances.map(a => {
                                const isTransporting = ['en_route', 'accepted'].includes(a.status);
                                return (
                                    <div key={a.id} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: 10, background: 'var(--color-bg-secondary)', borderRadius: 'var(--radius-sm)', borderLeft: `3px solid ${isTransporting ? 'var(--color-warning)' : 'var(--color-info)'}` }}>
                                        <div style={{ flex: 1 }}>
                                            <div style={{ fontSize: 12, fontWeight: 700, fontFamily: 'var(--font-mono)' }}>{a.name || `AMB-${a.id}`}</div>
                                            <div style={{ fontSize: 10, color: 'var(--color-text-muted)', marginTop: 2, textTransform: 'uppercase' }}>{a.status}</div>
                                        </div>
                                        {isTransporting && <div style={{ fontSize: 10, padding: '2px 6px', background: 'var(--color-warning-dim)', color: 'var(--color-warning)', borderRadius: 4, fontFamily: 'var(--font-mono)' }}>ACTIVE</div>}
                                    </div>
                                );
                            })}
                        </div>
                    </div>
                    <div style={{ flex: 1, padding: 20, overflowY: 'auto' }}>
                        <h2 style={{ fontSize: 13, fontFamily: 'var(--font-mono)', color: 'var(--color-text-muted)', borderBottom: '1px solid var(--color-border)', paddingBottom: 8, letterSpacing: '0.05em', marginBottom: 16 }}>
                            SECURE COMMS
                        </h2>
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                            {alerts.length === 0 && <div style={{ fontSize: 12, color: 'var(--color-text-muted)', fontFamily: 'var(--font-mono)' }}>[ NO INCOMING TRANSMISSIONS ]</div>}
                            {alerts.map(a => (
                                <div key={a.id} style={{ fontSize: 11, fontFamily: 'var(--font-mono)', lineHeight: '1.4' }}>
                                    <div style={{ color: a.type === 'DISPATCH' ? 'var(--color-accent)' : a.type === 'REROUTE' ? 'var(--color-warning)' : 'var(--color-critical)', opacity: 0.8, marginBottom: 2 }}>[{a.type}]</div>
                                    <div style={{ color: 'var(--color-text-secondary)' }}>{a.text}</div>
                                </div>
                            ))}
                        </div>
                    </div>
                </aside>

            </div>
        </div>
    );
}

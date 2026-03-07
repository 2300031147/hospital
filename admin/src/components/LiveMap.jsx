import { MapContainer, TileLayer, Marker, Popup } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';

// Fix for default Leaflet icon paths in Vite
delete L.Icon.Default.prototype._getIconUrl;

// Custom SVG Icons
const createIcon = (svgPath, color) => {
    return L.divIcon({
        className: 'custom-map-icon',
        html: `
            <svg width="32" height="32" viewBox="0 0 32 32" style="filter: drop-shadow(0 4px 6px rgba(0,0,0,0.5));">
                <circle cx="16" cy="16" r="14" fill="${color}" stroke="#fff" stroke-width="2" />
                <path d="${svgPath}" fill="#fff" transform="translate(6, 6) scale(0.83)" />
            </svg>
        `,
        iconSize: [32, 32],
        iconAnchor: [16, 16],
        popupAnchor: [0, -16]
    });
};

const hospitalIcon = createIcon(
    "M18 13h-4V9h-4v4H6v4h4v4h4v-4h4v-4zm-6-9C6.48 4 2 8.48 2 14s4.48 10 10 10 10-4.48 10-10S17.52 4 12 4zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8z",
    "#00cc66" // Success green
);

const idleAmbulanceIcon = createIcon(
    "M19.77 8.23l-3.54-3.54-1.41 1.41 3.54 3.54 1.41-1.41zM2 12c0 1.1.9 2 2 2h2v4c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2v-4h2c1.1 0 2-.9 2-2v-4H2v4zm10 2h-4v-4h-2v4H4v2h2v4h4v-4h4v-2h2v-4h-4v4z",
    "#3ba3ff" // Info blue for idle
);

const enRouteAmbulanceIcon = createIcon(
    "M19.77 8.23l-3.54-3.54-1.41 1.41 3.54 3.54 1.41-1.41zM2 12c0 1.1.9 2 2 2h2v4c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2v-4h2c1.1 0 2-.9 2-2v-4H2v4zm10 2h-4v-4h-2v4H4v2h2v4h4v-4h4v-2h2v-4h-4v4z",
    "#ffaa00" // Warning orange for transporting/en route
);

export default function LiveMap({ hospitals = [], ambulances = [] }) {
    // Center map around Hyderabad by default
    const center = [17.4065, 78.4772];

    return (
        <div style={{ height: '400px', width: '100%', borderRadius: 'var(--radius-md)', overflow: 'hidden', border: '1px solid var(--color-border)', position: 'relative' }}>
            <MapContainer center={center} zoom={12} scrollWheelZoom={true} style={{ height: '100%', width: '100%', background: '#1a1d24' }}>
                {/* Dark CartoDB Voyager map tiles matching dashboard theme */}
                <TileLayer
                    url="https://cartodb-basemaps-{s}.global.ssl.fastly.net/dark_all/{z}/{x}/{y}.png"
                    attribution='&copy; <a href="http://www.openstreetmap.org/copyright">OpenStreetMap</a> &copy; <a href="http://cartodb.com/attributions">CartoDB</a>'
                />

                {/* Render Hospitals */}
                {hospitals.map(h => (
                    h.lat && h.lon ? (
                        <Marker key={`h-${h.id}`} position={[h.lat, h.lon]} icon={hospitalIcon}>
                            <Popup className="dark-popup">
                                <div style={{ fontWeight: 700, fontSize: 13 }}>{h.name}</div>
                                <div style={{ fontSize: 11, color: '#888', marginTop: 2 }}>{h.status?.toUpperCase() || 'ACTIVE'}</div>
                                <div style={{ display: 'flex', gap: 6, marginTop: 6 }}>
                                    <span style={{ fontSize: 11, background: '#252830', padding: '2px 6px', borderRadius: 4 }}>Load: {h.current_load}/{h.max_capacity}</span>
                                    <span style={{ fontSize: 11, background: '#252830', padding: '2px 6px', borderRadius: 4 }}>ICU: {h.icu_beds}/{h.total_icu_beds}</span>
                                </div>
                            </Popup>
                        </Marker>
                    ) : null
                ))}

                {/* Render Ambulances */}
                {ambulances.map(a => {
                    const isTransporting = ['en_route', 'accepted'].includes(a.status);
                    return a.lat && a.lon ? (
                        <Marker key={`a-${a.id}`} position={[a.lat, a.lon]} icon={isTransporting ? enRouteAmbulanceIcon : idleAmbulanceIcon}>
                            <Popup className="dark-popup">
                                <div style={{ fontWeight: 700, fontSize: 13 }}>AMB-{a.id} • {a.name || 'Unit'}</div>
                                <div style={{ fontSize: 11, color: isTransporting ? '#ffaa00' : '#3ba3ff', marginTop: 2 }}>
                                    {isTransporting ? '🚨 TRANSPORTING PATIENT' : '✅ AVAILABLE ON DUTY'}
                                </div>
                                <div style={{ fontSize: 11, color: '#888', marginTop: 4 }}>
                                    Status: {a.status?.toUpperCase()}<br />
                                    {a.patient_severity && a.patient_severity !== 'unknown' && (
                                        <span>Patient: {a.patient_severity.toUpperCase()}</span>
                                    )}
                                </div>
                            </Popup>
                        </Marker>
                    ) : null
                })}
            </MapContainer>

            {/* Map Legend */}
            <div style={{
                position: 'absolute', bottom: 20, left: 20, zIndex: 400,
                background: 'rgba(26, 29, 36, 0.9)', padding: '10px 14px', borderRadius: 6, border: '1px solid var(--color-border)',
                backdropFilter: 'blur(4px)', display: 'flex', gap: 16, fontSize: 11, fontWeight: 600, boxShadow: '0 4px 12px rgba(0,0,0,0.5)'
            }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                    <div style={{ width: 12, height: 12, borderRadius: '50%', background: '#00cc66', border: '2px solid #fff' }} /> Hospital
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                    <div style={{ width: 12, height: 12, borderRadius: '50%', background: '#ffaa00', border: '2px solid #fff' }} /> Transporting
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                    <div style={{ width: 12, height: 12, borderRadius: '50%', background: '#3ba3ff', border: '2px solid #fff' }} /> Idle Driver
                </div>
            </div>

            <style>{`
                .dark-popup .leaflet-popup-content-wrapper,
                .dark-popup .leaflet-popup-tip {
                    background: var(--color-bg-tertiary);
                    color: var(--color-text-primary);
                    border: 1px solid var(--color-border);
                }
                .leaflet-container { font-family: inherit; }
                .leaflet-control-zoom a {
                    background-color: var(--color-bg-secondary) !important;
                    color: var(--color-text-primary) !important;
                    border-color: var(--color-border) !important;
                }
            `}</style>
        </div>
    );
}

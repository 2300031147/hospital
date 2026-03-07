import React, { useRef, useEffect, useState } from 'react';
import {
    StyleSheet, View, Text, TouchableOpacity, Animated, Platform,
} from 'react-native';
import MapView, { Marker, Polyline, UrlTile } from 'react-native-maps';
import { C, SEV_COLORS } from '../config';

export default function NavigationMapScreen({
    result, currentLocation, routeCoords,
    diversionAlert, countdown, onGoBack, vitals,
}) {
    const mapRef = useRef(null);
    const diversionAnim = useRef(new Animated.Value(0)).current;
    const fadeIn = useRef(new Animated.Value(0)).current;

    useEffect(() => {
        Animated.timing(fadeIn, { toValue: 1, duration: 300, useNativeDriver: true }).start();
    }, []);

    // Diversion alert animation
    useEffect(() => {
        if (diversionAlert) {
            Animated.sequence([
                Animated.timing(diversionAnim, { toValue: 1, duration: 300, useNativeDriver: true }),
                Animated.delay(6000),
                Animated.timing(diversionAnim, { toValue: 0, duration: 500, useNativeDriver: true }),
            ]).start();
        }
    }, [diversionAlert]);

    // Fit map to show both ambulance and hospital
    useEffect(() => {
        if (mapRef.current && result?.recommended?.hospital) {
            const dest = result.recommended.hospital;
            mapRef.current.fitToCoordinates(
                [currentLocation, { latitude: dest.lat, longitude: dest.lon }],
                { edgePadding: { top: 80, right: 40, bottom: 200, left: 40 }, animated: true }
            );
        }
    }, [result]);

    const fmtTime = (s) => {
        if (s === null || s === undefined) return '--:--';
        if (s <= 0) return 'ARRIVED';
        return `${Math.floor(s / 60)}:${(s % 60).toString().padStart(2, '0')}`;
    };

    const hospital = result?.recommended?.hospital;
    const severity = result?.severity;
    const isArrived = countdown === 0;

    return (
        <View style={{ flex: 1 }}>
            {/* Map */}
            <MapView
                ref={mapRef}
                style={StyleSheet.absoluteFillObject}
                initialRegion={{
                    latitude: currentLocation.latitude,
                    longitude: currentLocation.longitude,
                    latitudeDelta: 0.05, longitudeDelta: 0.05,
                }}
                mapType="none"
                showsUserLocation
            >
                <UrlTile
                    urlTemplate="https://a.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}@2x.png"
                    maximumZ={19}
                    flipY={false}
                />

                {/* Hospital Marker */}
                {hospital && (
                    <Marker
                        coordinate={{ latitude: hospital.lat, longitude: hospital.lon }}
                        title={hospital.name}
                        description="ICU Bed Reserved"
                    >
                        <View style={styles.hospitalMarker}>
                            <View style={styles.hospitalDot}>
                                <Text style={{ fontSize: 12 }}>🏥</Text>
                            </View>
                        </View>
                    </Marker>
                )}

                {/* Route Line */}
                {routeCoords ? (
                    <Polyline
                        coordinates={routeCoords}
                        strokeColor={C.brand}
                        strokeWidth={4}
                    />
                ) : hospital ? (
                    <Polyline
                        coordinates={[
                            currentLocation,
                            { latitude: hospital.lat, longitude: hospital.lon },
                        ]}
                        strokeColor={C.brand}
                        strokeWidth={3}
                        lineDashPattern={[8, 4]}
                    />
                ) : null}
            </MapView>

            {/* Top Bar — Back + Status */}
            <View style={styles.topBar}>
                <TouchableOpacity onPress={onGoBack} style={styles.backBtn} activeOpacity={0.7}>
                    <Text style={styles.backText}>← Back to dispatch</Text>
                </TouchableOpacity>

                <View style={[styles.connBadge, { backgroundColor: C.brandDim }]}>
                    <View style={[styles.connDot, { backgroundColor: C.brand }]} />
                    <Text style={[styles.connText, { color: C.brand }]}>TRANSMITTING</Text>
                </View>
            </View>

            {/* Diversion Alert */}
            <Animated.View style={[
                styles.diversion,
                {
                    opacity: diversionAnim,
                    transform: [{
                        scale: diversionAnim.interpolate({
                            inputRange: [0, 1], outputRange: [0.95, 1],
                        }),
                    }],
                },
            ]}>
                <Text style={styles.divTitle}>🚨 URGENT REROUTE</Text>
                <Text style={styles.divSub}>Diverted to:</Text>
                <Text style={styles.divHospital}>{diversionAlert?.to_hospital_name || ''}</Text>
                <Text style={styles.divReason}>
                    Higher priority patient assumed original bed.
                </Text>
            </Animated.View>

            {/* Floating Bottom UI */}
            <Animated.View style={[styles.floatingUI, { opacity: fadeIn }]}>
                {/* ETA Bar */}
                <View style={styles.etaBar}>
                    <View style={{ flex: 1 }}>
                        <Text style={styles.etaLabel}>EN ROUTE TO</Text>
                        <Text style={styles.etaHospital} numberOfLines={1}>
                            {hospital?.name || 'Healthcare Facility'}
                        </Text>
                    </View>
                    <View style={styles.etaBox}>
                        <Text style={styles.etaLabel}>ETA</Text>
                        <Text style={[
                            styles.etaTime,
                            { color: isArrived ? C.green : C.brand },
                        ]}>
                            {fmtTime(countdown)}
                        </Text>
                    </View>
                </View>

                {/* Severity + Vitals Card */}
                <View style={styles.vitalsCard}>
                    {/* Severity header */}
                    <View style={styles.sevRow}>
                        <View style={styles.sevLeft}>
                            <View style={[styles.sevDot, {
                                backgroundColor: SEV_COLORS[severity?.level] || C.green,
                            }]} />
                            <Text style={[styles.sevText, {
                                color: SEV_COLORS[severity?.level] || C.green,
                            }]}>
                                {severity?.level?.toUpperCase()} PATIENT
                            </Text>
                        </View>
                        <Text style={styles.sevScore}>
                            Score: {severity?.score != null ? (typeof severity.score === 'number' && severity.score < 1
                                ? (severity.score * 100).toFixed(0) + '%'
                                : severity.score
                            ) : '--'}
                        </Text>
                    </View>

                    {/* Vitals Row */}
                    <View style={styles.vitalsRow}>
                        {[
                            {
                                label: 'HR', icon: '❤️',
                                value: result?.recommended?.vitals_snapshot?.heart_rate || vitals?.heart_rate || '--',
                                unit: 'BPM', color: C.red,
                            },
                            {
                                label: 'SpO₂', icon: '🫁',
                                value: result?.recommended?.vitals_snapshot?.spo2 || vitals?.spo2 || '--',
                                unit: '%', color: C.blue,
                            },
                            {
                                label: 'BP', icon: '🩸',
                                value: result?.recommended?.vitals_snapshot?.systolic_bp || vitals?.systolic_bp || '--',
                                unit: 'mmHg', color: C.amber,
                            },
                        ].map(({ label, icon, value, unit, color }) => (
                            <View key={label} style={styles.vitalItem}>
                                <Text style={{ fontSize: 14 }}>{icon}</Text>
                                <Text style={[styles.vitalValue, { color }]}>{value}</Text>
                                <Text style={styles.vitalLabel}>{unit}</Text>
                            </View>
                        ))}
                    </View>

                    {/* Reasons */}
                    {severity?.reasons?.length > 0 && (
                        <View style={styles.reasonsRow}>
                            {severity.reasons.map((r, i) => (
                                <View key={i} style={styles.reasonBadge}>
                                    <Text style={styles.reasonText}>{r}</Text>
                                </View>
                            ))}
                        </View>
                    )}
                </View>

                {/* Distance + Score Info */}
                {result?.recommended && (
                    <View style={styles.infoBar}>
                        <View style={styles.infoItem}>
                            <Text style={styles.infoLabel}>Distance</Text>
                            <Text style={styles.infoValue}>
                                {result.recommended.distance_km?.toFixed(1)} km
                            </Text>
                        </View>
                        <View style={styles.infoDivider} />
                        <View style={styles.infoItem}>
                            <Text style={styles.infoLabel}>Score</Text>
                            <Text style={[styles.infoValue, { color: C.brand }]}>
                                {(result.recommended.final_score * 100).toFixed(0)}
                            </Text>
                        </View>
                        <View style={styles.infoDivider} />
                        <View style={styles.infoItem}>
                            <Text style={styles.infoLabel}>Readiness</Text>
                            <Text style={styles.infoValue}>
                                {(result.recommended.readiness_score * 100).toFixed(0)}%
                            </Text>
                        </View>
                    </View>
                )}
            </Animated.View>
        </View>
    );
}

const styles = StyleSheet.create({
    // Top
    topBar: {
        position: 'absolute', top: 0, left: 0, right: 0,
        flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center',
        paddingHorizontal: 16, paddingTop: Platform.OS === 'ios' ? 8 : 12, paddingBottom: 8,
        backgroundColor: C.surface0 + 'CC',
    },
    backBtn: { paddingVertical: 6, paddingHorizontal: 4 },
    backText: { color: C.brand, fontSize: 13, fontWeight: '600' },
    connBadge: {
        flexDirection: 'row', alignItems: 'center', gap: 6,
        paddingHorizontal: 10, paddingVertical: 4, borderRadius: 6,
    },
    connDot: { width: 5, height: 5, borderRadius: 3 },
    connText: { fontSize: 10, fontWeight: '800', letterSpacing: 1 },

    // Diversion
    diversion: {
        position: 'absolute', top: 60, left: 16, right: 16,
        backgroundColor: 'rgba(255,59,59,0.95)', padding: 20, borderRadius: 14,
        borderWidth: 1, borderColor: '#fca5a5',
        shadowColor: C.red, shadowOffset: { width: 0, height: 8 },
        shadowOpacity: 0.4, shadowRadius: 16, elevation: 12,
        zIndex: 100, alignItems: 'center',
    },
    divTitle: { color: '#fff', fontSize: 17, fontWeight: '800', marginBottom: 6 },
    divSub: { color: '#fca5a5', fontSize: 13, fontWeight: '500' },
    divHospital: { color: '#fff', fontSize: 18, fontWeight: '800', marginVertical: 4, textAlign: 'center' },
    divReason: { color: 'rgba(255,255,255,0.6)', fontSize: 11, textAlign: 'center' },

    // Floating Bottom
    floatingUI: { position: 'absolute', bottom: 24, left: 12, right: 12, gap: 8 },

    etaBar: {
        flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
        padding: 16, backgroundColor: C.surface1 + 'F0',
        borderWidth: 1, borderColor: C.borderDefault, borderRadius: 14,
    },
    etaLabel: { color: C.textMuted, fontSize: 10, fontWeight: '700', letterSpacing: 1 },
    etaHospital: { color: C.textPrimary, fontSize: 17, fontWeight: '700', marginTop: 2 },
    etaBox: {
        alignItems: 'center', paddingHorizontal: 14, paddingVertical: 8,
        backgroundColor: C.surface2, borderWidth: 1, borderColor: C.borderDefault, borderRadius: 10,
    },
    etaTime: {
        fontSize: 24, fontWeight: '800',
        fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace',
    },

    // Vitals Card
    vitalsCard: {
        padding: 16, backgroundColor: C.surface1 + 'F0',
        borderWidth: 1, borderColor: C.borderSubtle, borderRadius: 14,
    },
    sevRow: {
        flexDirection: 'row', justifyContent: 'space-between',
        borderBottomWidth: 1, borderBottomColor: C.borderSubtle,
        paddingBottom: 12, marginBottom: 12,
    },
    sevLeft: { flexDirection: 'row', alignItems: 'center', gap: 8 },
    sevDot: { width: 8, height: 8, borderRadius: 4 },
    sevText: { fontSize: 13, fontWeight: '700', letterSpacing: 1 },
    sevScore: { color: C.textSecondary, fontSize: 12, fontWeight: '600' },

    vitalsRow: { flexDirection: 'row', justifyContent: 'space-around' },
    vitalItem: { alignItems: 'center', gap: 2 },
    vitalValue: { fontSize: 22, fontWeight: '800', fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace' },
    vitalLabel: { color: C.textMuted, fontSize: 9, fontWeight: '600' },

    reasonsRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 6, marginTop: 12 },
    reasonBadge: {
        paddingHorizontal: 8, paddingVertical: 2,
        backgroundColor: C.surface2, borderRadius: 4,
        borderWidth: 1, borderColor: C.borderSubtle,
    },
    reasonText: { fontSize: 10, color: C.textMuted, fontWeight: '500' },

    // Info Bar
    infoBar: {
        flexDirection: 'row', alignItems: 'center',
        padding: 12, backgroundColor: C.surface1 + 'F0',
        borderWidth: 1, borderColor: C.borderSubtle, borderRadius: 12,
    },
    infoItem: { flex: 1, alignItems: 'center' },
    infoLabel: { fontSize: 9, color: C.textMuted, fontWeight: '700', letterSpacing: 0.5 },
    infoValue: {
        fontSize: 15, fontWeight: '800', color: C.textPrimary, marginTop: 2,
        fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace',
    },
    infoDivider: { width: 1, height: 28, backgroundColor: C.borderSubtle },

    // Hospital Marker
    hospitalMarker: { alignItems: 'center' },
    hospitalDot: {
        width: 32, height: 32, borderRadius: 16,
        backgroundColor: C.greenMuted,
        borderWidth: 2, borderColor: C.green,
        justifyContent: 'center', alignItems: 'center',
    },
});

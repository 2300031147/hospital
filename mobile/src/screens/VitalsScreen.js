import React, { useState } from 'react';
import {
    StyleSheet, View, Text, TextInput, TouchableOpacity,
    ScrollView, ActivityIndicator, Platform,
} from 'react-native';
import { C, EMERGENCY_TYPES } from '../config';

export default function VitalsScreen({ user, currentLocation, onDispatch, loading, onLogout }) {
    const [vitals, setVitals] = useState({
        heart_rate: '85',
        spo2: '97',
        systolic_bp: '120',
        age: '35',
        emergency_type: 'general',
    });

    const handleDispatch = () => {
        onDispatch({
            heart_rate: parseInt(vitals.heart_rate) || 80,
            spo2: parseInt(vitals.spo2) || 98,
            systolic_bp: parseInt(vitals.systolic_bp) || 120,
            age: parseInt(vitals.age) || 30,
            emergency_type: vitals.emergency_type,
        });
    };

    const vitalFields = [
        { key: 'heart_rate', label: 'Heart Rate', unit: 'BPM', icon: '❤️', color: C.red },
        { key: 'spo2', label: 'SpO₂', unit: '%', icon: '🫁', color: C.blue },
        { key: 'systolic_bp', label: 'Systolic BP', unit: 'mmHg', icon: '🩸', color: C.amber },
        { key: 'age', label: 'Patient Age', unit: 'yrs', icon: '👤', color: C.textSecondary },
    ];

    return (
        <ScrollView style={{ flex: 1 }} contentContainerStyle={styles.scroll}>
            {/* Patient Vitals Section */}
            <View style={styles.sectionHeader}>
                <Text style={styles.sectionIcon}>🩺</Text>
                <Text style={styles.sectionTitle}>Patient Vitals</Text>
            </View>

            {vitalFields.map(({ key, label, unit, icon, color }) => (
                <View key={key} style={styles.vitalRow}>
                    <View style={styles.vitalLabel}>
                        <Text style={{ fontSize: 16 }}>{icon}</Text>
                        <View>
                            <Text style={styles.vitalName}>{label}</Text>
                            <Text style={styles.vitalUnit}>{unit}</Text>
                        </View>
                    </View>
                    <TextInput
                        style={[styles.vitalInput, { borderColor: `${color}30` }]}
                        keyboardType="numeric"
                        value={vitals[key]}
                        onChangeText={v => setVitals(prev => ({ ...prev, [key]: v }))}
                        maxLength={3}
                    />
                </View>
            ))}

            {/* Emergency Type */}
            <View style={[styles.sectionHeader, { marginTop: 24 }]}>
                <Text style={styles.sectionIcon}>⚡</Text>
                <Text style={styles.sectionTitle}>Emergency Type</Text>
            </View>

            <View style={styles.typeGrid}>
                {EMERGENCY_TYPES.map(({ key, label, color }) => {
                    const active = vitals.emergency_type === key;
                    return (
                        <TouchableOpacity
                            key={key}
                            style={[
                                styles.typeBtn,
                                active && { borderColor: color, backgroundColor: color + '15' },
                            ]}
                            onPress={() => setVitals(prev => ({ ...prev, emergency_type: key }))}
                            activeOpacity={0.7}
                        >
                            <Text style={[styles.typeText, active && { color }]}>
                                {label}
                            </Text>
                        </TouchableOpacity>
                    );
                })}
            </View>

            {/* GPS Status */}
            <View style={styles.gpsBar}>
                <View style={styles.gpsDot} />
                <Text style={styles.gpsText}>
                    GPS: {currentLocation.latitude.toFixed(4)}, {currentLocation.longitude.toFixed(4)}
                </Text>
            </View>

            {/* Dispatch Button */}
            <TouchableOpacity
                style={[styles.dispatch, loading && { opacity: 0.5 }]}
                onPress={handleDispatch}
                disabled={loading}
                activeOpacity={0.85}
            >
                {loading ? (
                    <View style={styles.btnRow}>
                        <ActivityIndicator color="#0a0a12" />
                        <Text style={styles.dispatchText}>Classifying & Routing...</Text>
                    </View>
                ) : (
                    <Text style={styles.dispatchText}>🚑  DISPATCH AND ROUTE</Text>
                )}
            </TouchableOpacity>

            {/* Info text */}
            <Text style={styles.infoText}>
                AI severity classification + optimal hospital routing
            </Text>
        </ScrollView>
    );
}

const styles = StyleSheet.create({
    scroll: { padding: 16, paddingBottom: 40 },

    sectionHeader: {
        flexDirection: 'row', alignItems: 'center', gap: 8,
        marginBottom: 14,
    },
    sectionIcon: { fontSize: 16 },
    sectionTitle: {
        fontSize: 15, fontWeight: '700', color: C.textPrimary,
        letterSpacing: 0.5,
    },

    vitalRow: {
        flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
        padding: 14, backgroundColor: C.surface1,
        borderWidth: 1, borderColor: C.borderSubtle,
        borderRadius: 10, marginBottom: 8,
    },
    vitalLabel: {
        flexDirection: 'row', alignItems: 'center', gap: 10,
    },
    vitalName: { fontSize: 13, color: C.textSecondary, fontWeight: '500' },
    vitalUnit: { fontSize: 10, color: C.textMuted },
    vitalInput: {
        width: 76, textAlign: 'center', paddingVertical: 8, paddingHorizontal: 10,
        backgroundColor: C.surface2, borderWidth: 1, borderColor: C.borderDefault,
        borderRadius: 8, color: C.textPrimary, fontSize: 18, fontWeight: '800',
        fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace',
    },

    typeGrid: {
        flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginBottom: 24,
    },
    typeBtn: {
        paddingVertical: 9, paddingHorizontal: 14, borderRadius: 8,
        borderWidth: 1, borderColor: C.borderSubtle, backgroundColor: C.surface1,
    },
    typeText: { fontSize: 13, color: C.textSecondary, fontWeight: '500' },

    gpsBar: {
        flexDirection: 'row', alignItems: 'center', gap: 8,
        padding: 10, backgroundColor: C.surface1,
        borderRadius: 8, borderWidth: 1, borderColor: C.borderSubtle,
        marginBottom: 20,
    },
    gpsDot: { width: 6, height: 6, borderRadius: 3, backgroundColor: C.green },
    gpsText: {
        fontSize: 11, color: C.textMuted, fontWeight: '600',
        fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace',
    },

    dispatch: {
        backgroundColor: C.brand, paddingVertical: 16, borderRadius: 12,
        alignItems: 'center',
        shadowColor: C.brand, shadowOffset: { width: 0, height: 4 },
        shadowOpacity: 0.3, shadowRadius: 8, elevation: 6,
    },
    dispatchText: { fontSize: 15, fontWeight: '800', color: '#0a0a12', letterSpacing: 1 },
    btnRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },

    infoText: {
        fontSize: 11, color: C.textDisabled, textAlign: 'center', marginTop: 12,
    },
});

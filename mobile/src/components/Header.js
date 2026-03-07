import React from 'react';
import { StyleSheet, View, Text, TouchableOpacity, Platform } from 'react-native';
import { C } from '../config';

/**
 * Shared Header component — consistent across screens.
 */
export default function Header({ screen, user, onLogout, onGoBack }) {
    return (
        <View style={styles.header}>
            <View style={styles.row}>
                {screen === 'map' ? (
                    <TouchableOpacity onPress={onGoBack} style={styles.backBtn} activeOpacity={0.7}>
                        <Text style={styles.backText}>← Back to dispatch</Text>
                    </TouchableOpacity>
                ) : (
                    <View style={styles.userInfo}>
                        <View style={styles.avatar}>
                            <Text style={styles.avatarText}>
                                {user?.fullName?.charAt(0) || '?'}
                            </Text>
                        </View>
                        <View>
                            <Text style={styles.name}>{user?.fullName || 'Crew'}</Text>
                            <Text style={styles.role}>
                                {user?.ambulanceId ? `AMB-${user.ambulanceId}` : (user?.role || 'Paramedic')}
                            </Text>
                        </View>
                    </View>
                )}

                <View style={styles.rightGroup}>
                    <Text style={styles.brand}>AEROVHYN</Text>
                    {screen !== 'map' && (
                        <TouchableOpacity onPress={onLogout} style={styles.signOutBtn} activeOpacity={0.7}>
                            <Text style={styles.signOutText}>⏻ Sign out</Text>
                        </TouchableOpacity>
                    )}
                </View>
            </View>

            <Text style={styles.subtitle}>
                {screen === 'vitals' ? '🩺 Patient Assessment' : '🗺 Active Route Guidance'}
            </Text>
        </View>
    );
}

const styles = StyleSheet.create({
    header: {
        paddingHorizontal: 16, paddingTop: Platform.OS === 'ios' ? 4 : 12,
        paddingBottom: 10,
        borderBottomWidth: 1, borderBottomColor: C.borderSubtle,
        backgroundColor: C.surface0, zIndex: 10,
    },
    row: {
        flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center',
    },

    backBtn: { paddingVertical: 6 },
    backText: { color: C.brand, fontSize: 13, fontWeight: '600' },

    userInfo: { flexDirection: 'row', alignItems: 'center', gap: 10 },
    avatar: {
        width: 34, height: 34, borderRadius: 9,
        backgroundColor: C.brandDim,
        borderWidth: 1, borderColor: C.brand + '44',
        justifyContent: 'center', alignItems: 'center',
    },
    avatarText: { color: C.brand, fontSize: 14, fontWeight: '800' },
    name: { color: C.textPrimary, fontSize: 13, fontWeight: '600' },
    role: { color: C.textMuted, fontSize: 11 },

    rightGroup: { flexDirection: 'row', alignItems: 'center', gap: 10 },
    brand: {
        fontSize: 14, fontWeight: '800', color: C.textPrimary,
        letterSpacing: 4,
    },
    signOutBtn: {
        paddingHorizontal: 10, paddingVertical: 5, borderRadius: 6,
        borderWidth: 1, borderColor: C.borderDefault,
    },
    signOutText: { color: C.textMuted, fontSize: 11, fontWeight: '600' },

    subtitle: { color: C.textMuted, fontSize: 12, marginTop: 4 },
});

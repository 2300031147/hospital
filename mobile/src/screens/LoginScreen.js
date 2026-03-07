import React, { useState, useRef, useEffect } from 'react';
import {
    StyleSheet, View, Text, TextInput, TouchableOpacity,
    SafeAreaView, StatusBar, Animated, ActivityIndicator,
    KeyboardAvoidingView, Platform,
} from 'react-native';
import { C } from '../config';
import { login as apiLogin } from '../services/api';

export default function LoginScreen({ onLoginSuccess }) {
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const fadeIn = useRef(new Animated.Value(0)).current;
    const slideUp = useRef(new Animated.Value(30)).current;

    useEffect(() => {
        Animated.parallel([
            Animated.timing(fadeIn, { toValue: 1, duration: 500, useNativeDriver: true }),
            Animated.timing(slideUp, { toValue: 0, duration: 500, useNativeDriver: true }),
        ]).start();
    }, []);

    const handleLogin = async () => {
        if (!username.trim() || !password.trim()) {
            setError('Enter both fields');
            return;
        }
        setLoading(true);
        setError('');
        try {
            const data = await apiLogin(username.trim(), password.trim());
            onLoginSuccess(data.access_token, {
                fullName: data.full_name || data.user?.full_name,
                role: data.role || data.user?.role,
                userId: data.user_id || data.user?.id,
                ambulanceId: data.ambulance_id || data.user?.ambulance_id,
            });
        } catch (e) {
            setError(e.message || 'Connection failed');
        } finally {
            setLoading(false);
        }
    };

    return (
        <SafeAreaView style={styles.container}>
            <StatusBar barStyle="light-content" backgroundColor={C.surface0} />
            <KeyboardAvoidingView
                behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
                style={{ flex: 1 }}
            >
                <Animated.View style={[
                    styles.inner,
                    { opacity: fadeIn, transform: [{ translateY: slideUp }] },
                ]}>
                    {/* Brand */}
                    <View style={styles.brandGroup}>
                        <View style={styles.logoMark}>
                            <Text style={styles.logoPlus}>+</Text>
                        </View>
                        <Text style={styles.title}>AEROVHYN</Text>
                        <Text style={styles.subtitle}>Emergency Response Network</Text>
                    </View>

                    {/* Status bar */}
                    <View style={styles.statusBar}>
                        <View style={styles.statusDot} />
                        <Text style={styles.statusText}>System Online — Secure Connection</Text>
                    </View>

                    {/* Card */}
                    <View style={styles.card}>
                        <Text style={styles.cardTitle}>Operator Sign In</Text>
                        <Text style={styles.cardSub}>Access your field operations dashboard</Text>

                        {error ? (
                            <View style={styles.errorBox}>
                                <Text style={styles.errorText}>⚠ {error}</Text>
                            </View>
                        ) : null}

                        <View style={styles.formGroup}>
                            <Text style={styles.label}>OPERATOR ID</Text>
                            <TextInput
                                style={styles.input}
                                placeholder="Enter your operator ID"
                                placeholderTextColor={C.textMuted}
                                value={username}
                                onChangeText={setUsername}
                                autoCapitalize="none"
                                autoCorrect={false}
                                editable={!loading}
                            />
                        </View>

                        <View style={styles.formGroup}>
                            <Text style={styles.label}>ACCESS CODE</Text>
                            <TextInput
                                style={styles.input}
                                placeholder="Enter your access code"
                                placeholderTextColor={C.textMuted}
                                value={password}
                                onChangeText={setPassword}
                                secureTextEntry
                                autoCapitalize="none"
                                editable={!loading}
                            />
                        </View>

                        <TouchableOpacity
                            style={[styles.button, loading && { opacity: 0.5 }]}
                            onPress={handleLogin}
                            disabled={loading || !username.trim() || !password.trim()}
                            activeOpacity={0.8}
                        >
                            {loading ? (
                                <View style={styles.btnRow}>
                                    <ActivityIndicator color="#000" size="small" />
                                    <Text style={styles.buttonText}>Authenticating...</Text>
                                </View>
                            ) : (
                                <Text style={styles.buttonText}>🔐  Authenticate & Enter</Text>
                            )}
                        </TouchableOpacity>
                    </View>

                    {/* Footer badges */}
                    <View style={styles.footer}>
                        <View style={styles.badgeRow}>
                            <View style={styles.badge}>
                                <Text style={styles.badgeText}>AES-256</Text>
                            </View>
                            <View style={styles.badge}>
                                <Text style={styles.badgeText}>JWT Auth</Text>
                            </View>
                            <View style={styles.badge}>
                                <Text style={styles.badgeText}>RBAC</Text>
                            </View>
                        </View>
                        <Text style={styles.footerText}>
                            Authorized Personnel Only — All sessions are logged
                        </Text>
                    </View>
                </Animated.View>
            </KeyboardAvoidingView>
        </SafeAreaView>
    );
}

const styles = StyleSheet.create({
    container: { flex: 1, backgroundColor: C.surface0 },
    inner: { flex: 1, justifyContent: 'center', paddingHorizontal: 24 },

    brandGroup: { alignItems: 'center', marginBottom: 24 },
    logoMark: {
        width: 52, height: 52, borderRadius: 14,
        backgroundColor: C.brandDim,
        borderWidth: 2, borderColor: C.brand,
        justifyContent: 'center', alignItems: 'center', marginBottom: 12,
    },
    logoPlus: { fontSize: 24, fontWeight: '800', color: C.brand },
    title: { fontSize: 28, fontWeight: '800', color: C.textPrimary, letterSpacing: 6 },
    subtitle: { fontSize: 12, color: C.textMuted, marginTop: 4, letterSpacing: 1 },

    statusBar: {
        flexDirection: 'row', alignItems: 'center', gap: 8,
        paddingVertical: 8, paddingHorizontal: 14,
        backgroundColor: C.greenMuted, borderRadius: 8,
        marginBottom: 20,
    },
    statusDot: { width: 6, height: 6, borderRadius: 3, backgroundColor: C.green },
    statusText: { fontSize: 12, color: C.green, fontWeight: '600' },

    card: {
        backgroundColor: C.surface1,
        borderWidth: 1, borderColor: C.borderSubtle,
        borderRadius: 14, padding: 24,
    },
    cardTitle: { fontSize: 18, fontWeight: '700', color: C.textPrimary, marginBottom: 4 },
    cardSub: { fontSize: 13, color: C.textMuted, marginBottom: 20 },

    errorBox: {
        backgroundColor: C.redMuted,
        borderWidth: 1, borderColor: 'rgba(255,59,59,0.2)',
        borderRadius: 8, padding: 10, marginBottom: 16,
    },
    errorText: { color: C.red, fontSize: 13, fontWeight: '600' },

    formGroup: { marginBottom: 16 },
    label: {
        fontSize: 11, fontWeight: '700', color: C.textMuted,
        letterSpacing: 1, marginBottom: 6, textTransform: 'uppercase',
    },
    input: {
        backgroundColor: C.surface2,
        borderWidth: 1, borderColor: C.borderDefault,
        borderRadius: 8, paddingHorizontal: 14, paddingVertical: 13,
        color: C.textPrimary, fontSize: 14,
    },

    button: {
        backgroundColor: C.brand, paddingVertical: 15, borderRadius: 10,
        alignItems: 'center', marginTop: 8,
    },
    buttonText: { color: '#0a0a12', fontSize: 14, fontWeight: '700' },
    btnRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },

    footer: { alignItems: 'center', marginTop: 24 },
    badgeRow: { flexDirection: 'row', gap: 8, marginBottom: 8 },
    badge: {
        paddingVertical: 2, paddingHorizontal: 8,
        backgroundColor: C.surface2,
        borderWidth: 1, borderColor: C.borderDefault,
        borderRadius: 4,
    },
    badgeText: {
        fontSize: 10, fontWeight: '600', color: C.textMuted,
        letterSpacing: 0.5, fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace',
    },
    footerText: { fontSize: 11, color: C.textDisabled, textAlign: 'center' },
});

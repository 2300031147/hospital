import React, { useState, useRef, useEffect, useCallback } from 'react';
import {
  SafeAreaView, StatusBar, ActivityIndicator, Alert, View, Text, StyleSheet,
} from 'react-native';
import * as Location from 'expo-location';
import * as TaskManager from 'expo-task-manager';
import AsyncStorage from '@react-native-async-storage/async-storage';

import { C, LOCATION_TASK_NAME, AUTH_TOKEN_KEY, AUTH_USER_KEY, ACTIVE_AMB_KEY, API_BASE } from './src/config';
import { getStoredAuth, logout as apiLogout, routeAmbulance, getOSRMRoute } from './src/services/api';
import wsService from './src/services/websocket';

import LoginScreen from './src/screens/LoginScreen';
import VitalsScreen from './src/screens/VitalsScreen';
import NavigationMapScreen from './src/screens/NavigationMapScreen';
import Header from './src/components/Header';

// ─── Background Location Task ───
TaskManager.defineTask(LOCATION_TASK_NAME, async ({ data, error }) => {
  if (error) { console.error('BG Location Error:', error); return; }
  if (!data) return;

  const { locations } = data;
  if (!locations || locations.length === 0) return;

  const loc = locations[0];

  try {
    const token = await AsyncStorage.getItem(AUTH_TOKEN_KEY);
    const activeIdStr = await AsyncStorage.getItem(ACTIVE_AMB_KEY);
    if (!token || !activeIdStr) return;

    await fetch(`${API_BASE}/api/ambulances/${activeIdStr}/position`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: JSON.stringify({
        lat: loc.coords.latitude,
        lon: loc.coords.longitude
      })
    });
  } catch (err) {
    console.log('BG Task Error:', err);
  }
});

// ─── Main App ───
export default function App() {
  const [screen, setScreen] = useState('loading'); // loading | login | vitals | map
  const [authToken, setAuthToken] = useState(null);
  const [user, setUser] = useState(null);

  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState(null);
  const [routeCoords, setRouteCoords] = useState(null);
  const [countdown, setCountdown] = useState(null);
  const [diversionAlert, setDiversionAlert] = useState(null);
  const [currentLocation, setCurrentLocation] = useState({ latitude: 17.43, longitude: 78.45 });
  const [lastVitals, setLastVitals] = useState(null);

  const countdownRef = useRef(null);

  // ─── Auto-Login ───
  useEffect(() => {
    (async () => {
      try {
        const stored = await getStoredAuth();
        if (stored) {
          setAuthToken(stored.token);
          setUser(stored.user);
          setScreen('vitals');
        } else {
          setScreen('login');
        }
      } catch {
        setScreen('login');
      }
    })();
  }, []);

  // ─── Login Success ───
  const handleLoginSuccess = (token, userData) => {
    setAuthToken(token);
    setUser(userData);
    setScreen('vitals');
  };

  // ─── Logout ───
  const handleLogout = () => {
    Alert.alert('End Shift', 'Sign out and end your current session?', [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Sign out', style: 'destructive',
        onPress: async () => {
          if (countdownRef.current) {
            clearInterval(countdownRef.current);
            countdownRef.current = null;
          }
          wsService.disconnect();
          await apiLogout();
          setAuthToken(null);
          setUser(null);
          setResult(null);
          setCountdown(null);
          setRouteCoords(null);
          setScreen('login');
        },
      },
    ]);
  };

  // ─── Location + WebSocket ───
  useEffect(() => {
    if (!authToken || screen === 'login' || screen === 'loading') return;

    (async () => {
      const { status } = await Location.requestForegroundPermissionsAsync();
      if (status !== 'granted') {
        Alert.alert('Location permission denied');
        return;
      }
      await Location.requestBackgroundPermissionsAsync();

      const location = await Location.getCurrentPositionAsync({});
      setCurrentLocation({
        latitude: location.coords.latitude,
        longitude: location.coords.longitude,
      });
    })();

    // Connect WebSocket
    wsService.connect(authToken);

    // Listen for reroute events
    const unsub = wsService.on('reroute', (data) => {
      if (result && data.ambulance_id === result.ambulance_id) {
        handleDiversion(data);
      }
    });

    return () => {
      unsub();
    };
  }, [authToken, screen, result]);

  // ─── Background GPS Streaming ───
  useEffect(() => {
    let sub;
    if (screen === 'map' && result?.ambulance_id && authToken) {
      (async () => {
        try {
          await Location.startLocationUpdatesAsync(LOCATION_TASK_NAME, {
            accuracy: Location.Accuracy.High,
            timeInterval: 3000,
            distanceInterval: 10,
            showsBackgroundLocationIndicator: true,
            foregroundService: {
              notificationTitle: 'AEROVHYN Active',
              notificationBody: 'Transmitting location to command center.',
              notificationColor: '#00d4aa',
            },
          });
        } catch (e) {
          console.log('Background location start failed:', e);
        }

        sub = await Location.watchPositionAsync(
          { accuracy: Location.Accuracy.High, timeInterval: 3000, distanceInterval: 10 },
          (loc) => {
            const coords = {
              latitude: loc.coords.latitude,
              longitude: loc.coords.longitude,
            };
            setCurrentLocation(coords);
            wsService.sendLocation(result.ambulance_id, coords.latitude, coords.longitude);
          },
        );
      })();
    } else {
      Location.stopLocationUpdatesAsync(LOCATION_TASK_NAME).catch(() => { });
    }

    return () => {
      if (sub) sub.remove();
      Location.stopLocationUpdatesAsync(LOCATION_TASK_NAME).catch(() => { });
    };
  }, [screen, result, authToken]);

  // ─── Dispatch ───
  const handleDispatch = async (vitals) => {
    setLoading(true);
    setLastVitals(vitals);
    try {
      const data = await routeAmbulance(
        currentLocation.latitude,
        currentLocation.longitude,
        vitals,
      );

      // Fetch OSRM road route
      const dest = data.recommended?.hospital;
      if (dest) {
        try {
          const coords = await getOSRMRoute(
            currentLocation.latitude, currentLocation.longitude,
            dest.lat, dest.lon,
          );
          setRouteCoords(coords);
        } catch {
          setRouteCoords(null);
        }
      }

      setResult(data);
      await AsyncStorage.setItem(ACTIVE_AMB_KEY, data.ambulance_id.toString());
      setScreen('map');
      startCountdown(data.recommended?.eta_minutes);
    } catch (e) {
      if (e.message === 'SESSION_EXPIRED') {
        Alert.alert('Session Expired', 'Please sign in again.');
        setScreen('login');
      } else {
        Alert.alert('Dispatch Error', e.message);
      }
    } finally {
      setLoading(false);
    }
  };

  // ─── Diversion ───
  const handleDiversion = (data) => {
    setDiversionAlert(data);
    setRouteCoords(null); // Clear old route

    // Try to fetch new OSRM route
    if (data.to_hospital_lat && data.to_hospital_lon) {
      getOSRMRoute(
        currentLocation.latitude, currentLocation.longitude,
        data.to_hospital_lat, data.to_hospital_lon,
      ).then(setRouteCoords).catch(() => { });
    }

    setResult(prev => ({
      ...prev,
      recommended: {
        ...prev.recommended,
        hospital: {
          id: data.to_hospital,
          name: data.to_hospital_name,
          lat: data.to_hospital_lat,
          lon: data.to_hospital_lon,
        },
      },
    }));

    // Auto-dismiss after 7s
    setTimeout(() => setDiversionAlert(null), 7000);
  };

  // ─── ETA Countdown ───
  const startCountdown = (minutes) => {
    if (!minutes || minutes <= 0) return;
    let remaining = Math.round(minutes * 60);
    setCountdown(remaining);
    if (countdownRef.current) clearInterval(countdownRef.current);
    countdownRef.current = setInterval(() => {
      remaining -= 1;
      if (remaining <= 0) {
        clearInterval(countdownRef.current);
        countdownRef.current = null;
        setCountdown(0);
      } else {
        setCountdown(remaining);
      }
    }, 1000);
  };

  const goBack = () => {
    if (countdownRef.current) {
      clearInterval(countdownRef.current);
      countdownRef.current = null;
    }
    setScreen('vitals');
    setResult(null);
    setCountdown(null);
    setRouteCoords(null);
    setDiversionAlert(null);
  };

  // ─── Render ───
  if (screen === 'loading') {
    return (
      <SafeAreaView style={[styles.container, styles.center]}>
        <StatusBar barStyle="light-content" backgroundColor={C.surface0} />
        <View style={styles.loadingBox}>
          <View style={styles.loadingLogo}>
            <Text style={styles.loadingPlus}>+</Text>
          </View>
          <Text style={styles.loadingBrand}>AEROVHYN</Text>
          <ActivityIndicator size="small" color={C.brand} style={{ marginTop: 16 }} />
          <Text style={styles.loadingText}>Initializing...</Text>
        </View>
      </SafeAreaView>
    );
  }

  if (screen === 'login') {
    return <LoginScreen onLoginSuccess={handleLoginSuccess} />;
  }

  return (
    <SafeAreaView style={styles.container}>
      <StatusBar barStyle="light-content" backgroundColor={C.surface0} />

      {screen !== 'map' && (
        <Header screen={screen} user={user} onLogout={handleLogout} onGoBack={goBack} />
      )}

      {screen === 'vitals' && (
        <VitalsScreen
          user={user}
          currentLocation={currentLocation}
          onDispatch={handleDispatch}
          loading={loading}
        />
      )}

      {screen === 'map' && (
        <NavigationMapScreen
          result={result}
          currentLocation={currentLocation}
          routeCoords={routeCoords}
          diversionAlert={diversionAlert}
          countdown={countdown}
          onGoBack={goBack}
          vitals={lastVitals}
        />
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: C.surface0 },
  center: { justifyContent: 'center', alignItems: 'center' },
  loadingBox: { alignItems: 'center' },
  loadingLogo: {
    width: 56, height: 56, borderRadius: 16,
    backgroundColor: C.brandDim,
    borderWidth: 2, borderColor: C.brand,
    justifyContent: 'center', alignItems: 'center', marginBottom: 12,
  },
  loadingPlus: { fontSize: 26, fontWeight: '800', color: C.brand },
  loadingBrand: { fontSize: 22, fontWeight: '800', color: C.textPrimary, letterSpacing: 6 },
  loadingText: { color: C.textMuted, marginTop: 8, fontSize: 12 },
});

/**
 * AEROVHYN Mobile — API Service
 * Handles all REST calls to the FastAPI backend with JWT auth.
 */
import * as SecureStore from 'expo-secure-store';
import { API_BASE, OSRM_BASE, AUTH_TOKEN_KEY, AUTH_USER_KEY } from '../config';

async function getToken() {
    return SecureStore.getItemAsync(AUTH_TOKEN_KEY);
}

function authHeaders(token) {
    return {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
    };
}

async function request(endpoint, options = {}) {
    const token = await getToken();
    const url = `${API_BASE}${endpoint}`;
    const config = {
        headers: authHeaders(token),
        ...options,
    };

    const response = await fetch(url, config);

    if (response.status === 401) {
        await Promise.all([
            SecureStore.deleteItemAsync(AUTH_TOKEN_KEY),
            SecureStore.deleteItemAsync(AUTH_USER_KEY)
        ]);
        throw new Error('SESSION_EXPIRED');
    }

    if (!response.ok) {
        const error = await response.json().catch(() => ({ detail: 'Unknown error' }));
        throw new Error(error.detail || `HTTP ${response.status}`);
    }

    return response.json();
}

// ─── Auth ───
export async function login(username, password) {
    const res = await fetch(`${API_BASE}/api/auth/token`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password }),
    });

    if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        throw new Error(err.detail || 'Authentication failed');
    }

    const data = await res.json();
    await SecureStore.setItemAsync(AUTH_TOKEN_KEY, data.access_token);
    await SecureStore.setItemAsync(AUTH_USER_KEY, JSON.stringify({
        fullName: data.full_name || data.user?.full_name,
        role: data.role || data.user?.role,
        userId: data.user_id || data.user?.id,
        ambulanceId: data.ambulance_id || data.user?.ambulance_id,
    }));
    return data;
}

export async function logout() {
    await Promise.all([
        SecureStore.deleteItemAsync(AUTH_TOKEN_KEY),
        SecureStore.deleteItemAsync(AUTH_USER_KEY),
        SecureStore.deleteItemAsync('@active_amb_id')
    ]);
}

export async function getStoredAuth() {
    const [token, userStr] = await Promise.all([
        SecureStore.getItemAsync(AUTH_TOKEN_KEY),
        SecureStore.getItemAsync(AUTH_USER_KEY)
    ]);
    if (token && userStr) {
        return { token, user: JSON.parse(userStr) };
    }
    return null;
}

// ─── Dispatch ───
export async function routeAmbulance(ambulanceLat, ambulanceLon, vitals) {
    return request('/api/route', {
        method: 'POST',
        body: JSON.stringify({
            ambulance_lat: ambulanceLat,
            ambulance_lon: ambulanceLon,
            vitals,
        }),
    });
}

// ─── OSRM Road Routing (free, no API key needed) ───
export async function getOSRMRoute(fromLat, fromLon, toLat, toLon) {
    const url = `${OSRM_BASE}/route/v1/driving/${fromLon},${fromLat};${toLon},${toLat}?overview=full&geometries=geojson`;
    const res = await fetch(url);
    const data = await res.json();

    if (data.code === 'Ok' && data.routes.length > 0) {
        return data.routes[0].geometry.coordinates.map(c => ({
            latitude: c[1],
            longitude: c[0],
        }));
    }
    return null;
}

// ─── Ambulance Position ───
export async function updateAmbulancePosition(id, lat, lon) {
    return request(`/api/ambulances/${id}/position`, {
        method: 'PUT',
        body: JSON.stringify({ lat, lon }),
    });
}

// ─── Handoff ───
export async function acknowledgeHandoff(hospitalId) {
    return request(`/api/hospitals/${hospitalId}/acknowledge`, { method: 'POST' });
}

export async function acceptPatient(hospitalId, ambulanceId) {
    return request(`/api/hospitals/${hospitalId}/accept/${ambulanceId}`, { method: 'POST' });
}

// ─── Health ───
export async function healthCheck() {
    return request('/api/health');
}

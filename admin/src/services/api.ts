/*
 * AEROVHYN v2 — API Service
 * Fetch wrapper for all backend endpoints including analytics & blockchain.
 */

const BASE_URL = '/api';

async function request(endpoint: string, options: any = {}) {
    // Use localStorage token while still allowing credentials (cookies) as fallback
    const token = localStorage.getItem('aerovhyn_token');

    options.headers = options.headers || {};

    // Set default Content-Type if not already specified
    if (!options.headers['Content-Type']) {
        options.headers['Content-Type'] = 'application/json';
    }

    if (token) {
        options.headers = {
            ...options.headers,
            'Authorization': `Bearer ${token}`
        };
    }

    const res = await fetch(`${BASE_URL}${endpoint}`, {
        credentials: 'include',
        ...options,
        headers: options.headers, // Ensure the modified headers are used
    });

    if (!res.ok) {
        let err;
        try {
            err = await res.json();
        } catch (e) {
            err = { detail: res.statusText || 'API request failed' };
        }

        // Handle unauthorized globally
        if (res.status === 401 || res.status === 403) {
            console.error('Auth Error:', err.detail);
            // We could wipe token here but let AuthContext handle global logouts via state
        }

        throw new Error(err.detail || 'API request failed');
    }
    return res.json();
}

// Auth
export const loginUser = async (username, password) => {
    const res = await fetch(`${BASE_URL}/auth/token`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ username, password }),
    });
    if (!res.ok) {
        let err;
        try { err = await res.json(); } catch { err = { detail: 'Login failed' }; }
        throw new Error(err.detail || 'Invalid credentials');
    }
    return res.json();
};

export const getUsers = () => request('/users');
export const createUser = (data) => request('/users', { method: 'POST', body: JSON.stringify(data) });
export const updateUser = (id, data) => request(`/users/${id}`, { method: 'PUT', body: JSON.stringify(data) });
export const deleteUser = (id) => request(`/users/${id}`, { method: 'DELETE' });
export const resetUserPassword = (id, new_password) => request(`/users/${id}/password`, { method: 'PUT', body: JSON.stringify({ new_password }) });

// Hospitals
export const getHospitals = () => request('/hospitals');
export const getHospital = (id) => request(`/hospitals/${id}`);
export const createHospital = (data) => request('/hospitals', { method: 'POST', body: JSON.stringify(data) });
export const updateHospital = (id, data) => request(`/hospitals/${id}`, { method: 'PUT', body: JSON.stringify(data) });
export const deleteHospital = (id) => request(`/hospitals/${id}`, { method: 'DELETE' });
export const acknowledgeHandoff = (id) =>
    request(`/hospitals/${id}/acknowledge`, { method: 'POST' });
export const acceptPatient = (hospitalId, ambulanceId) =>
    request(`/hospitals/${hospitalId}/accept/${ambulanceId}`, { method: 'POST' });
export const releaseBed = (id) =>
    request(`/hospitals/${id}/release-bed`, { method: 'POST' });

// Classification & Routing
export const classifyPatient = (vitals) =>
    request('/classify', { method: 'POST', body: JSON.stringify(vitals) });
export const routeAmbulance = (data) =>
    request('/route', { method: 'POST', body: JSON.stringify(data) });

// Ambulances
export const getAmbulances = () => request('/ambulances');
export const createAmbulance = (data) =>
    request('/ambulances', { method: 'POST', body: JSON.stringify(data) });
export const updateAmbulancePosition = (id, data) =>
    request(`/ambulances/${id}/position`, { method: 'PUT', body: JSON.stringify(data) });

// Analytics
export const getAnalytics = () => request('/analytics');

// Settings
export const getSettings = () => request('/settings');
export const updateSettings = (data) => request('/settings', { method: 'PUT', body: JSON.stringify(data) });

// Blockchain
export const getBlockchain = (limit = 50) => request(`/blockchain?limit=${limit}`);
export const verifyBlockchain = () => request('/blockchain/verify');

// Logs
export const getLogs = (limit = 50) => request(`/logs?limit=${limit}`);

// Simulation
export const simulateOverload = (hospitalId) =>
    request(`/simulate/overload/${hospitalId}`, { method: 'POST' });
export const simulateReset = () =>
    request('/simulate/reset', { method: 'POST' });

// Health
export const healthCheck = () => request('/health');

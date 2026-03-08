/**
 * AEROVHYN Mobile — Design Tokens & Configuration
 * Centralized config for colors, API endpoints, and constants.
 */

import Constants from 'expo-constants';

// ─── API Endpoints ───
export const API_BASE = Constants.expoConfig?.extra?.apiBase ?? 'https://api.yourdomain.com';
export const WS_BASE = Constants.expoConfig?.extra?.wsBase ?? 'wss://api.yourdomain.com/ws/updates';

// ─── Storage Keys ───
export const AUTH_TOKEN_KEY = '@aerovhyn_token';
export const AUTH_USER_KEY = '@aerovhyn_user';
export const ACTIVE_AMB_KEY = '@active_amb_id';

// ─── Background Task ───
export const LOCATION_TASK_NAME = 'background-location-task';

// ─── Design Tokens (8px grid, semantic dark palette) ───
export const C = {
    // Surfaces
    surface0: '#09090b',
    surface1: '#0f0f12',
    surface2: '#18181b',
    surface3: '#1f1f23',
    surface4: '#27272a',

    // Borders
    borderSubtle: 'rgba(255,255,255,0.06)',
    borderDefault: 'rgba(255,255,255,0.1)',
    borderStrong: 'rgba(255,255,255,0.16)',
    borderFocus: 'rgba(0,212,170,0.5)',

    // Brand — matches dashboard teal accent
    brand: '#00d4aa',
    brandDark: '#00b894',
    brandMuted: 'rgba(0,212,170,0.12)',
    brandDim: 'rgba(0,212,170,0.06)',

    // Semantic
    red: '#ff3b3b',
    redMuted: 'rgba(255,59,59,0.12)',
    amber: '#ffaa00',
    amberMuted: 'rgba(255,170,0,0.12)',
    green: '#00cc66',
    greenMuted: 'rgba(0,204,102,0.12)',
    blue: '#3b8bff',
    blueMuted: 'rgba(59,139,255,0.12)',
    violet: '#aa66ff',

    // Text
    textPrimary: '#e8e8f0',
    textSecondary: '#9898b0',
    textMuted: '#5a5a78',
    textDisabled: '#3a3a52',
};

export const SEV_COLORS = {
    critical: C.red,
    moderate: C.amber,
    stable: C.green,
};

export const EMERGENCY_TYPES = [
    { key: 'cardiac', label: '❤️ Cardiac', color: C.red },
    { key: 'trauma', label: '🩸 Trauma', color: '#ff6644' },
    { key: 'respiratory', label: '🫁 Respiratory', color: C.amber },
    { key: 'neurological', label: '🧠 Neurological', color: C.violet },
    { key: 'fracture', label: '🦴 Fracture', color: C.blue },
    { key: 'burn', label: '🔥 Burn', color: '#ff8844' },
    { key: 'general', label: '🏥 General', color: C.textMuted },
];

export const STATUS_COLORS = {
    idle: C.textMuted,
    en_route: C.amber,
    at_scene: C.red,
    transporting: C.blue,
    accepted: C.brand,
    completed: C.green,
};

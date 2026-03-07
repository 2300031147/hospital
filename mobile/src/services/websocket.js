/**
 * AEROVHYN Mobile — WebSocket Service
 * Manages WebSocket connection with auto-reconnect, heartbeat, and event dispatch.
 */
import { WS_BASE } from '../config';

const RECONNECT_DELAYS = [1000, 2000, 4000, 8000, 15000];
const HEARTBEAT_INTERVAL = 30000;

class WebSocketService {
    constructor() {
        this.ws = null;
        this.token = null;
        this.listeners = new Map();
        this.reconnectAttempt = 0;
        this.reconnectTimer = null;
        this.heartbeatTimer = null;
        this.intentionalClose = false;
    }

    connect(token) {
        if (this.ws?.readyState === WebSocket.OPEN) return;
        this.token = token;
        this.intentionalClose = false;

        try {
            this.ws = new WebSocket(`${WS_BASE}?token=${token}`);

            this.ws.onopen = () => {
                console.log('[WS] Connected');
                this.reconnectAttempt = 0;
                this._startHeartbeat();
                this._emit('connection', { connected: true });
            };

            this.ws.onmessage = (event) => {
                try {
                    const data = JSON.parse(event.data);
                    if (data.type === 'pong') return; // Heartbeat response

                    const eventType = data.type || data.event || 'message';
                    this._emit(eventType, data);
                    this._emit('*', data); // Wildcard
                } catch { /* ignore non-JSON */ }
            };

            this.ws.onclose = () => {
                console.log('[WS] Disconnected');
                this._stopHeartbeat();
                this._emit('connection', { connected: false });

                if (!this.intentionalClose && this.token) {
                    const delay = RECONNECT_DELAYS[
                        Math.min(this.reconnectAttempt, RECONNECT_DELAYS.length - 1)
                    ];
                    this.reconnectAttempt++;
                    console.log(`[WS] Reconnecting in ${delay}ms (attempt ${this.reconnectAttempt})`);
                    this.reconnectTimer = setTimeout(() => this.connect(this.token), delay);
                }
            };

            this.ws.onerror = (e) => {
                console.log('[WS] Error:', e.message);
            };
        } catch (err) {
            console.log('[WS] Connection failed:', err);
        }
    }

    disconnect() {
        this.intentionalClose = true;
        clearTimeout(this.reconnectTimer);
        this._stopHeartbeat();
        if (this.ws) {
            this.ws.close();
            this.ws = null;
        }
        this.token = null;
    }

    send(data) {
        if (this.ws?.readyState === WebSocket.OPEN) {
            this.ws.send(typeof data === 'string' ? data : JSON.stringify(data));
            return true;
        }
        return false;
    }

    sendLocation(ambulanceId, lat, lon) {
        return this.send({
            type: 'location_update',
            ambulance_id: ambulanceId,
            lat,
            lon,
        });
    }

    on(eventType, callback) {
        if (!this.listeners.has(eventType)) {
            this.listeners.set(eventType, new Set());
        }
        this.listeners.get(eventType).add(callback);

        // Return unsubscribe function
        return () => {
            const set = this.listeners.get(eventType);
            if (set) {
                set.delete(callback);
                if (set.size === 0) this.listeners.delete(eventType);
            }
        };
    }

    _emit(eventType, data) {
        const listeners = this.listeners.get(eventType);
        if (listeners) {
            listeners.forEach(cb => {
                try { cb(data); } catch (err) { console.log('[WS] Listener error:', err); }
            });
        }
    }

    _startHeartbeat() {
        this._stopHeartbeat();
        this.heartbeatTimer = setInterval(() => {
            this.send({ type: 'ping' });
        }, HEARTBEAT_INTERVAL);
    }

    _stopHeartbeat() {
        clearInterval(this.heartbeatTimer);
    }

    get isConnected() {
        return this.ws?.readyState === WebSocket.OPEN;
    }
}

// Singleton
export default new WebSocketService();

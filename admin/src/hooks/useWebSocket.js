/*
 * AEROVHYN — useWebSocket Hook v2
 * Exponential backoff reconnect, heartbeat, pub-sub event system.
 */

import { useEffect, useRef, useState, useCallback } from 'react';

const WS_URL = `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/ws/updates`;

export default function useWebSocket() {
    const [connected, setConnected] = useState(false);
    const wsRef = useRef(null);
    const listenersRef = useRef({});
    const reconnectAttemptRef = useRef(0);
    const reconnectTimerRef = useRef(null);
    const heartbeatRef = useRef(null);

    const on = useCallback((eventType, callback) => {
        if (!listenersRef.current[eventType]) listenersRef.current[eventType] = [];
        listenersRef.current[eventType].push(callback);
        return () => {
            listenersRef.current[eventType] = (listenersRef.current[eventType] || []).filter(cb => cb !== callback);
        };
    }, []);

    const connect = useCallback(() => {
        if (wsRef.current?.readyState === WebSocket.OPEN) return;

        try {
            const ws = new WebSocket(WS_URL);
            wsRef.current = ws;

            ws.onopen = () => {
                setConnected(true);
                reconnectAttemptRef.current = 0;
                console.log('[WS] Connected');

                // Start heartbeat — send ping every 25s
                if (heartbeatRef.current) clearInterval(heartbeatRef.current);
                heartbeatRef.current = setInterval(() => {
                    if (ws.readyState === WebSocket.OPEN) {
                        ws.send(JSON.stringify({ type: 'ping' }));
                    }
                }, 25000);
            };

            ws.onmessage = (event) => {
                try {
                    const data = JSON.parse(event.data);
                    if (data.type === 'pong' || data.type === 'ping') return;

                    // Dispatch to registered listeners
                    const handlers = listenersRef.current[data.type];
                    if (handlers) handlers.forEach(cb => cb(data));

                    // Also dispatch to wildcard listeners
                    const wildcardHandlers = listenersRef.current['*'];
                    if (wildcardHandlers) wildcardHandlers.forEach(cb => cb(data));
                } catch (e) {
                    console.warn('[WS] Parse error:', e);
                }
            };

            ws.onclose = (event) => {
                setConnected(false);
                if (heartbeatRef.current) clearInterval(heartbeatRef.current);

                // Exponential backoff: 1s, 2s, 4s, 8s... up to 15s
                const delay = Math.min(1000 * Math.pow(2, reconnectAttemptRef.current), 15000);
                reconnectAttemptRef.current += 1;
                console.log(`[WS] Disconnected (code=${event.code}). Reconnecting in ${delay / 1000}s...`);

                reconnectTimerRef.current = setTimeout(connect, delay);
            };

            ws.onerror = (error) => {
                console.error('[WS] Error:', error);
            };
        } catch (e) {
            console.error('[WS] Connection failed:', e);
        }
    }, []);

    const disconnect = useCallback(() => {
        if (reconnectTimerRef.current) clearTimeout(reconnectTimerRef.current);
        if (heartbeatRef.current) clearInterval(heartbeatRef.current);
        if (wsRef.current) {
            wsRef.current.onclose = null; // Prevent auto-reconnect
            wsRef.current.close();
        }
        setConnected(false);
    }, []);

    useEffect(() => {
        connect();
        return disconnect;
    }, [connect, disconnect]);

    return { connected, on, disconnect, reconnect: connect };
}

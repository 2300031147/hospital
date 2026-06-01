package com.aerovhyn.realtime.handler;

import com.aerovhyn.auth.config.JwtTokenProvider;
import com.aerovhyn.core.service.AmbulanceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(WebSocketHandler.class);
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper objectMapper;
    private final AmbulanceService ambulanceService;

    public WebSocketHandler(JwtTokenProvider jwtTokenProvider, ObjectMapper objectMapper, AmbulanceService ambulanceService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.objectMapper = objectMapper;
        this.ambulanceService = ambulanceService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String token = extractToken(session);
        if (token != null && jwtTokenProvider.validateToken(token)) {
            String role = jwtTokenProvider.getRole(token);
            Long ambulanceId = jwtTokenProvider.getAmbulanceId(token);

            session.getAttributes().put("role", role);
            if (ambulanceId != null) {
                session.getAttributes().put("ambulanceId", ambulanceId);
            }

            sessions.put(session.getId(), session);
            log.info("WS connected: {} (authenticated)", session.getId());
        } else {
            log.warn("WS rejected: {} (no valid token)", session.getId());
            try { session.close(CloseStatus.POLICY_VIOLATION); } catch (IOException e) { /* ignore */ }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        try {
            Map<String, Object> payload = objectMapper.readValue(message.getPayload(), Map.class);
            String type = (String) payload.get("type");
            if (type == null) return;

            switch (type) {
                case "ping" -> {
                    session.sendMessage(new TextMessage("{\"type\":\"pong\"}"));
                }
                case "location_update" -> {
                    String role = (String) session.getAttributes().get("role");
                    if (!"paramedic".equals(role)) {
                        log.warn("WS reject location_update: non-paramedic user");
                        return;
                    }

                    Long secureAmbId = (Long) session.getAttributes().get("ambulanceId");
                    if (secureAmbId == null) {
                        log.warn("WS reject location_update: no assigned ambulance_id for paramedic");
                        return;
                    }

                    Object latObj = payload.get("lat");
                    Object lonObj = payload.get("lon");
                    if (latObj instanceof Number && lonObj instanceof Number) {
                        double lat = ((Number) latObj).doubleValue();
                        double lon = ((Number) lonObj).doubleValue();

                        if (lat >= -90.0 && lat <= 90.0 && lon >= -180.0 && lon <= 180.0) {
                            try {
                                ambulanceService.updatePosition(secureAmbId, lat, lon);
                                log.debug("WS LatLon update for {}: {},{}", secureAmbId, lat, lon);
                            } catch (Exception e) {
                                log.error("Failed to save WS location update for {}: {}", secureAmbId, e.getMessage());
                            }
                        } else {
                            log.warn("Invalid WS coords range for {}: {},{}", secureAmbId, lat, lon);
                        }
                    } else {
                        log.warn("Invalid WS coords types for {}", secureAmbId);
                    }
                }
                default -> log.debug("Unknown WS message type: {}", type);
            }
        } catch (Exception e) {
            log.warn("WS message error: {}", e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        log.info("WS disconnected: {} ({})", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        sessions.remove(session.getId());
        log.warn("WS transport error: {} ({})", session.getId(), exception.getMessage());
    }

    public void broadcast(String jsonMessage) {
        TextMessage message = new TextMessage(jsonMessage);
        for (WebSocketSession session : sessions.values()) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(message);
                } catch (IOException e) {
                    log.warn("WS send error to {}: {}", session.getId(), e.getMessage());
                }
            }
        }
    }

    public int getConnectionCount() {
        return sessions.size();
    }

    private String extractToken(WebSocketSession session) {
        List<String> cookies = session.getHandshakeHeaders().get("Cookie");
        if (cookies != null) {
            for (String cookieHeader : cookies) {
                for (String cookie : cookieHeader.split(";")) {
                    String[] parts = cookie.trim().split("=", 2);
                    if (parts.length == 2 && "access_token".equals(parts[0].trim())) {
                        String value = parts[1].trim();
                        try {
                            value = java.net.URLDecoder.decode(value, java.nio.charset.StandardCharsets.UTF_8.name());
                        } catch (Exception e) {
                            // ignore decoding errors and fall back to raw value
                        }
                        if (value.startsWith("Bearer ")) {
                            return value.substring(7).trim();
                        }
                        return value;
                    }
                }
            }
        }

        URI uri = session.getUri();
        if (uri != null) {
            String query = uri.getQuery();
            if (query != null) {
                for (String param : query.split("&")) {
                    String[] paramParts = param.split("=", 2);
                    if (paramParts.length == 2 && "token".equals(paramParts[0])) {
                        try {
                            return java.net.URLDecoder.decode(paramParts[1], java.nio.charset.StandardCharsets.UTF_8.name());
                        } catch (Exception e) {
                            return paramParts[1];
                        }
                    }
                }
            }
        }
        return null;
    }
}

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
        if (sessions.size() >= 500) {
            log.warn("WS rejected: max connections (500) reached");
            try { session.close(CloseStatus.SERVICE_OVERLOAD); } catch (IOException e) { /* ignore */ }
            return;
        }

        String token = extractToken(session);
        if (token != null && jwtTokenProvider.validateToken(token)) {
            String role = jwtTokenProvider.getRole(token);
            Long ambulanceId = jwtTokenProvider.getAmbulanceId(token);
            Long hospitalId = jwtTokenProvider.getHospitalId(token);

            session.getAttributes().put("role", role);
            if (ambulanceId != null) {
                session.getAttributes().put("ambulanceId", ambulanceId);
            }
            if (hospitalId != null) {
                session.getAttributes().put("hospitalId", hospitalId);
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
        broadcastToLocal(jsonMessage);
    }

    public void handleRedisMessage(String message) {
        broadcastToLocal(message);
    }

    private void broadcastToLocal(String jsonMessage) {
        Map<String, Object> data = null;
        try {
            data = objectMapper.readValue(jsonMessage, Map.class);
        } catch (Exception e) {
            log.warn("Failed to parse broadcast JSON: {}", e.getMessage());
        }

        TextMessage message = new TextMessage(jsonMessage);
        for (WebSocketSession session : sessions.values()) {
            if (!session.isOpen()) {
                sessions.remove(session.getId());
                continue;
            }

            if (data != null && !isAuthorizedForMessage(session, data)) {
                continue;
            }

            try {
                session.sendMessage(message);
            } catch (IOException e) {
                log.warn("WS send error to {}: {}", session.getId(), e.getMessage());
                sessions.remove(session.getId());
                try { session.close(CloseStatus.SERVER_ERROR); } catch (IOException ex) { /* ignore */ }
            }
        }
    }

    private boolean isAuthorizedForMessage(WebSocketSession session, Map<String, Object> data) {
        String role = (String) session.getAttributes().get("role");
        if (role == null) return false;

        if ("command_center".equalsIgnoreCase(role) || "dispatcher".equalsIgnoreCase(role)) {
            return true;
        }

        String type = (String) data.get("type");
        if ("alert".equals(type)) {
            return true;
        }

        if ("paramedic".equalsIgnoreCase(role)) {
            Long sessionAmbId = (Long) session.getAttributes().get("ambulanceId");
            if (sessionAmbId == null) return false;

            Object ambIdObj = data.get("ambulance_id");
            if (ambIdObj instanceof Number) {
                return sessionAmbId.equals(((Number) ambIdObj).longValue());
            }
            Object handoffObj = data.get("handoff");
            if (handoffObj instanceof Map) {
                Object subAmbId = ((Map<?, ?>) handoffObj).get("ambulance_id");
                if (subAmbId instanceof Number) {
                    return sessionAmbId.equals(((Number) subAmbId).longValue());
                }
            }
            return false;
        }

        if ("hospital_admin".equalsIgnoreCase(role)) {
            Long sessionHospId = (Long) session.getAttributes().get("hospitalId");
            if (sessionHospId == null) return false;

            List<String> hospFields = List.of(
                "hospital_id", "old_hospital_id", "resolved_hospital_id", "original_hospital_id", "to_hospital"
            );
            for (String field : hospFields) {
                Object val = data.get(field);
                if (val instanceof Number && sessionHospId.equals(((Number) val).longValue())) {
                    return true;
                }
            }

            Object hospObj = data.get("hospital");
            if (hospObj instanceof Map) {
                Object subHospId = ((Map<?, ?>) hospObj).get("id");
                if (subHospId instanceof Number) {
                    return sessionHospId.equals(((Number) subHospId).longValue());
                }
            }

            Object handoffObj = data.get("handoff");
            if (handoffObj instanceof Map) {
                Object subHospId = ((Map<?, ?>) handoffObj).get("hospital_id");
                if (subHospId instanceof Number) {
                    return sessionHospId.equals(((Number) subHospId).longValue());
                }
            }

            return false;
        }

        return false;
    }

    public int getConnectionCount() {
        return sessions.size();
    }

    public void cleanDeadConnections() {
        for (WebSocketSession session : sessions.values()) {
            if (!session.isOpen()) {
                sessions.remove(session.getId());
                log.info("WS cleaned dead connection: {}", session.getId());
            }
        }
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

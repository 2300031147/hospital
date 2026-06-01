package com.aerovhyn.realtime.controller;

import com.aerovhyn.realtime.handler.WebSocketHandler;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/ws")
@PreAuthorize("hasRole('COMMAND_CENTER')")
public class ConnectionMetricsController {

    private final WebSocketHandler webSocketHandler;

    public ConnectionMetricsController(WebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    @GetMapping("/metrics")
    public Map<String, Object> getMetrics() {
        return Map.of(
                "status", "active",
                "connections", webSocketHandler.getConnectionCount()
        );
    }
}

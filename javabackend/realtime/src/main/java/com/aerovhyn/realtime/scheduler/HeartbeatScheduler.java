package com.aerovhyn.realtime.scheduler;

import com.aerovhyn.realtime.handler.WebSocketHandler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class HeartbeatScheduler {

    private final WebSocketHandler webSocketHandler;

    public HeartbeatScheduler(WebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    @Scheduled(fixedRate = 30000)
    public void sendHeartbeat() {
        webSocketHandler.broadcast("{\"type\":\"ping\"}");
    }
}

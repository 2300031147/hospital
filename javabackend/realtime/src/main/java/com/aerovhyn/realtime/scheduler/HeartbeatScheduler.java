package com.aerovhyn.realtime.scheduler;

import com.aerovhyn.realtime.handler.WebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class HeartbeatScheduler {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatScheduler.class);
    private final WebSocketHandler webSocketHandler;

    public HeartbeatScheduler(WebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    @Scheduled(fixedRate = 30000)
    public void sendHeartbeat() {
        webSocketHandler.cleanDeadConnections();
        webSocketHandler.broadcast("{\"type\":\"ping\"}");
    }
}

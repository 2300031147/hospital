package com.aerovhyn.notification.listener;

import com.aerovhyn.common.events.CriticalAlertEvent;
import com.aerovhyn.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class CriticalAlertEventListener {

    private static final Logger log = LoggerFactory.getLogger(CriticalAlertEventListener.class);
    private final NotificationService notificationService;

    public CriticalAlertEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Async
    @EventListener
    public void handleCriticalAlert(CriticalAlertEvent event) {
        String message = String.format(
                "URGENT: CRITICAL patient arriving in %.1f min(s). Open the AEROVHYN dashboard to ACCEPT the handoff.",
                event.etaMinutes());

        try {
            notificationService.sendSms("+15559998888", message);
        } catch (Exception e) {
            log.error("SMS notification failed for hospital {}: {}", event.hospitalId(), e.getMessage());
        }

        try {
            notificationService.sendPush(event.hospitalId(), "CRITICAL HANDOFF", message);
        } catch (Exception e) {
            log.error("Push notification failed for hospital {}: {}", event.hospitalId(), e.getMessage());
        }
    }
}

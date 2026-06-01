package com.aerovhyn.notification.controller;

import com.aerovhyn.notification.service.NotificationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@PreAuthorize("hasRole('COMMAND_CENTER')")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping("/test")
    public Map<String, String> testNotification() {
        notificationService.sendSms("+15550000000", "Test notification from AEROVHYN");
        notificationService.sendPush(1L, "TEST", "This is a test notification");
        return Map.of("status", "sent", "message", "Test notification dispatched");
    }
}

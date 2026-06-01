package com.aerovhyn.notification.service.impl;

import com.aerovhyn.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class TwilioSmsServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(TwilioSmsServiceImpl.class);

    @Override
    @Async("notificationExecutor")
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 2000, multiplier = 2))
    public void sendSms(String to, String message) {
        log.info("SMS SENT to {}: {}", to, message);
        // In production: Twilio Java SDK integration
    }

    @Override
    @Async("notificationExecutor")
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 2000, multiplier = 2))
    public void sendPush(Long hospitalId, String title, String body) {
        log.info("PUSH SENT to hospital {}: {} - {}", hospitalId, title, body);
        // In production: Firebase Admin SDK integration
    }
}

package com.aerovhyn.notification.service;

public interface NotificationService {
    void sendSms(String to, String message);
    void sendPush(Long hospitalId, String title, String body);
}

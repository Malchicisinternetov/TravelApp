package com.example.notificationservice;

import java.time.Duration;

public class NotificationSendException extends RuntimeException {
    private final Duration retryDelay;

    public NotificationSendException(Duration retryDelay) {
        super("Telegram delivery failed");
        this.retryDelay = retryDelay;
    }

    public Duration getRetryDelay() {
        return retryDelay;
    }
}

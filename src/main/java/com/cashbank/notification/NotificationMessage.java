package com.cashbank.notification;

import java.util.UUID;

public record NotificationMessage(
        UUID userId,
        NotificationType type,
        String title,
        String message,
        String reference) {
}

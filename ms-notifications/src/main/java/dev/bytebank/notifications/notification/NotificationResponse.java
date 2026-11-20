package dev.bytebank.notifications.notification;

import java.math.BigDecimal;
import java.time.Instant;

/** Aviso tal como lo devuelve la API (ver docs/contratos/ms-notifications.md). */
public record NotificationResponse(Long id, String iban, String type, BigDecimal amount, BigDecimal balanceAfter,
                                   String reference, Long movementId, String message, Instant createdAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getIban(), notification.getType(),
                notification.getAmount(), notification.getBalanceAfter(), notification.getReference(),
                notification.getMovementId(), notification.getMessage(), notification.getCreatedAt());
    }
}
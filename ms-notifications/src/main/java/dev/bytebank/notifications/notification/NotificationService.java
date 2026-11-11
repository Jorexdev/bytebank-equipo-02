package dev.bytebank.notifications.notification;

import dev.bytebank.notifications.event.MovementEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class NotificationService {

    private final NotificationRepository notifications;

    public NotificationService(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    @Transactional
    public void notifyOwner(MovementEvent event) {
        notifications.save(new Notification(event.ownerId(), event.movementId(), event.iban(), event.type(),
                event.amount(), event.balanceAfter(), event.reference(),
                NotificationMessages.of(event), event.createdAt()));
    }
}

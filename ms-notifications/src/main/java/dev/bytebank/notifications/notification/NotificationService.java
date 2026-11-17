package dev.bytebank.notifications.notification;

import dev.bytebank.common.web.PageResponse;
import dev.bytebank.notifications.event.MovementEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final NotificationRepository notifications;

    public NotificationService(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    @Transactional
    public void notifyHolders(MovementEvent event) {
        if (event.holderIds() == null || event.holderIds().isEmpty()) {
            log.warn("Movimiento {} sin titulares; no se generan avisos", event.movementId());
            return;
        }
        for (Long holderId : event.holderIds()) {
            notifications.save(new Notification(holderId, event.movementId(), event.iban(), event.type(),
                    event.amount(), event.balanceAfter(), event.reference(),
                    NotificationMessages.of(event), event.createdAt()));
        }
    }

    /** Avisos de un usuario, los más recientes primero. */
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(Long userId, int page, int size) {
        var result = notifications.findByUserId(userId, PageRequest.of(page, size, NEWEST_FIRST));
        return PageResponse.of(result, NotificationResponse::from);
    }
}

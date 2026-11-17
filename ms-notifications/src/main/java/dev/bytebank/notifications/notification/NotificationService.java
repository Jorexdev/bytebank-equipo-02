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

    /**
     * Guarda un aviso por titular. Kafka entrega cada evento al menos una vez, así que el mismo movimiento puede
     * llegar repetido: si un titular ya tiene el aviso de ese movimiento, no se guarda otro. La restricción única
     * {@code (movement_id, user_id)} cubre el caso de dos entregas procesadas a la vez: una de ellas falla, se
     * deshace entera y, al reintentarla, encuentra los avisos ya guardados.
     */
    @Transactional
    public void notifyHolders(MovementEvent event) {
        if (event.holderIds() == null || event.holderIds().isEmpty()) {
            log.warn("Movimiento {} sin titulares; no se generan avisos", event.movementId());
            return;
        }
        for (Long holderId : event.holderIds()) {
            if (notifications.existsByMovementIdAndUserId(event.movementId(), holderId)) {
                log.info("Movimiento {} ya avisado al usuario {}; se ignora la reentrega", event.movementId(), holderId);
                continue;
            }
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

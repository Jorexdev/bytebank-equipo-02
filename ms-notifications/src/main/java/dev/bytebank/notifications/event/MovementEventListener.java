package dev.bytebank.notifications.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bytebank.notifications.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class MovementEventListener {

    private static final Logger log = LoggerFactory.getLogger(MovementEventListener.class);

    private final NotificationService notifications;
    private final ObjectMapper objectMapper;

    public MovementEventListener(NotificationService notifications, ObjectMapper objectMapper) {
        this.notifications = notifications;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = MovementEvent.TOPIC)
    public void onMovement(String payload) throws JsonProcessingException {
        MovementEvent event = objectMapper.readValue(payload, MovementEvent.class);
        log.info("Movimiento {} recibido", event.movementId());
        notifications.notifyHolders(event);
    }
}

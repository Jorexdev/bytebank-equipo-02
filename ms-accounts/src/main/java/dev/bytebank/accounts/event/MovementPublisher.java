package dev.bytebank.accounts.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bytebank.accounts.account.Account;
import dev.bytebank.accounts.movement.Movement;
import dev.bytebank.accounts.outbox.OutboxEvent;
import dev.bytebank.accounts.outbox.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registra cada movimiento para que ms-notifications avise al cliente.
 *
 * No envía a Kafka directamente: guarda el evento en el outbox, en la misma transacción que el movimiento.
 * Si la operación se deshace, el evento desaparece con ella; si se confirma, OutboxRelay lo publica después.
 */
@Component
public class MovementPublisher {

    private static final Logger log = LoggerFactory.getLogger(MovementPublisher.class);

    private final OutboxEventRepository outbox;
    private final ObjectMapper objectMapper;

    public MovementPublisher(OutboxEventRepository outbox, ObjectMapper objectMapper) {
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(Movement movement, Account account) {
        MovementEvent event = MovementEvent.of(movement, account);
        outbox.save(new OutboxEvent(MovementEvent.TOPIC, account.getIban(), toJson(event)));
        log.debug("Movimiento {} guardado en el outbox", movement.getId());
    }

    private String toJson(MovementEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se ha podido serializar el movimiento " + event.movementId(), e);
        }
    }
}

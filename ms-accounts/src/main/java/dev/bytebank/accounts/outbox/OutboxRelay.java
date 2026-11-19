package dev.bytebank.accounts.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Publica en Kafka los eventos pendientes del outbox, en orden de id.
 *
 * Un evento solo se marca como publicado cuando Kafka confirma que lo ha recibido. Si un envío falla, se para la
 * vuelta para no adelantar eventos posteriores de la misma cuenta, y se reintenta en la siguiente.
 * Garantía: al menos una vez. Si el proceso cae después de la confirmación de Kafka y antes del commit, el evento
 * se reenvía; ms-notifications ignora los repetidos (KAF-03).
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final int batchSize;
    private final long sendTimeoutMs;

    public OutboxRelay(OutboxEventRepository outbox, KafkaTemplate<String, String> kafka,
                       @Value("${bytebank.outbox.batch-size}") int batchSize,
                       @Value("${bytebank.outbox.send-timeout-ms}") long sendTimeoutMs) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.batchSize = batchSize;
        this.sendTimeoutMs = sendTimeoutMs;
    }

    @Scheduled(fixedDelayString = "${bytebank.outbox.relay-delay-ms}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> pending = outbox.findPendingForUpdate(batchSize);
        for (OutboxEvent event : pending) {
            if (!send(event)) {
                return;
            }
        }
        if (!pending.isEmpty()) {
            log.debug("Publicados {} eventos del outbox", pending.size());
        }
    }

    /** Envía un evento y espera la confirmación de Kafka. Devuelve false si no se ha podido publicar. */
    private boolean send(OutboxEvent event) {
        try {
            kafka.send(event.getTopic(), event.getMessageKey(), event.getPayload())
                    .get(sendTimeoutMs, TimeUnit.MILLISECONDS);
            event.markPublished(Instant.now());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            event.markFailed("Interrumpido mientras se publicaba");
            return false;
        } catch (ExecutionException | TimeoutException  | RuntimeException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            event.markFailed(cause.toString());
            log.warn("No se ha podido publicar el evento {} del outbox (intento {}): {}",
                    event.getId(), event.getAttempts(), cause.toString());
            return false;
        }
    }
}

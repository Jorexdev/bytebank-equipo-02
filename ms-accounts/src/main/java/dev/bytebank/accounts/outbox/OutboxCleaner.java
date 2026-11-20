package dev.bytebank.accounts.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Borra del outbox los eventos ya publicados con más antigüedad que la retención configurada.
 * Se conservan unos días para poder comprobar qué se publicó y cuándo si hay una incidencia.
 */
@Component
public class OutboxCleaner {

    private static final Logger log = LoggerFactory.getLogger(OutboxCleaner.class);

    private final OutboxEventRepository outbox;
    private final Duration retention;

    public OutboxCleaner(OutboxEventRepository outbox, @Value("${bytebank.outbox.retention-days}") long retentionDays) {
        this.outbox = outbox;
        this.retention = Duration.ofDays(retentionDays);
    }

    @Scheduled(cron = "${bytebank.outbox.cleanup-cron}", zone = "${bytebank.zone}")
    @Transactional
    public int deleteOldPublished() {
        int deleted = outbox.deletePublishedBefore(Instant.now().minus(retention));
        if (deleted > 0) {
            log.info("Borrados {} eventos publicados del outbox con más de {} días", deleted, retention.toDays());
        }
        return deleted;
    }
}

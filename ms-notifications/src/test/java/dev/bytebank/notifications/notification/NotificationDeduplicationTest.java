package dev.bytebank.notifications.notification;

import dev.bytebank.notifications.event.MovementEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * KAF-03: Kafka entrega cada evento al menos una vez, así que el mismo movimiento puede llegar repetido
 * (por ejemplo, al reiniciar el servicio con offsets sin confirmar). Un titular no debe recibir dos avisos
 * del mismo movimiento. Se prueba contra PostgreSQL real porque la garantía final es la restricción única.
 *
 * El contenedor es singleton (se arranca una vez por JVM), igual que en IntegrationTest de ms-accounts.
 */
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "bytebank.internal-key=clave-interna-de-test",
        "bytebank.jwt.secret=clave-secreta-de-test-que-debe-ser-muy-larga-para-pasar-la-validacion"})
class NotificationDeduplicationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    static {
        POSTGRES.start();
    }

    static final String IBAN = "ES7639990001000000000011";

    @Autowired
    NotificationService service;
    @Autowired
    NotificationRepository notifications;

    @AfterEach
    void cleanUp() {
        notifications.deleteAll();
    }

    @Test
    void redeliveredEventDoesNotNotifyTwice() {
        MovementEvent event = event(154L, List.of(2L, 3L));

        service.notifyHolders(event);
        service.notifyHolders(event); // reentrega de Kafka

        assertThat(notifications.findAll())
                .extracting(Notification::getUserId)
                .containsExactlyInAnyOrder(2L, 3L);
    }

    @Test
    void redeliveryOnlyAddsTheHoldersThatWereMissing() {
        notifications.save(notification(155L, 2L));

        service.notifyHolders(event(155L, List.of(2L, 3L)));

        assertThat(notifications.findAll())
                .extracting(Notification::getUserId)
                .containsExactlyInAnyOrder(2L, 3L);
    }

    @Test
    void differentMovementsAreNotifiedSeparately() {
        service.notifyHolders(event(156L, List.of(2L)));
        service.notifyHolders(event(157L, List.of(2L)));

        assertThat(notifications.findAll()).hasSize(2);
    }

    @Test
    void databaseRejectsADuplicateNotification() {
        notifications.saveAndFlush(notification(158L, 2L));

        assertThatThrownBy(() -> notifications.saveAndFlush(notification(158L, 2L)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static MovementEvent event(Long movementId, List<Long> holderIds) {
        return new MovementEvent(movementId, IBAN, holderIds, "INGRESO", new BigDecimal("10.00"),
                new BigDecimal("10.00"), "Nómina", "REF-" + movementId, Instant.parse("2026-11-17T10:00:00Z"));
    }

    private static Notification notification(Long movementId, Long userId) {
        return new Notification(userId, movementId, IBAN, "INGRESO", new BigDecimal("10.00"),
                new BigDecimal("10.00"), "REF-" + movementId, "Aviso", Instant.parse("2026-11-17T10:00:00Z"));
    }
}

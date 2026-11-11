package dev.bytebank.notifications.notification;

import dev.bytebank.notifications.event.MovementEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationMessagesTest {

    @Test
    void sentTransfer() {
        MovementEvent event = new MovementEvent(1L, "ES7639990001000000000011", 2L, "TRANSFERENCIA_EMITIDA",
                new BigDecimal("-12.50"), new BigDecimal("87.50"), "Cena", "TRF-1", Instant.now());

        assertThat(NotificationMessages.of(event))
                .isEqualTo("Has enviado 12,50 € desde tu cuenta ···0011 (Cena). Saldo: 87,50 €");
    }

    @Test
    void depositWithoutConcept() {
        MovementEvent event = new MovementEvent(2L, "ES7639990001000000000011", 2L, "INGRESO",
                new BigDecimal("1500.00"), new BigDecimal("1587.50"), null, null, Instant.now());

        assertThat(NotificationMessages.of(event))
                .isEqualTo("Ingreso de 1.500,00 € en tu cuenta ···0011. Saldo: 1.587,50 €");
    }
}

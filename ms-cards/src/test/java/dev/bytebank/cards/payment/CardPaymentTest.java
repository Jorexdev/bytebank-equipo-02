package dev.bytebank.cards.payment;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CardPaymentTest {

    @Test
    void keepsTheTraceReference() {
        CardPayment payment = new CardPayment("CARD-abc", 1L, new BigDecimal("42.50"), "Supermercado",
                Instant.parse("2026-11-16T10:00:00Z"));

        assertThat(payment.getReference()).isEqualTo("CARD-abc");
    }
}

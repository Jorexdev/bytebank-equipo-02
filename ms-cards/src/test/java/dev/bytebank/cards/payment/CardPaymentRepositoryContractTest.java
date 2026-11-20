package dev.bytebank.cards.payment;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CardPaymentRepositoryContractTest {

    @Test
    void exposesApprovedAmountQuery() throws Exception {
        var method = CardPaymentRepository.class.getMethod("sumApprovedAmountSince", Long.class, Instant.class);

        assertThat(method.getReturnType()).isEqualTo(BigDecimal.class);
    }
}

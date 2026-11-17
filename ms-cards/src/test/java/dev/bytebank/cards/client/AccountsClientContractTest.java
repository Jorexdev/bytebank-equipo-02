package dev.bytebank.cards.client;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AccountsClientContractTest {

    @Test
    void movementRequestCarriesTheRequestedLedgerType() {
        var debit = new AccountsClient.MovementRequest("CARD-abc", "ES123", new BigDecimal("42.50"),
                "PAGO_TARJETA", "Pago en Supermercado");
        var reversal = new AccountsClient.MovementRequest("CARD-abc-REV", "ES123", new BigDecimal("42.50"),
                "INGRESO", "Reverso del pago CARD-abc");

        assertThat(debit.type()).isEqualTo("PAGO_TARJETA");
        assertThat(reversal.type()).isEqualTo("INGRESO");
    }

    @Test
    void exposesDebitAndCreditOperations() throws Exception {
        assertThat(AccountsClient.class.getMethod("debit", String.class, String.class, BigDecimal.class, String.class))
                .returns(AccountsClient.LedgerResult.class, java.lang.reflect.Method::getReturnType);
        assertThat(AccountsClient.class.getMethod("credit", String.class, String.class, BigDecimal.class, String.class))
                .returns(AccountsClient.LedgerResult.class, java.lang.reflect.Method::getReturnType);
    }
}

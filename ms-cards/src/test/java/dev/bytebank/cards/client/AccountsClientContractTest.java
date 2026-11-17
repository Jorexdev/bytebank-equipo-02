package dev.bytebank.cards.client;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import dev.bytebank.common.http.InternalClients;
import org.mockito.ArgumentCaptor;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Test
    void creditSendsAnIncomeReversalToTheCreditEndpoint() {
        InternalClients clients = mock(InternalClients.class);
        RestClient rest = mock(RestClient.class, RETURNS_DEEP_STUBS);
        when(clients.forService(anyString())).thenReturn(rest);
        when(rest.post().uri("/internal/accounts/credit").body(any()).retrieve()
                .body(AccountsClient.LedgerResult.class)).thenReturn(
                new AccountsClient.LedgerResult("CARD-abc-REV", new BigDecimal("100.00")));

        AccountsClient accountClient = new AccountsClient(clients, "http://accounts");
        accountClient.credit("CARD-abc-REV", "ES123", new BigDecimal("42.50"), "Reverso del pago CARD-abc");

        ArgumentCaptor<AccountsClient.MovementRequest> request = ArgumentCaptor.forClass(AccountsClient.MovementRequest.class);
        verify(rest.post().uri("/internal/accounts/credit")).body(request.capture());
        assertThat(request.getValue().type()).isEqualTo("INGRESO");
        assertThat(request.getValue().reference()).isEqualTo("CARD-abc-REV");
    }
}

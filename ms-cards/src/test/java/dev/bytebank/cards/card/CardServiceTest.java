package dev.bytebank.cards.card;

import dev.bytebank.cards.client.AccountsClient;
import dev.bytebank.cards.client.AccountsClient.AccountInfo;
import dev.bytebank.cards.payment.CardPaymentRepository;
import dev.bytebank.common.error.ApiException;
import dev.bytebank.common.iban.Iban;
import dev.bytebank.common.security.AuthUser;
import dev.bytebank.common.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CardServiceTest {

    static final String IBAN = Iban.generate(97);
    static final AuthUser ANA = new AuthUser(2L, "ana@bytebank.test", Role.CLIENTE);

    CardRepository cards;
    CardPaymentRepository payments;
    AccountsClient accountsClient;
    CardService service;

    @BeforeEach
    void setUp() {
        cards = mock(CardRepository.class);
        payments = mock(CardPaymentRepository.class);
        accountsClient = mock(AccountsClient.class);
        service = new CardService(cards, payments, accountsClient, Clock.systemUTC(),
                "Europe/Madrid", new BigDecimal("600.00"));
        when(cards.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void issuesCardForHolder() {
        when(accountsClient.getAccount(IBAN))
                .thenReturn(new AccountInfo(IBAN, "ACTIVA", new BigDecimal("100.00"), List.of(2L)));

        Card card = service.issue(IBAN, ANA);

        assertThat(card.getAccountIban()).isEqualTo(IBAN);
        assertThat(card.getUserId()).isEqualTo(2L);
        assertThat(card.getLast4()).matches("\\d{4}");
        assertThat(card.getDailyLimit()).isEqualByComparingTo("600.00");
        assertThat(card.getStatus()).isEqualTo(CardStatus.ACTIVA);
    }

    @Test
    void rejectsNonHolder() {
        when(accountsClient.getAccount(IBAN))
                .thenReturn(new AccountInfo(IBAN, "ACTIVA", new BigDecimal("100.00"), List.of(99L)));

        assertThatThrownBy(() -> service.issue(IBAN, ANA))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> {
                    ApiException api = (ApiException) error;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(api.getCode()).isEqualTo("CUENTA_NO_ENCONTRADA");
                });
    }

    @Test
    void rejectsInactiveAccount() {
        when(accountsClient.getAccount(IBAN))
                .thenReturn(new AccountInfo(IBAN, "BLOQUEADA", new BigDecimal("100.00"), List.of(2L)));

        assertThatThrownBy(() -> service.issue(IBAN, ANA))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> {
                    ApiException api = (ApiException) error;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.getCode()).isEqualTo("CUENTA_NO_OPERATIVA");
                });
    }

    @Test
    void propagatesUnknownAccount() {
        when(accountsClient.getAccount(IBAN))
                .thenThrow(ApiException.notFound("CUENTA_NO_ENCONTRADA", "No existe la cuenta"));

        assertThatThrownBy(() -> service.issue(IBAN, ANA))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}

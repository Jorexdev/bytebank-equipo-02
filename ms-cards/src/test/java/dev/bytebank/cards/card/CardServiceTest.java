package dev.bytebank.cards.card;

import dev.bytebank.cards.client.AccountsClient;
import dev.bytebank.cards.client.AccountsClient.AccountInfo;
import dev.bytebank.cards.payment.CardPaymentRepository;
import dev.bytebank.cards.payment.CardPayment;
import dev.bytebank.cards.payment.PaymentStatus;
import dev.bytebank.common.error.ApiException;
import dev.bytebank.common.http.RemoteUnavailableException;
import dev.bytebank.common.iban.Iban;
import dev.bytebank.common.security.AuthUser;
import dev.bytebank.common.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
        when(payments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(payments.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
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

    @Test
    void approvesPaymentAndDebitsTheAccount() throws Exception {
        Card card = card(1L, CardStatus.ACTIVA);
        locked(card);
        when(payments.sumApprovedAmountSince(eq(1L), any())).thenReturn(BigDecimal.ZERO);

        CardPayment payment = service.pay(1L, new BigDecimal("42.50"), "Supermercado", ANA);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APROBADO);
        assertThat(payment.getReference()).startsWith("CARD-");
        verify(accountsClient).debit(startsWithCardReference(), eq(IBAN), eq(new BigDecimal("42.50")),
                eq("Pago en Supermercado"));
        verify(payments).saveAndFlush(payment);
    }

    @Test
    void rejectsBlockedCardWithoutDebiting() throws Exception {
        Card card = card(1L, CardStatus.BLOQUEADA);
        locked(card);

        CardPayment payment = service.pay(1L, new BigDecimal("42.50"), "Supermercado", ANA);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.RECHAZADO);
        assertThat(payment.getRejectionReason()).isEqualTo("TARJETA_BLOQUEADA");
        verify(accountsClient, never()).debit(anyString(), anyString(), any(), anyString());
        verify(payments).save(payment);
    }

    @Test
    void rejectsWhenApprovedPaymentsExceedDailyLimit() throws Exception {
        Card card = card(1L, CardStatus.ACTIVA);
        locked(card);
        when(payments.sumApprovedAmountSince(eq(1L), any())).thenReturn(new BigDecimal("590.00"));

        CardPayment payment = service.pay(1L, new BigDecimal("10.01"), "Supermercado", ANA);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.RECHAZADO);
        assertThat(payment.getRejectionReason()).isEqualTo("LIMITE_DIARIO_SUPERADO");
        verify(accountsClient, never()).debit(anyString(), anyString(), any(), anyString());
    }

    @Test
    void allowsPaymentExactlyAtDailyLimit() throws Exception {
        Card card = card(1L, CardStatus.ACTIVA);
        locked(card);
        when(payments.sumApprovedAmountSince(eq(1L), any())).thenReturn(new BigDecimal("590.00"));

        CardPayment payment = service.pay(1L, new BigDecimal("10.00"), "Supermercado", ANA);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APROBADO);
    }

    @Test
    void doesNotSaveWhenAccountsIsUnavailable() throws Exception {
        Card card = card(1L, CardStatus.ACTIVA);
        locked(card);
        when(payments.sumApprovedAmountSince(eq(1L), any())).thenReturn(BigDecimal.ZERO);
        when(accountsClient.debit(anyString(), anyString(), any(), anyString()))
                .thenThrow(new RemoteUnavailableException("ms-accounts no responde"));

        assertThatThrownBy(() -> service.pay(1L, new BigDecimal("42.50"), "Supermercado", ANA))
                .isInstanceOf(RemoteUnavailableException.class)
                .extracting(error -> ((RemoteUnavailableException) error).getCode())
                .isEqualTo("SERVICIO_NO_DISPONIBLE");
        verify(payments, never()).save(any());
        verify(payments, never()).saveAndFlush(any());
    }

    @Test
    void savesBusinessRejectionFromAccounts() throws Exception {
        Card card = card(1L, CardStatus.ACTIVA);
        locked(card);
        when(payments.sumApprovedAmountSince(eq(1L), any())).thenReturn(BigDecimal.ZERO);
        when(accountsClient.debit(anyString(), anyString(), any(), anyString()))
                .thenThrow(ApiException.conflict("FONDOS_INSUFICIENTES", "Saldo insuficiente"));

        CardPayment payment = service.pay(1L, new BigDecimal("42.50"), "Supermercado", ANA);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.RECHAZADO);
        assertThat(payment.getRejectionReason()).isEqualTo("FONDOS_INSUFICIENTES");
        verify(payments).save(payment);
    }

    @Test
    void rethrowsUnexpectedRemoteErrorWithoutSaving() throws Exception {
        Card card = card(1L, CardStatus.ACTIVA);
        locked(card);
        when(payments.sumApprovedAmountSince(eq(1L), any())).thenReturn(BigDecimal.ZERO);
        when(accountsClient.debit(anyString(), anyString(), any(), anyString()))
                .thenThrow(ApiException.forbidden("clave inválida"));

        assertThatThrownBy(() -> service.pay(1L, new BigDecimal("42.50"), "Supermercado", ANA))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).getCode())
                .isEqualTo("ACCESO_DENEGADO");
        verify(payments, never()).save(any());
    }

    @Test
    void compensatesDebitWhenLocalSaveFails() throws Exception {
        Card card = card(1L, CardStatus.ACTIVA);
        locked(card);
        when(payments.sumApprovedAmountSince(eq(1L), any())).thenReturn(BigDecimal.ZERO);
        doThrow(new IllegalStateException("db caída")).when(payments).saveAndFlush(any());

        assertThatThrownBy(() -> service.pay(1L, new BigDecimal("42.50"), "Supermercado", ANA))
                .isInstanceOf(IllegalStateException.class);
        verify(accountsClient).credit(argThat(reference -> reference.endsWith("-REV")), eq(IBAN),
                eq(new BigDecimal("42.50")), argThat(concept -> concept.startsWith("Reverso del pago CARD-")));
    }

    private Card card(Long id, CardStatus status) throws Exception {
        Card card = new Card(IBAN, ANA.id(), "4821", new BigDecimal("600.00"));
        card.changeStatus(status);
        Field field = Card.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(card, id);
        return card;
    }

    private void locked(Card card) {
        when(cards.findByIdForUpdate(card.getId())).thenReturn(Optional.of(card));
    }

    private static String startsWithCardReference() {
        return argThat(reference -> reference.startsWith("CARD-"));
    }
}

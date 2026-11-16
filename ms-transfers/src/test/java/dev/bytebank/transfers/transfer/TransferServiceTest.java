package dev.bytebank.transfers.transfer;

import dev.bytebank.common.error.ApiException;
import dev.bytebank.common.http.RemoteUnavailableException;
import dev.bytebank.common.iban.Iban;
import dev.bytebank.common.security.AuthUser;
import dev.bytebank.common.security.Role;
import dev.bytebank.common.web.PageResponse;
import dev.bytebank.transfers.client.AccountsClient;
import dev.bytebank.transfers.client.AccountsClient.AccountInfo;
import dev.bytebank.transfers.client.FraudClient;
import dev.bytebank.transfers.transfer.TransferDtos.CreateTransferRequest;
import dev.bytebank.transfers.transfer.TransferDtos.TransferResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    static final String ORIGIN = Iban.generate(1);
    static final String DESTINATION = Iban.generate(2);
    static final AuthUser ANA = new AuthUser(2L, "ana@bytebank.test", Role.CLIENTE);
    static final AuthUser LUIS = new AuthUser(3L, "luis@bytebank.test", Role.CLIENTE);
    static final AuthUser GESTOR = new AuthUser(1L, "gestor@bytebank.test", Role.GESTOR);

    @Mock
    TransferRepository transfers;
    @Mock
    AccountsClient accounts;
    @Mock
    FraudClient fraud;

    @InjectMocks
    TransferService service;

    @BeforeEach
    void setUp() {
        lenient().when(transfers.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(accounts.getAccount(ORIGIN))
                .thenReturn(new AccountInfo(ORIGIN, "ACTIVA", new BigDecimal("1000"), List.of(2L)));
        lenient().when(accounts.getAccount(DESTINATION))
                .thenReturn(new AccountInfo(DESTINATION, "ACTIVA", new BigDecimal("100"), List.of(3L)));
        lenient().when(fraud.check(anyString(), anyString(), any(), any()))
                .thenReturn(new FraudClient.CheckResponse(1L, FraudClient.Verdict.APROBADA, List.of()));
    }

    @Test
    void completesTransferBetweenOwnAccounts() {
        Transfer t = service.create(new CreateTransferRequest(ORIGIN, DESTINATION, new BigDecimal("100.00"), "Cena"), ANA);

        assertThat(t.getStatus()).isEqualTo(TransferStatus.COMPLETADA);
    }

    @Test
    void coHolderCanTransferFromSharedAccount() {
        when(accounts.getAccount(ORIGIN))
                .thenReturn(new AccountInfo(ORIGIN, "ACTIVA", new BigDecimal("1000"), List.of(2L, 3L)));

        Transfer t = service.create(
                new CreateTransferRequest(ORIGIN, DESTINATION, new BigDecimal("100.00"), "Cena"), LUIS);

        assertThat(t.getStatus()).isEqualTo(TransferStatus.COMPLETADA);
    }

    @Test
    void rejectsWhenOriginHasNoFunds() {
        when(accounts.debit(anyString(), eq(ORIGIN), any(), any()))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, "FONDOS_INSUFICIENTES", "Saldo insuficiente"));

        Transfer t = service.create(new CreateTransferRequest(ORIGIN, DESTINATION, new BigDecimal("5000.00"), null), ANA);

        assertThat(t.getStatus()).isEqualTo(TransferStatus.RECHAZADA);
    }

    @Test
    void transferToBlockedAccountIsRejectedWithoutMovingMoney() {
        when(accounts.getAccount(DESTINATION))
                .thenReturn(new AccountInfo(DESTINATION, "BLOQUEADA", new BigDecimal("100"), List.of(3L)));

        Transfer t = service.create(new CreateTransferRequest(ORIGIN, DESTINATION, new BigDecimal("50.00"), null), ANA);

        assertThat(t.getStatus()).isEqualTo(TransferStatus.RECHAZADA);
        assertThat(t.getRejectionReason()).isEqualTo("CUENTA_NO_OPERATIVA");
        verify(accounts, never()).debit(anyString(), anyString(), any(), any());
        verify(accounts, never()).credit(anyString(), anyString(), any(), any());
    }

    @Test
    void moneyIsReturnedToOriginWhenCreditFails() {
        when(accounts.credit(anyString(), eq(DESTINATION), any(), any()))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, "CUENTA_NO_OPERATIVA", "La cuenta no está activa"));

        Transfer t = service.create(new CreateTransferRequest(ORIGIN, DESTINATION, new BigDecimal("50.00"), null), ANA);

        assertThat(t.getStatus()).isEqualTo(TransferStatus.RECHAZADA);
        // el reverso devuelve el dinero al IBAN de origen
        verify(accounts).credit(anyString(), eq(ORIGIN), eq(new BigDecimal("50.00")), anyString());
    }

    @Test
    void cannotTransferToSameAccount() {
        assertThatThrownBy(() -> service.create(new CreateTransferRequest(ORIGIN, ORIGIN, BigDecimal.TEN, null), ANA))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsNonPositiveAmount() {
        assertThatThrownBy(() -> service.create(
                new CreateTransferRequest(ORIGIN, DESTINATION, new BigDecimal("-100.00"), null), ANA))
                .isInstanceOf(ApiException.class);

        verify(accounts, never()).debit(anyString(), anyString(), any(), any());
    }

    @Test
    void transferFromUnknownAccountIsNotFound() {
        when(accounts.getAccount(ORIGIN)).thenThrow(
                ApiException.notFound("CUENTA_NO_ENCONTRADA", "No existe la cuenta"));

        assertThatThrownBy(() -> service.create(
                new CreateTransferRequest(ORIGIN, DESTINATION, new BigDecimal("100.00"), null), ANA))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);

        verify(transfers, never()).save(any());
    }

    @Test
    void cannotTransferFromAnotherUsersAccount() {
        when(accounts.getAccount(ORIGIN))
                .thenReturn(new AccountInfo(
                        ORIGIN,
                        "ACTIVA",
                        new BigDecimal("1000"),
                        List.of(99L)));

        assertThatThrownBy(() -> service.create(
                new CreateTransferRequest(
                        ORIGIN,
                        DESTINATION,
                        new BigDecimal("100.00"),
                        "Transferencia no autorizada"),
                ANA))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);

        verify(transfers, never()).save(any());
        verify(accounts, never()).debit(anyString(), anyString(), any(), any());
        verify(accounts, never()).credit(anyString(), anyString(), any(), any());
    }

    @Test
    void transfersOver5000ArePendingWithoutMovingMoney() {
        Transfer t = service.create(new CreateTransferRequest(ORIGIN, DESTINATION, new BigDecimal("6000.00"), null), ANA);

        assertThat(t.getStatus()).isEqualTo(TransferStatus.PENDIENTE);
        verify(accounts, never()).debit(anyString(), anyString(), any(), any());
        verify(accounts, never()).credit(anyString(), anyString(), any(), any());
    }

    @Test
    void fraudBlockedTransferIsRejectedWithoutMovingMoney() {
        when(fraud.check(anyString(), anyString(), any(), any()))
                .thenReturn(new FraudClient.CheckResponse(2L, FraudClient.Verdict.BLOQUEADA,
                        List.of("IMPORTE_MAXIMO")));

        Transfer t = service.create(
                new CreateTransferRequest(ORIGIN, DESTINATION, new BigDecimal("21000.00"), null), ANA);

        assertThat(t.getStatus()).isEqualTo(TransferStatus.RECHAZADA);
        assertThat(t.getRejectionReason()).isEqualTo("IMPORTE_MAXIMO");
        verify(accounts, never()).debit(anyString(), anyString(), any(), any());
        verify(accounts, never()).credit(anyString(), anyString(), any(), any());
    }

    @Test
    void fraudReviewLeavesTransferPendingWithoutMovingMoney() {
        when(fraud.check(anyString(), anyString(), any(), any()))
                .thenReturn(new FraudClient.CheckResponse(3L, FraudClient.Verdict.REVISAR,
                        List.of("EXTERNA_IMPORTE_ALTO")));

        Transfer t = service.create(
                new CreateTransferRequest(ORIGIN, DESTINATION, new BigDecimal("1500.00"), null), ANA);

        assertThat(t.getStatus()).isEqualTo(TransferStatus.PENDIENTE);
        verify(accounts, never()).debit(anyString(), anyString(), any(), any());
        verify(accounts, never()).credit(anyString(), anyString(), any(), any());
    }

    @Test
    void sendsTheTransferDataToFraud() {
        service.create(new CreateTransferRequest(ORIGIN, DESTINATION, new BigDecimal("100.00"), "Cena"), ANA);

        verify(fraud).check(ORIGIN, DESTINATION, ANA.id(), new BigDecimal("100.00"));
    }

    @Test
    void transferFailsWhenFraudServiceIsUnavailable() {
        when(fraud.check(anyString(), anyString(), any(), any()))
                .thenThrow(new RemoteUnavailableException("ms-fraud no responde"));

        assertThatThrownBy(() -> service.create(
                new CreateTransferRequest(ORIGIN, DESTINATION, new BigDecimal("100.00"), null), ANA))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode())
                .isEqualTo("SERVICIO_NO_DISPONIBLE");

        verify(transfers, never()).save(any());
        verify(accounts, never()).debit(anyString(), anyString(), any(), any());
    }

    @Test
    void approveExecutesPendingTransfer() {
        Transfer pending = pendingTransfer();
        when(transfers.findById(7L)).thenReturn(Optional.of(pending));
        when(transfers.updateStatusIfPending(7L, TransferStatus.COMPLETADA)).thenReturn(1);

        Transfer t = service.approve(7L);

        assertThat(t.getStatus()).isEqualTo(TransferStatus.COMPLETADA);
        verify(accounts).debit(anyString(), eq(ORIGIN), eq(new BigDecimal("6000.00")), any());
        verify(accounts).credit(anyString(), eq(DESTINATION), eq(new BigDecimal("6000.00")), any());
    }

    @Test
    void rejectMarksPendingTransferAsRejectedWithoutMovingMoney() {
        Transfer pending = pendingTransfer();
        when(transfers.findById(7L)).thenReturn(Optional.of(pending));
        when(transfers.updateStatusIfPending(7L, TransferStatus.RECHAZADA)).thenReturn(1);

        Transfer t = service.reject(7L);

        assertThat(t.getStatus()).isEqualTo(TransferStatus.RECHAZADA);
        assertThat(t.getRejectionReason()).isEqualTo("RECHAZADA_POR_GESTOR");
        verify(accounts, never()).debit(anyString(), anyString(), any(), any());
        verify(accounts, never()).credit(anyString(), anyString(), any(), any());
    }

    @Test
    void approveOnlyAcceptsPendingTransfers() {
        Transfer completed = new Transfer(ORIGIN, DESTINATION, new BigDecimal("6000.00"), null, 2L);
        when(transfers.findById(7L)).thenReturn(Optional.of(completed));

        assertThatThrownBy(() -> service.approve(7L))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode())
                .isEqualTo("TRANSFERENCIA_NO_PENDIENTE");

        verify(accounts, never()).debit(anyString(), anyString(), any(), any());
    }

    @Test
    void approveFailsWhenAnotherGestorAlreadyClaimedIt() {
        Transfer pending = pendingTransfer();
        when(transfers.findById(7L)).thenReturn(Optional.of(pending));
        when(transfers.updateStatusIfPending(7L, TransferStatus.COMPLETADA)).thenReturn(0);

        assertThatThrownBy(() -> service.approve(7L))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode())
                .isEqualTo("TRANSFERENCIA_NO_PENDIENTE");

        verify(accounts, never()).debit(anyString(), anyString(), any(), any());
    }

    @Test
    void approvedTransferIsRejectedWhenDebitFails() {
        Transfer pending = pendingTransfer();
        when(transfers.findById(7L)).thenReturn(Optional.of(pending));
        when(transfers.updateStatusIfPending(7L, TransferStatus.COMPLETADA)).thenReturn(1);
        when(accounts.debit(anyString(), eq(ORIGIN), any(), any()))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, "FONDOS_INSUFICIENTES", "Saldo insuficiente"));

        Transfer t = service.approve(7L);

        assertThat(t.getStatus()).isEqualTo(TransferStatus.RECHAZADA);
        assertThat(t.getRejectionReason()).isEqualTo("FONDOS_INSUFICIENTES");
        verify(accounts, never()).credit(anyString(), anyString(), any(), any());
    }

    @Test
void gestorCanSeeAnyTransfer() {
    Transfer pending = pendingTransfer();
    when(transfers.findById(7L)).thenReturn(Optional.of(pending));

    assertThat(service.get(7L, GESTOR)).isSameAs(pending);
}

@Test
void clientCannotSeeTransfersOfOtherUsers() {
    Transfer pending = pendingTransfer();
    when(transfers.findById(7L)).thenReturn(Optional.of(pending));

    assertThatThrownBy(() -> service.get(7L, LUIS)).isInstanceOf(ApiException.class);
}

@Test
void listPendingReturnsTransfersWaitingForApproval() {
    when(transfers.findByStatus(eq(TransferStatus.PENDIENTE), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(pendingTransfer())));

    PageResponse<TransferResponse> page = service.listPending(0, 20);

    assertThat(page.content()).hasSize(1);
    assertThat(page.content().get(0).status()).isEqualTo(TransferStatus.PENDIENTE);
}

    private static Transfer pendingTransfer() {
        Transfer transfer = new Transfer(ORIGIN, DESTINATION, new BigDecimal("6000.00"), "Coche", 2L);
        transfer.pending();
        return transfer;
    }
}

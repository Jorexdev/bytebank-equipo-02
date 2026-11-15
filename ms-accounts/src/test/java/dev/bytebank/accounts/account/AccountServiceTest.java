package dev.bytebank.accounts.account;

import dev.bytebank.accounts.client.AuthClient;
import dev.bytebank.accounts.event.MovementPublisher;
import dev.bytebank.accounts.movement.MovementRepository;
import dev.bytebank.common.error.ApiException;
import dev.bytebank.common.iban.Iban;
import dev.bytebank.common.security.AuthUser;
import dev.bytebank.common.security.Role;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    static final String IBAN = Iban.generate(99);

    @Mock
    AccountRepository accounts;
    @Mock
    MovementRepository movements;
    @Mock
    MovementPublisher publisher;
    @Mock
    AuthClient auth;

    @InjectMocks
    AccountService service;

    @Test
    void unknownIbanIsNotFound() {
        when(accounts.findByIban(IBAN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(IBAN))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus(), e -> ((ApiException) e).getCode())
                .containsExactly(HttpStatus.NOT_FOUND, "CUENTA_NO_ENCONTRADA");
    }

    @Test
    void knownIbanIsReturned() {
        Account account = account(IBAN, 1L);
        when(accounts.findByIban(IBAN)).thenReturn(Optional.of(account));

        assertThat(service.get(IBAN)).isSameAs(account);
    }

    @Test
    void clientCanOnlyListOwnAccounts() {
        AuthUser client = new AuthUser(1L, "client@test.com", Role.CLIENTE);
        Account account = account(IBAN, 1L);

        when(accounts.findByHolderId(1L)).thenReturn(List.of(account));

        assertThat(service.listForUser(client)).containsExactly(account);
    }

    @Test
    void gestorCanListAllAccounts() {
        AuthUser gestor = new AuthUser(2L, "gestor@test.com", Role.GESTOR);
        Account firstAccount = account(Iban.generate(100), 1L);
        Account secondAccount = account(Iban.generate(101), 3L);

        when(accounts.findAllByOrderByCreatedAtAscIdAsc())
                .thenReturn(List.of(firstAccount, secondAccount));

        assertThat(service.listForUser(gestor))
                .containsExactly(firstAccount, secondAccount);
    }

    @Test
    void clientCanAccessOwnAccount() {
        AuthUser client = new AuthUser(1L, "client@test.com", Role.CLIENTE);
        Account account = account(IBAN, 1L);

        when(accounts.findByIban(IBAN)).thenReturn(Optional.of(account));

        assertThat(service.getForUser(IBAN, client)).isSameAs(account);
    }

    @Test
    void clientCannotAccessAnotherUsersAccount() {
        AuthUser client = new AuthUser(1L, "client@test.com", Role.CLIENTE);
        Account account = account(IBAN, 2L);

        when(accounts.findByIban(IBAN)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.getForUser(IBAN, client))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).getStatus())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void gestorCanAccessAnotherUsersAccount() {
        AuthUser gestor = new AuthUser(2L, "gestor@test.com", Role.GESTOR);
        Account account = account(IBAN, 1L);

        when(accounts.findByIban(IBAN)).thenReturn(Optional.of(account));

        assertThat(service.getForUser(IBAN, gestor)).isSameAs(account);
    }

    private static Account account(String iban, Long... holderIds) {
        Account account = new Account(iban);
        for (Long holderId : holderIds) {
            account.addHolder(holderId);
        }
        return account;
    }
}

package dev.bytebank.accounts.account;

import dev.bytebank.accounts.client.AuthClient;
import dev.bytebank.accounts.client.AuthClient.UserInfo;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountHolderServiceTest {

    static final String IBAN = Iban.generate(98);
    static final AuthUser ANA = new AuthUser(2L, "ana@bytebank.test", Role.CLIENTE);

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
    void holderAddsClientByEmail() {
        Account account = accountWithHolders(2L);
        when(accounts.findByIbanForUpdate(IBAN)).thenReturn(Optional.of(account));
        when(auth.findByEmail("luis@bytebank.test"))
                .thenReturn(new UserInfo(3L, "luis@bytebank.test", "Luis", Role.CLIENTE));

        assertThat(service.addHolder(IBAN, "luis@bytebank.test", ANA).getHolderIds())
                .containsExactly(2L, 3L);
    }

    @Test
    void nonHolderCannotAdd() {
        when(accounts.findByIbanForUpdate(IBAN)).thenReturn(Optional.of(accountWithHolders(3L)));

        assertThatThrownBy(() -> service.addHolder(IBAN, "luis@bytebank.test", ANA))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode())
                .isEqualTo("ACCESO_DENEGADO");
    }

    @Test
    void rejectsDuplicateHolder() {
        when(accounts.findByIbanForUpdate(IBAN)).thenReturn(Optional.of(accountWithHolders(2L, 3L)));
        when(auth.findByEmail("luis@bytebank.test"))
                .thenReturn(new UserInfo(3L, "luis@bytebank.test", "Luis", Role.CLIENTE));

        assertThatThrownBy(() -> service.addHolder(IBAN, "luis@bytebank.test", ANA))
                .extracting(e -> ((ApiException) e).getCode())
                .isEqualTo("TITULAR_YA_EXISTE");
    }

    @Test
    void rejectsFifthHolder() {
        when(accounts.findByIbanForUpdate(IBAN)).thenReturn(Optional.of(accountWithHolders(2L, 3L, 4L, 5L)));
        when(auth.findByEmail("sexto@bytebank.test"))
                .thenReturn(new UserInfo(6L, "sexto@bytebank.test", "Sexto", Role.CLIENTE));

        assertThatThrownBy(() -> service.addHolder(IBAN, "sexto@bytebank.test", ANA))
                .extracting(e -> ((ApiException) e).getCode())
                .isEqualTo("MAXIMO_TITULARES_ALCANZADO");
    }

    @Test
    void rejectsGestorAsHolder() {
        when(accounts.findByIbanForUpdate(IBAN)).thenReturn(Optional.of(accountWithHolders(2L)));
        when(auth.findByEmail("gestor@bytebank.test"))
                .thenReturn(new UserInfo(1L, "gestor@bytebank.test", "Gestor", Role.GESTOR));

        assertThatThrownBy(() -> service.addHolder(IBAN, "gestor@bytebank.test", ANA))
                .extracting(e -> ((ApiException) e).getCode())
                .isEqualTo("USUARIO_NO_CLIENTE");
    }

    @Test
    void trimsEmailBeforeLookup() {
        when(accounts.findByIbanForUpdate(IBAN)).thenReturn(Optional.of(accountWithHolders(2L)));
        when(auth.findByEmail("luis@bytebank.test"))
                .thenReturn(new UserInfo(3L, "luis@bytebank.test", "Luis", Role.CLIENTE));

        service.addHolder(IBAN, "  luis@bytebank.test  ", ANA);

        verify(auth).findByEmail("luis@bytebank.test");
    }

    @Test
    void holdersReturnsNames() {
        when(accounts.findByIban(IBAN)).thenReturn(Optional.of(accountWithHolders(2L, 3L)));
        when(auth.findById(2L)).thenReturn(new UserInfo(2L, "ana@bytebank.test", "Ana", Role.CLIENTE));
        when(auth.findById(3L)).thenReturn(new UserInfo(3L, "luis@bytebank.test", "Luis", Role.CLIENTE));

        assertThat(service.holders(IBAN, ANA))
                .extracting(AccountDtos.HolderResponse::fullName)
                .containsExactly("Ana", "Luis");
    }

    private static Account accountWithHolders(Long... ids) {
        Account account = new Account(IBAN);
        for (Long id : ids) {
            account.addHolder(id);
        }
        return account;
    }
}

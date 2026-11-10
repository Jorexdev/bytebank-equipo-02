package dev.bytebank.accounts.account;

import dev.bytebank.common.error.ApiException;
import dev.bytebank.common.iban.Iban;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    static final String IBAN = Iban.generate(99);

    @Mock
    AccountRepository accounts;

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
        Account account = new Account(IBAN, 1L);
        when(accounts.findByIban(IBAN)).thenReturn(Optional.of(account));

        assertThat(service.get(IBAN)).isSameAs(account);
    }
}

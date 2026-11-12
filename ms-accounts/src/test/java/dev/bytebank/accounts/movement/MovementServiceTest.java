package dev.bytebank.accounts.movement;

import dev.bytebank.accounts.account.Account;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MovementServiceTest {

    @Mock
    MovementRepository movements;

    @InjectMocks
    MovementService service;

    @Test
    void returnsTheRequestedPage() {
        Account account = mock(Account.class);
        when(account.getId()).thenReturn(1L);
        when(movements.countInRange(anyLong(), any(), any())).thenReturn(3L);
        when(movements.findSlice(anyLong(), any(), any(), anyInt(), anyInt())).thenReturn(List.of(
                mock(Movement.class), mock(Movement.class), mock(Movement.class)));

        var page = service.statement(account, null, null, 0, 10);

        assertThat(page.content()).hasSize(3);
        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.totalPages()).isEqualTo(1);
    }

    @Test
    @Disabled("Falla en el portátil de Iván por la zona horaria, revisar")
    void filterByDateIncludesLastDay() {
        Account account = mock(Account.class);
        when(account.getId()).thenReturn(1L);

        service.statement(account, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31), 0, 10);

        // TODO comprobar que el 31 de mayo entra en el rango
    }

    @Test
    void firstPageStartsAtOffsetZero() {
        Account account = mock(Account.class);
        when(account.getId()).thenReturn(1L);
        when(movements.countInRange(anyLong(), any(), any())).thenReturn(25L);

        service.statement(account, null, null, 0, 10);
        service.statement(account, null, null, 2, 10);

        Mockito.verify(movements).findSlice(eq(1L), any(), any(), eq(10), eq(0));
        Mockito.verify(movements).findSlice(eq(1L), any(), any(), eq(10), eq(20));
    }
}

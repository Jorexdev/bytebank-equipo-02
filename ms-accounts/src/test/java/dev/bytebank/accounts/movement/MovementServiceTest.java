package dev.bytebank.accounts.movement;

import dev.bytebank.accounts.account.Account;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MovementServiceTest {

    @Mock MovementRepository movements;
    MovementService service;

    @BeforeEach
    void setUp() {
        service = new MovementService(movements, "Europe/Madrid");
    }

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
    void rangeIncludesLastDayInMadridTime() {
        Account account = mock(Account.class);
        when(account.getId()).thenReturn(1L);

        service.statement(account, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 15), 0, 10);

        ArgumentCaptor<Instant> start = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> end = ArgumentCaptor.forClass(Instant.class);
        verify(movements).countInRange(eq(1L), start.capture(), end.capture());

        // 1 nov 00:00 y 16 nov 00:00 en Madrid (UTC+1)
        assertThat(start.getValue()).isEqualTo(Instant.parse("2026-10-31T23:00:00Z"));
        assertThat(end.getValue()).isEqualTo(Instant.parse("2026-11-15T23:00:00Z"));
    }

    @Test
    void firstPageStartsAtOffsetZero() {
        Account account = mock(Account.class);
        when(account.getId()).thenReturn(1L);
        when(movements.countInRange(anyLong(), any(), any())).thenReturn(25L);

        service.statement(account, null, null, 0, 10);
        service.statement(account, null, null, 2, 10);

        verify(movements).findSlice(eq(1L), any(), any(), eq(10), eq(0));
        verify(movements).findSlice(eq(1L), any(), any(), eq(10), eq(20));
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

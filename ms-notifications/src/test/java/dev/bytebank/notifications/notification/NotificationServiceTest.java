package dev.bytebank.notifications.notification;

import dev.bytebank.common.web.PageResponse;
import dev.bytebank.notifications.event.MovementEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    static final String IBAN = "ES7639990001000000000011";

    @Mock
    NotificationRepository notifications;
    @InjectMocks
    NotificationService service;

    @Test
    void notifiesEveryHolder() {
        service.notifyHolders(event(List.of(2L, 3L)));

        verify(notifications, times(2)).save(any(Notification.class));
    }

    @Test
    void ignoresEventWithoutHolders() {
        service.notifyHolders(event(null));

        verifyNoInteractions(notifications);
    }

    @Test
    void listsOnlyTheCallerNotificationsNewestFirst() {
        when(notifications.findByUserId(eq(2L), any(Pageable.class))).thenReturn(Page.empty());

        service.list(2L, 3, 5);

        var pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(notifications).findByUserId(eq(2L), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(3);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
        // createdAt descendente y, a igualdad de fecha, id descendente: el orden no cambia entre páginas.
        assertThat(pageable.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }

    @Test
    void returnsTheContractFieldsAndPageInformation() {
        Instant createdAt = Instant.parse("2026-06-12T10:05:00Z");
        Notification notification = new Notification(2L, 154L, IBAN, "TRANSFERENCIA_EMITIDA",
                new BigDecimal("-50.00"), new BigDecimal("2450.00"), "TRF-8f2c",
                "Has enviado 50,00 € desde tu cuenta ···0011 (Alquiler). Saldo: 2.450,00 €", createdAt);
        ReflectionTestUtils.setField(notification, "id", 31L); // el id lo asigna la base de datos
        when(notifications.findByUserId(eq(2L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(notification), PageRequest.of(1, 5), 6));

        PageResponse<NotificationResponse> page = service.list(2L, 1, 5);

        assertThat(page.content()).containsExactly(new NotificationResponse(31L, IBAN, "TRANSFERENCIA_EMITIDA",
                new BigDecimal("-50.00"), new BigDecimal("2450.00"), "TRF-8f2c", 154L,
                "Has enviado 50,00 € desde tu cuenta ···0011 (Alquiler). Saldo: 2.450,00 €", createdAt));
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(5);
        assertThat(page.totalElements()).isEqualTo(6);
        assertThat(page.totalPages()).isEqualTo(2);
    }

    @Test
    void returnsAnEmptyPageWhenTheUserHasNoNotifications() {
        when(notifications.findByUserId(eq(2L), any(Pageable.class))).thenReturn(Page.empty(PageRequest.of(0, 20)));

        PageResponse<NotificationResponse> page = service.list(2L, 0, 20);

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isZero();
    }

    private static MovementEvent event(List<Long> holderIds) {
        return new MovementEvent(1L, IBAN, holderIds, "INGRESO", new BigDecimal("10.00"),
                new BigDecimal("10.00"), null, null, Instant.now());
    }
}

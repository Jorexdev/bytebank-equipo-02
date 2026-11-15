package dev.bytebank.notifications.notification;

import dev.bytebank.notifications.event.MovementEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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

    private static MovementEvent event(List<Long> holderIds) {
        return new MovementEvent(1L, IBAN, holderIds, "INGRESO", new BigDecimal("10.00"),
                new BigDecimal("10.00"), null, null, Instant.now());
    }
}

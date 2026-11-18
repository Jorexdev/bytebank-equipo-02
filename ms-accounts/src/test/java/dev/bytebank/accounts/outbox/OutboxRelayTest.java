package dev.bytebank.accounts.outbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    static final String TOPIC = "bytebank.movements";
    static final int BATCH_SIZE = 100;
    static final long SEND_TIMEOUT_MS = 50;

    @Mock OutboxEventRepository outbox;
    @Mock KafkaTemplate<String, String> kafka;

    OutboxRelay relay;
    OutboxEvent first;
    OutboxEvent second;

    @BeforeEach
    void setUp() {
        relay = new OutboxRelay(outbox, kafka, BATCH_SIZE, SEND_TIMEOUT_MS);
        first = new OutboxEvent(TOPIC, "ES01", "{\"movementId\":1}");
        second = new OutboxEvent(TOPIC, "ES01", "{\"movementId\":2}");
    }

    @Test
    void publishesPendingEventsInOrderAndMarksThem() {
        when(outbox.findPendingForUpdate(BATCH_SIZE)).thenReturn(List.of(first, second));
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(ok());

        relay.publishPending();

        InOrder order = inOrder(kafka);
        order.verify(kafka).send(TOPIC, "ES01", "{\"movementId\":1}");
        order.verify(kafka).send(TOPIC, "ES01", "{\"movementId\":2}");
        assertThat(first.getPublishedAt()).isNotNull();
        assertThat(second.getPublishedAt()).isNotNull();
    }

    @Test
    void kafkaFailureKeepsTheEventPendingAndStopsTheRound() {
        when(outbox.findPendingForUpdate(BATCH_SIZE)).thenReturn(List.of(first, second));
        when(kafka.send(TOPIC, "ES01", "{\"movementId\":1}"))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker caído")));

        relay.publishPending();

        assertThat(first.getPublishedAt()).isNull();
        assertThat(first.getAttempts()).isEqualTo(1);
        assertThat(first.getLastError()).contains("broker caído");
        // el segundo no se intenta: si se publicara, adelantaría al primero
        verify(kafka, never()).send(TOPIC, "ES01", "{\"movementId\":2}");
        assertThat(second.getAttempts()).isZero();
    }

    @Test
    void kafkaThatDoesNotAnswerCountsAsAFailure() {
        when(outbox.findPendingForUpdate(BATCH_SIZE)).thenReturn(List.of(first));
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(new CompletableFuture<>());

        relay.publishPending();

        assertThat(first.getPublishedAt()).isNull();
        assertThat(first.getAttempts()).isEqualTo(1);
        assertThat(first.getLastError()).contains("TimeoutException");
    }

    @Test
    void failedEventIsPublishedInTheNextRound() {
        when(outbox.findPendingForUpdate(BATCH_SIZE)).thenReturn(List.of(first));
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker caído")))
                .thenReturn(ok());

        relay.publishPending();
        relay.publishPending();

        assertThat(first.getPublishedAt()).isNotNull();
        assertThat(first.getAttempts()).isEqualTo(1);
        assertThat(first.getLastError()).isNull();
    }

    @Test
    void nothingPendingSendsNothing() {
        when(outbox.findPendingForUpdate(BATCH_SIZE)).thenReturn(List.of());

        relay.publishPending();

        verifyNoInteractions(kafka);
    }

    private static CompletableFuture<SendResult<String, String>> ok() {
        return CompletableFuture.completedFuture(null);
    }
}

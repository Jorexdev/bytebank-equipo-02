package dev.bytebank.accounts.event;

import dev.bytebank.accounts.IntegrationTest;
import dev.bytebank.accounts.account.Account;
import dev.bytebank.accounts.account.AccountRepository;
import dev.bytebank.accounts.account.AccountStatus;
import dev.bytebank.accounts.ledger.LedgerDtos.MovementRequest;
import dev.bytebank.accounts.ledger.LedgerService;
import dev.bytebank.accounts.movement.Movement;
import dev.bytebank.accounts.movement.MovementType;
import dev.bytebank.common.error.ApiException;
import dev.bytebank.common.iban.Iban;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * KAF-02: un cliente nunca debe recibir el aviso de un movimiento que no existe.
 * Solo se publica en Kafka lo que se ha confirmado en la base de datos.
 */
class MovementEventPublicationTest extends IntegrationTest {

    static final Duration WAIT = Duration.ofSeconds(5);

    @Autowired LedgerService ledger;
    @Autowired AccountRepository accounts;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired ConsumerFactory<String, String> consumerFactory;
    @Autowired MovementPublisher publisher;


    @BeforeEach
    void setUp() {
        jdbc.update("delete from outbox_events");
        jdbc.update("delete from movements");
        jdbc.update("delete from accounts");
    }

    @Test
    void committedMovementIsPublished() {
        String iban = account(AccountStatus.ACTIVA);

        ledger.debit(request(iban, MovementType.TRANSFERENCIA_EMITIDA));

        assertThat(messagesFor(iban)).hasSize(1);
    }

    @Test
    void rolledBackMovementIsNotPublished() {
        String iban = account(AccountStatus.ACTIVA);

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            ledger.debit(request(iban, MovementType.TRANSFERENCIA_EMITIDA));
            throw new IllegalStateException("fallo después del cargo");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject("select count(*) from movements", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from outbox_events", Integer.class)).isZero();
        assertThat(messagesFor(iban)).isEmpty();
    }

    @Test
    void atomicTransferThatFailsPublishesNothing() {
        String origin = account(AccountStatus.ACTIVA);
        String blocked = account(AccountStatus.BLOQUEADA);

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            ledger.debit(request(origin, MovementType.TRANSFERENCIA_EMITIDA));
            ledger.credit(request(blocked, MovementType.TRANSFERENCIA_RECIBIDA));
        })).isInstanceOf(ApiException.class);

        assertThat(messagesFor(origin)).isEmpty();
    }

    @Test
    void committedMovementIsSavedInTheOutbox() {
        String iban = account(AccountStatus.ACTIVA);

        ledger.debit(request(iban, MovementType.TRANSFERENCIA_EMITIDA));

        var rows = jdbc.queryForList("select topic, message_key, payload from outbox_events");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("topic")).isEqualTo(MovementEvent.TOPIC);
        assertThat(rows.get(0).get("message_key")).isEqualTo(iban);
        assertThat((String) rows.get(0).get("payload")).contains("\"type\":\"TRANSFERENCIA_EMITIDA\"");
    }

    @Test
    void publishingOutsideATransactionIsRejected() {
        Account account = accounts.findByIban(account(AccountStatus.ACTIVA)).orElseThrow();
        Movement movement = new Movement(account, MovementType.INGRESO, new BigDecimal("10.00"), "Prueba", null);

        assertThatThrownBy(() -> publisher.publish(movement, account))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events", Integer.class)).isZero();
    }



    private String account(AccountStatus status) {
        Account account = new Account(Iban.generate(accounts.nextAccountNumber()));
        account.addHolder(1L);
        account.credit(new BigDecimal("100.00"));
        account.changeStatus(status);
        return accounts.save(account).getIban();
    }

    private static MovementRequest request(String iban, MovementType type) {
        return new MovementRequest("TRF-KAF02", iban, new BigDecimal("10.00"), type, null, null);
    }

    /** Lee el topic desde el principio durante WAIT y devuelve solo los mensajes de esa cuenta. */
    private List<ConsumerRecord<String, String>> messagesFor(String iban) {
        Properties props = new Properties();
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        List<ConsumerRecord<String, String>> found = new ArrayList<>();
        try (Consumer<String, String> consumer =
                     consumerFactory.createConsumer("kaf02-test-" + UUID.randomUUID(), null, null, props)) {
            consumer.subscribe(List.of(MovementEvent.TOPIC));
            long deadline = System.nanoTime() + WAIT.toNanos();
            while (System.nanoTime() < deadline) {
                consumer.poll(Duration.ofMillis(500)).forEach(r -> {
                    if (iban.equals(r.key())) {
                        found.add(r);
                    }
                });
            }
        }
        return found;
    }
}

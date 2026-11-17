package dev.bytebank.cards.payment;

import dev.bytebank.cards.card.Card;
import dev.bytebank.cards.card.CardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "bytebank.internal-key=clave-interna-de-test",
        "bytebank.jwt.secret=clave-secreta-de-test-que-debe-ser-muy-larga-para-pasar-la-validacion"
})
class CardPaymentRepositoryIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    static {
        POSTGRES.start();
    }

    private final CardRepository cards;
    private final CardPaymentRepository payments;
    private final TransactionTemplate transactions;

    @Autowired
    CardPaymentRepositoryIntegrationTest(CardRepository cards, CardPaymentRepository payments,
                                         PlatformTransactionManager transactionManager) {
        this.cards = cards;
        this.payments = payments;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @BeforeEach
    void clean() {
        payments.deleteAll();
        cards.deleteAll();
    }

    @Test
    void sumsOnlyApprovedPaymentsFromTheBoundary() {
        Card card = cards.save(new Card("ES7639990001000000000001", 2L, "4821", new BigDecimal("600.00")));
        Instant boundary = Instant.parse("2026-11-16T23:00:00Z");

        CardPayment approvedAtBoundary = payment("CARD-one", card, "100.00", PaymentStatus.APROBADO,
                boundary);
        CardPayment approvedBeforeBoundary = payment("CARD-two", card, "200.00", PaymentStatus.APROBADO,
                boundary.minusSeconds(1));
        CardPayment rejectedAfterBoundary = payment("CARD-three", card, "300.00", PaymentStatus.RECHAZADO,
                boundary.plusSeconds(1));
        payments.saveAll(java.util.List.of(approvedAtBoundary, approvedBeforeBoundary, rejectedAfterBoundary));

        assertThat(payments.sumApprovedAmountSince(card.getId(), boundary))
                .isEqualByComparingTo("100.00");
    }

    @Test
    void referenceIsPersistedAndUnique() {
        Card card = cards.save(new Card("ES7639990001000000000002", 2L, "4822", new BigDecimal("600.00")));
        CardPayment payment = payment("CARD-persisted", card, "10.00", PaymentStatus.APROBADO, Instant.now());

        CardPayment persisted = payments.saveAndFlush(payment);

        assertThat(persisted.getReference()).isEqualTo("CARD-persisted");
        assertThat(payments.findAll()).extracting(CardPayment::getReference).containsExactly("CARD-persisted");
    }

    @Test
    void lockedCardLookupSerializesConcurrentTransactions() throws Exception {
        Card card = cards.saveAndFlush(new Card("ES7639990001000000000003", 2L, "4823",
                new BigDecimal("600.00")));
        CountDownLatch firstHasLock = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Future<?> first = executor.submit(() -> transactions.execute(status -> {
            cards.findByIdForUpdate(card.getId());
            firstHasLock.countDown();
            await(releaseFirst);
            return null;
        }));
        Future<?> second = null;
        try {
            assertThat(firstHasLock.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> startedSecond = executor.submit(() -> transactions.execute(status -> {
                cards.findByIdForUpdate(card.getId());
                return null;
            }));
            second = startedSecond;
            assertThatThrownBy(() -> startedSecond.get(200, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
        } finally {
            releaseFirst.countDown();
            first.get(5, TimeUnit.SECONDS);
            if (second != null) {
                second.get(5, TimeUnit.SECONDS);
            }
            executor.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("La prueba fue interrumpida", e);
        }
    }

    private static CardPayment payment(String reference, Card card, String amount, PaymentStatus status,
                                       Instant createdAt) {
        CardPayment payment = new CardPayment(reference, card.getId(), new BigDecimal(amount), "Comercio", createdAt);
        if (status == PaymentStatus.APROBADO) {
            payment.approve();
        } else {
            payment.reject("FONDOS_INSUFICIENTES");
        }
        return payment;
    }
}

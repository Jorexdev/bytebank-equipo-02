package dev.bytebank.accounts.ledger;

import dev.bytebank.accounts.IntegrationTest;
import dev.bytebank.accounts.account.Account;
import dev.bytebank.accounts.account.AccountRepository;
import dev.bytebank.accounts.account.AccountStatus;
import dev.bytebank.accounts.ledger.LedgerDtos.MovementRequest;
import dev.bytebank.accounts.movement.MovementType;
import dev.bytebank.common.error.ApiException;
import dev.bytebank.common.iban.Iban;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerServiceIntegrationTest extends IntegrationTest {

    @Autowired
    LedgerService ledger;
    @Autowired
    AccountRepository accounts;
    @Autowired
    JdbcTemplate jdbc;

    String iban;

    @BeforeEach
    void setUp() {
        jdbc.update("delete from movements");
        jdbc.update("delete from accounts");
        Account account = new Account(Iban.generate(accounts.nextAccountNumber()), 1L);
        account.credit(new BigDecimal("100.00"));
        iban = accounts.save(account).getIban();
    }

    @Test
    void debitReducesBalance() {
        ledger.debit(new MovementRequest("TRF-1", iban, new BigDecimal("30.00"), MovementType.TRANSFERENCIA_EMITIDA, null));

        assertThat(accounts.findByIban(iban).orElseThrow().getBalance()).isEqualByComparingTo("70");
    }

    @Test
    void creditIncreasesBalance() {
        ledger.credit(new MovementRequest("TRF-2", iban, new BigDecimal("50.00"), MovementType.TRANSFERENCIA_RECIBIDA, null));

        assertThat(accounts.findByIban(iban).orElseThrow().getBalance()).isEqualByComparingTo("150");
    }

    @Test
    void cannotDebitMoreThanBalance() {
        assertThatThrownBy(() -> ledger.debit(new MovementRequest("TRF-3", iban, new BigDecimal("101.00"), MovementType.TRANSFERENCIA_EMITIDA, null)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void cannotCreditBlockedAccount() {
        Account account = accounts.findByIban(iban).orElseThrow();
        account.changeStatus(AccountStatus.BLOQUEADA);
        accounts.save(account);

        assertThatThrownBy(() -> ledger.credit(new MovementRequest("TRF-4", iban, new BigDecimal("10.00"), MovementType.TRANSFERENCIA_RECIBIDA, null)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void concurrentDebitsDoNotOverspend() throws InterruptedException {
        int concurrent = 4;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(concurrent);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();

        ExecutorService executor = Executors.newFixedThreadPool(concurrent);
        for (int i = 0; i < concurrent; i++) {
            executor.submit(() -> {
                try {
                    start.await();
                    ledger.debit(new MovementRequest("TRF-CONC", iban, new BigDecimal("30.00"),
                            MovementType.TRANSFERENCIA_EMITIDA, null));
                    successes.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ApiException e) {
                    failures.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        assertThat(successes.get()).isEqualTo(3);
        assertThat(failures.get()).isEqualTo(1);
        assertThat(accounts.findByIban(iban).orElseThrow().getBalance()).isEqualByComparingTo("10");
    }
}

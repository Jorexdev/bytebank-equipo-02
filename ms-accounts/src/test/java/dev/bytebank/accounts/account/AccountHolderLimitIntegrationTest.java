package dev.bytebank.accounts.account;

import dev.bytebank.accounts.IntegrationTest;
import dev.bytebank.accounts.client.AuthClient;
import dev.bytebank.accounts.client.AuthClient.UserInfo;
import dev.bytebank.common.error.ApiException;
import dev.bytebank.common.security.AuthUser;
import dev.bytebank.common.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class AccountHolderLimitIntegrationTest extends IntegrationTest {

    static final AuthUser ANA = new AuthUser(2L, "ana@bytebank.test", Role.CLIENTE);

    @Autowired
    AccountService service;

    @Autowired
    AccountRepository accounts;

    @Autowired
    JdbcTemplate jdbc;

    @MockBean
    AuthClient auth;

    @BeforeEach
    void clean() {
        jdbc.update("delete from movements");
        jdbc.update("delete from accounts");
    }

    @Test
    void concurrentAddsNeverExceedFourHolders() throws InterruptedException {
        Account account = service.open(ANA);
        Map<String, UserInfo> extras = Map.of(
                "u10@test.com", new UserInfo(10L, "u10@test.com", "U10", Role.CLIENTE),
                "u11@test.com", new UserInfo(11L, "u11@test.com", "U11", Role.CLIENTE),
                "u12@test.com", new UserInfo(12L, "u12@test.com", "U12", Role.CLIENTE),
                "u13@test.com", new UserInfo(13L, "u13@test.com", "U13", Role.CLIENTE));
        when(auth.findByEmail(anyString())).thenAnswer(invocation -> extras.get(invocation.getArgument(0)));

        int concurrent = extras.size();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(concurrent);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger limitRejections = new AtomicInteger();
        AtomicReference<Throwable> unexpected = new AtomicReference<>();

        ExecutorService executor = Executors.newFixedThreadPool(concurrent);
        for (String email : extras.keySet()) {
            executor.submit(() -> {
                try {
                    start.await();
                    service.addHolder(account.getIban(), email, ANA);
                    successes.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ApiException e) {
                    if ("MAXIMO_TITULARES_ALCANZADO".equals(e.getCode())) {
                        limitRejections.incrementAndGet();
                    } else {
                        unexpected.set(e);
                    }
                } catch (RuntimeException e) {
                    unexpected.set(e);
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        assertThat(unexpected.get()).isNull();
        assertThat(successes.get()).isEqualTo(3);
        assertThat(limitRejections.get()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from account_holders where account_id = ?", Integer.class, account.getId()))
                .isEqualTo(4);
        assertThat(accounts.findByIban(account.getIban()).orElseThrow().getHolderIds())
                .hasSize(4)
                .contains(2L);
    }
}

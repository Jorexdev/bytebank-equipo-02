package dev.bytebank.accounts.demo;

import dev.bytebank.accounts.client.AuthClient;
import dev.bytebank.accounts.client.AuthClient.DemoHolders;
import dev.bytebank.common.error.ApiException;
import dev.bytebank.common.http.RemoteUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** SEG-03: las cuentas de demo solo se crean si ms-auth confirma que no hay usuarios reales. */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class DemoSeederTest {

    private static final DemoHolders HOLDERS = new DemoHolders(41L, 42L); // ya no son 2 y 3
    private static final Duration TIMEOUT = Duration.ofMinutes(3);

    @Mock
    AuthClient auth;

    @Mock
    DemoAccounts demoAccounts;

    private final FakeTime time = new FakeTime();
    private DemoSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new DemoSeeder(auth, demoAccounts, TIMEOUT, time, time::sleep);
    }

    @Test
    void createsTheAccountsForTheHoldersThatAuthGives() {
        when(auth.demoHolders()).thenReturn(HOLDERS);

        assertThat(seeder.seedWhenAuthIsReady()).isTrue();

        verify(demoAccounts).create(HOLDERS);
        assertThat(time.sleeps).isEmpty();
    }

    @Test
    void doesNotAskAuthWhenThereAreAccountsAlready() {
        when(demoAccounts.exist()).thenReturn(true);

        seeder.run(null);

        verifyNoInteractions(auth);
    }

    @Test
    void givesUpAtOnceWhenAuthHasRealUsers(CapturedOutput output) {
        when(auth.demoHolders())
                .thenThrow(ApiException.conflict("BASE_NO_DEMO", "Hay usuarios que no son de demostración"));

        assertThat(seeder.seedWhenAuthIsReady()).isFalse();

        verify(auth, times(1)).demoHolders();
        verify(demoAccounts, never()).create(any());
        assertThat(time.sleeps).isEmpty();
        assertThat(output).contains("BASE_NO_DEMO");
    }

    @Test
    void givesUpAtOnceWhenAuthIsNotInDemo(CapturedOutput output) {
        when(auth.demoHolders()).thenThrow(ApiException.notFound("RUTA_NO_ENCONTRADA", "No existe esa ruta"));

        assertThat(seeder.seedWhenAuthIsReady()).isFalse();

        verify(auth, times(1)).demoHolders();
        verify(demoAccounts, never()).create(any());
        assertThat(output).contains("RUTA_NO_ENCONTRADA");
    }

    @Test
    void retriesWhileAuthIsNotReady() {
        when(auth.demoHolders())
                .thenThrow(new RemoteUnavailableException("No se pudo contactar con el servicio remoto"))
                .thenThrow(new RemoteUnavailableException("El servicio remoto respondió con 503"))
                .thenReturn(HOLDERS);

        assertThat(seeder.seedWhenAuthIsReady()).isTrue();

        verify(demoAccounts).create(HOLDERS);
        assertThat(time.sleeps).containsExactly(Duration.ofSeconds(2), Duration.ofSeconds(4));
    }

    @Test
    void stopsWhenTheTimeoutRunsOutAndSaysSo(CapturedOutput output) {
        when(auth.demoHolders()).thenThrow(new RemoteUnavailableException("No se pudo contactar con el servicio remoto"));

        assertThat(seeder.seedWhenAuthIsReady()).isFalse();

        verify(demoAccounts, never()).create(any());
        assertThat(time.totalSlept()).isGreaterThan(Duration.ofMinutes(2)).isLessThanOrEqualTo(TIMEOUT);
        assertThat(time.sleeps).startsWith(Duration.ofSeconds(2), Duration.ofSeconds(4), Duration.ofSeconds(8))
                .allSatisfy(wait -> assertThat(wait).isLessThanOrEqualTo(Duration.ofSeconds(15)));
        assertThat(output).contains("sin cuentas");
    }

    @Test
    @Timeout(5) // si run() esperase a ms-auth, el test se quedaría colgado aquí
    void doesNotBlockTheStartupWhileWaitingForAuth() throws Exception {
        CountDownLatch authAnswers = new CountDownLatch(1);
        when(auth.demoHolders()).thenAnswer(call -> {
            authAnswers.await();
            return HOLDERS;
        });

        seeder.run(null);

        verify(demoAccounts, never()).create(any());
        authAnswers.countDown();
        verify(demoAccounts, timeout(2000)).create(HOLDERS);
    }

    /** Reloj que solo avanza cuando el seeder "duerme": así se prueban 3 minutos sin esperarlos. */
    private static final class FakeTime extends Clock {

        private Instant now = Instant.parse("2026-11-19T10:00:00Z");
        private final List<Duration> sleeps = new ArrayList<>();

        void sleep(Duration duration) {
            sleeps.add(duration);
            now = now.plus(duration);
        }

        Duration totalSlept() {
            return sleeps.stream().reduce(Duration.ZERO, Duration::plus);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}

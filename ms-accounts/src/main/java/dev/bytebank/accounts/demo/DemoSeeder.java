package dev.bytebank.accounts.demo;

import dev.bytebank.accounts.client.AuthClient;
import dev.bytebank.accounts.client.AuthClient.DemoHolders;
import dev.bytebank.common.error.ApiException;
import dev.bytebank.common.http.RemoteUnavailableException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Cuentas de demostración (perfil demo). Pide a ms-auth los ids de Ana y Luis y solo siembra
 * si ms-auth confirma que no hay usuarios reales (SEG-03).
 */
@Component
@Profile("demo")
public class DemoSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);
    private static final Duration FIRST_WAIT = Duration.ofSeconds(2);
    private static final Duration MAX_WAIT = Duration.ofSeconds(15);


    /** Espera entre intentos. En los tests se sustituye para no esperar de verdad. */
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private final AuthClient auth;
    private final DemoAccounts demoAccounts;
    private final Duration timeout;
    private final Clock clock;
    private final Sleeper sleeper;

    @Autowired
    public DemoSeeder(AuthClient auth, DemoAccounts demoAccounts,
                      @Value("${bytebank.demo.seed-timeout}") Duration timeout) {
        this(auth, demoAccounts, timeout, Clock.systemUTC(), duration -> Thread.sleep(duration.toMillis()));
    }

    DemoSeeder(AuthClient auth, DemoAccounts demoAccounts, Duration timeout, Clock clock, Sleeper sleeper) {
        this.auth = auth;
        this.demoAccounts = demoAccounts;
        this.timeout = timeout;
        this.clock = clock;
        this.sleeper = sleeper;
    }

        /**
     * No bloquea el arranque: ms-auth puede tardar más de un minuto en estar listo, y mientras tanto
     * ms-accounts debe arrancar y responder. Las cuentas se crean en segundo plano en cuanto ms-auth contesta.
     */
    @Override
    public void run(ApplicationArguments args) {
        if (demoAccounts.exist()) {
            return;
        }
        Thread thread = new Thread(this::seedWhenAuthIsReady, "demo-seeder");
        thread.setDaemon(true);
        thread.start();
    }

    /** Pregunta a ms-auth hasta que responda o se agote el plazo. Devuelve true si ha creado las cuentas. */
    boolean seedWhenAuthIsReady() {
        Instant deadline = clock.instant().plus(timeout);
        Duration wait = FIRST_WAIT;
        while (true) {
            try {
                DemoHolders holders = auth.demoHolders();
                demoAccounts.create(holders);
                log.info("Cuentas de demostración creadas para los usuarios {} (Ana) y {} (Luis)",
                        holders.anaId(), holders.luisId());
                return true;
            } catch (RemoteUnavailableException e) {
                if (clock.instant().plus(wait).isAfter(deadline)) {
                    log.error("La demo ha arrancado sin cuentas: ms-auth no ha tenido la demo lista en {} s ({}). "
                            + "Reinicia ms-accounts cuando ms-auth esté en marcha.", timeout.toSeconds(), e.getMessage());
                    return false;
                }
                log.warn("ms-auth todavía no tiene lista la demo ({}). Nuevo intento en {} s",
                        e.getMessage(), wait.toSeconds());
                if (!pause(wait)) {
                    return false;
                }
                Duration doubled = wait.multipliedBy(2);
                wait = doubled.compareTo(MAX_WAIT) < 0 ? doubled : MAX_WAIT;
            } catch (ApiException e) {
                log.error("No se crean las cuentas de demostración: ms-auth ha respondido {} {} ({})",
                        e.getStatus().value(), e.getCode(), e.getMessage());
                return false;
            } catch (RuntimeException e) {
                log.error("No se han podido crear las cuentas de demostración", e);
                return false;
            }
        }
    }

    private boolean pause(Duration wait) {
        try {
            sleeper.sleep(wait);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Espera interrumpida: no se crean las cuentas de demostración");
            return false;
        }
    }

}

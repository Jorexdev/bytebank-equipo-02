package dev.bytebank.accounts.demo;

import dev.bytebank.accounts.account.Account;
import dev.bytebank.accounts.account.AccountRepository;
import dev.bytebank.accounts.movement.Movement;
import dev.bytebank.accounts.movement.MovementRepository;
import dev.bytebank.accounts.movement.MovementType;
import dev.bytebank.common.iban.Iban;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Cuentas de demostración (perfil demo). Los titulares son los usuarios del seeder de ms-auth:
 * 2 = ana@bytebank.test y 3 = luis@bytebank.test.
 */
@Component
@Profile("demo")
public class DemoSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);
    private static final long ANA = 2L;
    private static final long LUIS = 3L;

    private final AccountRepository accounts;
    private final MovementRepository movements;

    public DemoSeeder(AccountRepository accounts, MovementRepository movements) {
        this.accounts = accounts;
        this.movements = movements;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (accounts.count() > 0) {
            return;
        }
        open(ANA, new BigDecimal("2500.00"));
        open(ANA, new BigDecimal("150.75"));
        open(LUIS, new BigDecimal("800.00"));
        log.info("Cuentas de demostración creadas");
    }

    private void open(long ownerId, BigDecimal initialBalance) {
        Account account = accounts.save(new Account(Iban.generate(accounts.nextAccountNumber()), ownerId));
        account.credit(initialBalance);
        movements.save(new Movement(account, MovementType.INGRESO, initialBalance, "Saldo inicial", null));
    }
}

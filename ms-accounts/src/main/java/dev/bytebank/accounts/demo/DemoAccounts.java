package dev.bytebank.accounts.demo;

import dev.bytebank.accounts.account.Account;
import dev.bytebank.accounts.account.AccountRepository;
import dev.bytebank.accounts.client.AuthClient.DemoHolders;
import dev.bytebank.accounts.movement.Movement;
import dev.bytebank.accounts.movement.MovementRepository;
import dev.bytebank.accounts.movement.MovementType;
import dev.bytebank.common.iban.Iban;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Crea las cuentas de demostración. Está separada de DemoSeeder para que la transacción funcione:
 * DemoSeeder la llama desde otro hilo, y @Transactional solo actúa si la llamada pasa por el proxy de Spring.
 */
@Component
@Profile("demo")
public class DemoAccounts {

    private final AccountRepository accounts;
    private final MovementRepository movements;

    public DemoAccounts(AccountRepository accounts, MovementRepository movements) {
        this.accounts = accounts;
        this.movements = movements;
    }

    @Transactional(readOnly = true)
    public boolean exist() {
        return accounts.count() > 0;
    }

    /** Las tres cuentas se crean juntas o ninguna. */
    @Transactional
    public void create(DemoHolders holders) {
        if (accounts.count() > 0) {
            return; // por si se han creado mientras se esperaba a ms-auth
        }
        open(holders.anaId(), new BigDecimal("2500.00"));
        open(holders.anaId(), new BigDecimal("150.75"));
        open(holders.luisId(), new BigDecimal("800.00"));
    }

    private void open(long ownerId, BigDecimal initialBalance) {
        Account account = new Account(Iban.generate(accounts.nextAccountNumber()));
        account.addHolder(ownerId);
        account = accounts.save(account);
        account.credit(initialBalance);
        movements.save(new Movement(account, MovementType.INGRESO, initialBalance, "Saldo inicial", null));
    }
}

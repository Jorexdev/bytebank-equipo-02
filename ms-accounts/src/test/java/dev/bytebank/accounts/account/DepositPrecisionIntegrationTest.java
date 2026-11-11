package dev.bytebank.accounts.account;

import dev.bytebank.accounts.IntegrationTest;
import dev.bytebank.common.iban.Iban;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reproduce la incidencia de contabilidad: dos ingresos de 0,10 € y 0,20 €
 * dejan un saldo de 0,30000000000000004 € porque el saldo se guarda como double
 * (coma flotante binaria). El test fija el valor observado hoy.
 *
 * Cuando el saldo se almacene con precisión decimal (BigDecimal / NUMERIC),
 * el valor esperado es 0,30 y este test debe actualizarse.
 */
class DepositPrecisionIntegrationTest extends IntegrationTest {

    @Autowired
    AccountService accountService;
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
        iban = accounts.save(account).getIban();
    }

    @Test
    void dosIngresosDe010Y020DejanSaldoConErrorDeComa() {
        accountService.deposit(iban, 0.10, "Ingreso 1");
        accountService.deposit(iban, 0.20, "Ingreso 2");

        double saldo = accounts.findByIban(iban).orElseThrow().getBalance();

        // Valor observado hoy. Lo esperado por contabilidad es 0.30.
        assertThat(saldo).isEqualTo(0.30000000000000004);
    }
}

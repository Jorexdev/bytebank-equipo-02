package dev.bytebank.accounts.account;

import dev.bytebank.accounts.IntegrationTest;
import dev.bytebank.common.iban.Iban;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Incidencia de contabilidad: dos ingresos de 0,10 € y 0,20 € tenían que dar 0,30 €,
 * pero con saldos en double daban 0,30000000000000004 €.
 *
 * Los saldos e importes son BigDecimal con escala 2 (NUMERIC(19,2)). Este test comprueba
 * el saldo final, cada saldo intermedio y que no aparece el error de coma flotante.
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
    void dosIngresosDe010Y020DanUnSaldoDe030Exacto() {
        accountService.deposit(iban, new BigDecimal("0.10"), "Ingreso 1");
        accountService.deposit(iban, new BigDecimal("0.20"), "Ingreso 2");

        BigDecimal saldo = accounts.findByIban(iban).orElseThrow().getBalance();

        assertThat(saldo.toPlainString()).isEqualTo("0.30");
        assertThat(saldo).isNotEqualByComparingTo("0.30000000000000004");
    }

    @Test
    void saldoTrasCadaIngresoEsExactoEnElExtracto() {
        accountService.deposit(iban, new BigDecimal("0.10"), "Ingreso 1");
        accountService.deposit(iban, new BigDecimal("0.20"), "Ingreso 2");

        var saldosTrasMovimiento = jdbc.queryForList(
                "select balance_after from movements order by id", BigDecimal.class);

        assertThat(saldosTrasMovimiento).containsExactly(new BigDecimal("0.10"), new BigDecimal("0.30"));
    }
}

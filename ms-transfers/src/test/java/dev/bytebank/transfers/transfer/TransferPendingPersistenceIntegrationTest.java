package dev.bytebank.transfers.transfer;

import dev.bytebank.common.iban.Iban;
import dev.bytebank.transfers.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAM-02: cómo se guarda y se consulta una transferencia pendiente de aprobación en PostgreSQL.
 * Cubre lo que el compilador no ve: el valor que llega a la columna y la consulta JPQL de
 * {@link TransferRepository#updateStatusIfPending}, que nombra el estado dentro de un texto.
 */
class TransferPendingPersistenceIntegrationTest extends IntegrationTest {

    @Autowired
    TransferRepository transfers;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        transfers.deleteAll();
    }

    @Test
    void pendingTransferIsStoredAsPendienteRevision() {
        Transfer transfer = savedPendingTransfer();

        assertThat(storedStatus(transfer)).isEqualTo("PENDIENTE_REVISION");
    }

    @Test
    void pendingListFindsThePendingTransfer() {
        Transfer transfer = savedPendingTransfer();

        var page = transfers.findByStatus(transfer.getStatus(), PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(Transfer::getId).containsExactly(transfer.getId());
    }

    @Test
    void onlyTheFirstDecisionClaimsAPendingTransfer() {
        Transfer transfer = savedPendingTransfer();

        assertThat(transfers.updateStatusIfPending(transfer.getId(), TransferStatus.COMPLETADA)).isEqualTo(1);
        assertThat(transfers.updateStatusIfPending(transfer.getId(), TransferStatus.RECHAZADA)).isZero();
        assertThat(storedStatus(transfer)).isEqualTo("COMPLETADA");
    }

    private Transfer savedPendingTransfer() {
        Transfer transfer = new Transfer(Iban.generate(1), Iban.generate(2), new BigDecimal("6000.00"), "Alquiler", 2L);
        transfer.pending();
        return transfers.save(transfer);
    }

    private String storedStatus(Transfer transfer) {
        return jdbc.queryForObject("select status from transfers where id = ?", String.class, transfer.getId());
    }
}

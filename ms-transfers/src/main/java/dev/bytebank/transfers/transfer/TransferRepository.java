package dev.bytebank.transfers.transfer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface TransferRepository extends JpaRepository<Transfer, Long> {

    Page<Transfer> findByCreatedBy(Long createdBy, Pageable pageable);

    /**
     * Updates the status only if the transfer is still pending.
     * Returns 0 when another approval or rejection has already claimed it.
     */
    @Modifying
    @Transactional
    @Query("update Transfer t set t.status = :newStatus " +
            "where t.id = :id and t.status = dev.bytebank.transfers.transfer.TransferStatus.PENDIENTE")
    int updateStatusIfPending(@Param("id") Long id, @Param("newStatus") TransferStatus newStatus);
}

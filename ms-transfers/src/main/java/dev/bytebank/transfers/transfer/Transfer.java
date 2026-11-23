package dev.bytebank.transfers.transfer;

import dev.bytebank.common.iban.Iban;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Entity
@Table(name = "transfers")
public class Transfer {

    private static final BigDecimal FEE_RATE = new BigDecimal("0.005");
    private static final BigDecimal MIN_FEE = new BigDecimal("1.00");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "origin_iban", nullable = false, length = 34)
    private String originIban;

    @Column(name = "destination_iban", nullable = false, length = 34)
    private String destinationIban;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(length = 140)
    private String concept;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransferStatus status;

    @Column(name = "rejection_reason", length = 255)
    private String rejectionReason;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Transfer() {
    }

    public Transfer(String originIban, String destinationIban, BigDecimal amount, String concept, Long createdBy) {
        this.originIban = originIban;
        this.destinationIban = destinationIban;
        this.amount = amount;
        this.concept = concept;
        this.createdBy = createdBy;
        this.status = TransferStatus.COMPLETADA;
        this.createdAt = Instant.now();
    }

    public String ledgerReference() {
        return "TRF-" + id;
    }

    /**
     * Fee for this transfer: 0.5% of the amount, minimum 1.00 €, rounded half-up to cents.
     * Zero when the destination is another ByteBank account.
     *
     * <p>Recomputed with the current rate on every read, so a rate change would alter the fee
     * shown for past transfers and the fee charged for a pending one.
     */
    public BigDecimal fee() {
        if (Iban.isOwnBank(destinationIban)) {
            return BigDecimal.ZERO.setScale(2);
        }
        return amount.multiply(FEE_RATE).setScale(2, RoundingMode.HALF_UP).max(MIN_FEE);
    }

    public void complete() {
        this.status = TransferStatus.COMPLETADA;
        this.rejectionReason = null;
    }

    public void pending() {
        this.status = TransferStatus.PENDIENTE_REVISION;
        this.rejectionReason = null;
    }

    public void reject(String reason) {
        this.status = TransferStatus.RECHAZADA;
        this.rejectionReason = reason;
    }

    public Long getId() {
        return id;
    }

    public String getOriginIban() {
        return originIban;
    }

    public String getDestinationIban() {
        return destinationIban;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getConcept() {
        return concept;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

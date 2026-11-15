package dev.bytebank.transfers.transfer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "transfers")
public class Transfer {

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

    @Column(name = "rejection_reason", length = 40)
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

    public void complete() {
        this.status = TransferStatus.COMPLETADA;
        this.rejectionReason = null;
    }

    public void pending() {
        this.status = TransferStatus.PENDIENTE;
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

package dev.bytebank.cards.payment;

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

/** Intento de pago con tarjeta. Los rechazados también se guardan, para que el cliente los vea. */
@Entity
@Table(name = "card_payments")
public class CardPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "card_id", nullable = false)
    private Long cardId;

    @Column(nullable = false, unique = true, length = 64)
    private String reference;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 100)
    private String merchant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(name = "rejection_reason", length = 40)
    private String rejectionReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CardPayment() {
    }

    public CardPayment(String reference, Long cardId, BigDecimal amount, String merchant, Instant createdAt) {
        this.reference = reference;
        this.cardId = cardId;
        this.amount = amount;
        this.merchant = merchant;
        this.createdAt = createdAt;
    }

    public String ledgerReference() {
        return reference != null ? reference : "CARD-" + id;
    }

    public void approve() {
        this.status = PaymentStatus.APROBADO;
        this.rejectionReason = null;
    }

    public void reject(String reason) {
        this.status = PaymentStatus.RECHAZADO;
        this.rejectionReason = reason;
    }

    public Long getId() {
        return id;
    }

    public Long getCardId() {
        return cardId;
    }

    public String getReference() {
        return reference;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getMerchant() {
        return merchant;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

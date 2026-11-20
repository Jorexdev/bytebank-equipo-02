package dev.bytebank.cards.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface CardPaymentRepository extends JpaRepository<CardPayment, Long> {

    /** Total gastado con la tarjeta desde {@code since}. Sirve para comprobar el límite diario. */
    @Query("""
            select coalesce(sum(p.amount), 0) from CardPayment p
            where p.cardId = :cardId and p.status = dev.bytebank.cards.payment.PaymentStatus.APROBADO
              and p.createdAt >= :since
            """)
    BigDecimal sumApprovedAmountSince(@Param("cardId") Long cardId, @Param("since") Instant since);

    List<CardPayment> findByCardIdOrderByCreatedAtDescIdDesc(Long cardId);
}

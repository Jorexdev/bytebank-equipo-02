package dev.bytebank.accounts.movement;

import java.math.BigDecimal;
import java.time.Instant;

public record MovementResponse(Long id, MovementType type, BigDecimal amount, BigDecimal balanceAfter,
                               String concept, String reference, Instant createdAt) {

    public static MovementResponse from(Movement movement) {
        return new MovementResponse(movement.getId(), movement.getType(), movement.getAmount(),
                movement.getBalanceAfter(), movement.getConcept(), movement.getReference(), movement.getCreatedAt());
    }
}

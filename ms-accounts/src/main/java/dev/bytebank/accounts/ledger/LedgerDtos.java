package dev.bytebank.accounts.ledger;

import dev.bytebank.accounts.movement.MovementType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public final class LedgerDtos {

    private LedgerDtos() {
    }

    /**
     * Cargo o abono en una cuenta. {@code reference} identifica la operación que lo origina (por ejemplo TRF-42).
     * En un cargo, {@code fee} se descuenta aparte como comisión; si falta, vale cero.
     */
    public record MovementRequest(
            @NotBlank @Size(max = 64) String reference,
            @NotBlank String iban,
            @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amount,
            @NotNull MovementType type,
            @Size(max = 140) String concept,
            @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal fee) {

        public MovementRequest {
            fee = fee == null ? BigDecimal.ZERO : fee;
        }
    }

    public record LedgerResult(String reference, BigDecimal balanceAfter) {
    }
}

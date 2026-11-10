package dev.bytebank.accounts.ledger;

import dev.bytebank.accounts.movement.MovementType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class LedgerDtos {

    private LedgerDtos() {
    }

    /** Cargo o abono en una cuenta. {@code reference} identifica la operación que lo origina (por ejemplo TRF-42). */
    public record MovementRequest(
            @NotBlank @Size(max = 64) String reference,
            @NotBlank String iban,
            @Positive double amount,
            @NotNull MovementType type,
            @Size(max = 140) String concept) {
    }

    public record LedgerResult(String reference, double balanceAfter) {
    }
}

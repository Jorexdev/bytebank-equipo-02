package dev.bytebank.transfers.transfer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public final class TransferDtos {

    private TransferDtos() {
    }

    public record CreateTransferRequest(
            @NotBlank String originIban,
            @NotBlank String destinationIban,
            @NotNull BigDecimal amount,
            @Size(max = 140) String concept) {
    }

    public record TransferResponse(Long id, String originIban, String destinationIban, BigDecimal amount,
                                   BigDecimal fee, String concept, TransferStatus status, String rejectionReason,
                                   Instant createdAt) {

        public static TransferResponse from(Transfer t) {
            BigDecimal fee = t.getStatus() == TransferStatus.RECHAZADA
                    ? BigDecimal.ZERO.setScale(2)
                    : t.fee();
            return new TransferResponse(t.getId(), t.getOriginIban(), t.getDestinationIban(), t.getAmount(),
                    fee, t.getConcept(), t.getStatus(), t.getRejectionReason(), t.getCreatedAt());
        }
    }
}

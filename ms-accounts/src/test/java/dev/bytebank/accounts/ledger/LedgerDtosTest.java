package dev.bytebank.accounts.ledger;

import dev.bytebank.accounts.ledger.LedgerDtos.MovementRequest;
import dev.bytebank.accounts.movement.MovementType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerDtosTest {

    static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsNonPositiveAmount() {
        assertThat(VALIDATOR.validate(request(-100))).isNotEmpty();
        assertThat(VALIDATOR.validate(request(0))).isNotEmpty();
        assertThat(VALIDATOR.validate(request(100))).isEmpty();
    }

    private static MovementRequest request(double amount) {
        return new MovementRequest("TRF-1", "ES6839990001000000000001", amount,
                MovementType.TRANSFERENCIA_EMITIDA, null);
    }
}

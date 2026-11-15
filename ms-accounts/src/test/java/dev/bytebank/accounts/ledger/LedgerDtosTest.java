package dev.bytebank.accounts.ledger;

import dev.bytebank.accounts.ledger.LedgerDtos.MovementRequest;
import dev.bytebank.accounts.movement.MovementType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerDtosTest {

    static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsNonPositiveAmount() {
        assertThat(VALIDATOR.validate(request("-100"))).isNotEmpty();
        assertThat(VALIDATOR.validate(request("0"))).isNotEmpty();
        assertThat(VALIDATOR.validate(request("100"))).isEmpty();
    }

    @Test
    void rejectsMoreThanTwoDecimals() {
        assertThat(VALIDATOR.validate(request("10.001"))).isNotEmpty();
        assertThat(VALIDATOR.validate(request("10.10"))).isEmpty();
    }

    private static MovementRequest request(String amount) {
        return new MovementRequest("TRF-1", "ES6839990001000000000001", new BigDecimal(amount),
                MovementType.TRANSFERENCIA_EMITIDA, null);
    }
}

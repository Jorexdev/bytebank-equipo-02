package dev.bytebank.common;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Sin un secreto JWT válido o sin clave interna, la configuración no se acepta y el servicio no arranca. */
class BytebankPropertiesTest {

    private static final String SECRET = "secreto-jwt-solo-para-tests-0123456789abcdef";

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll
    static void closeFactory() {
        FACTORY.close();
    }

    @Test
    void acceptsSecretOfAtLeast32Characters() {
        assertThat(validate(new BytebankProperties.Jwt(SECRET, null), "clave-interna")).isEmpty();
    }

    @Test
    void rejectsSecretShorterThan32Characters() {
        assertThat(validate(new BytebankProperties.Jwt("corto", null), "clave-interna"))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("el secreto JWT debe tener al menos 32 caracteres");
    }

    @Test
    void rejectsBlankSecret() {
        assertThat(validate(new BytebankProperties.Jwt(" ".repeat(40), null), "clave-interna")).isNotEmpty();
    }

    @Test
    void rejectsMissingJwtConfiguration() {
        assertThat(validate(null, "clave-interna")).isNotEmpty();
    }

    @Test
    void rejectsBlankInternalKey() {
        assertThat(validate(new BytebankProperties.Jwt(SECRET, null), " ")).isNotEmpty();
    }

    @Test
    void tokensExpireAfter60MinutesByDefault() {
        assertThat(new BytebankProperties.Jwt(SECRET, null).expirationMinutesOrDefault()).isEqualTo(60);
        assertThat(new BytebankProperties.Jwt(SECRET, 15L).expirationMinutesOrDefault()).isEqualTo(15);
    }

    @Test
    void rejectsZeroExpirationMinutes() {
        assertThat(validate(new BytebankProperties.Jwt(SECRET, 0L), "clave-interna"))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("la duración del JWT debe ser mayor que cero");
    }

    @Test
    void rejectsNegativeExpirationMinutes() {
        assertThat(validate(new BytebankProperties.Jwt(SECRET, -1L), "clave-interna"))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("la duración del JWT debe ser mayor que cero");
    }

    private static Set<ConstraintViolation<BytebankProperties>> validate(BytebankProperties.Jwt jwt, String internalKey) {
        return VALIDATOR.validate(new BytebankProperties(jwt, internalKey, null));
    }
}

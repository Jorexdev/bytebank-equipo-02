package dev.bytebank.auth.auth;

import dev.bytebank.auth.user.User;
import dev.bytebank.common.BytebankProperties;
import dev.bytebank.common.security.AuthUser;
import dev.bytebank.common.security.JwtVerifier;
import dev.bytebank.common.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtIssuerTest {

    private static final String SECRET = "secreto-jwt-solo-para-tests-0123456789abcdef";
    private static final String OTHER_SECRET = "otro-secreto-distinto-para-tests-9876543210";

    @Test
    void issuedTokenIsAcceptedByAVerifierWithTheSameSecret() {
        String token = new JwtIssuer(properties(SECRET, null)).issue(user());

        assertThat(new JwtVerifier(properties(SECRET, null)).verify(token))
                .contains(new AuthUser(7L, "ana@bytebank.test", Role.CLIENTE));
    }

    @Test
    void issuedTokenIsRejectedByAVerifierWithAnotherSecret() {
        String token = new JwtIssuer(properties(SECRET, null)).issue(user());

        assertThat(new JwtVerifier(properties(OTHER_SECRET, null)).verify(token)).isEmpty();
    }

    @Test
    void tokensExpireAfter60MinutesByDefault() {
        assertThat(new JwtIssuer(properties(SECRET, null)).expirationSeconds()).isEqualTo(3600);
    }

    @Test
    void expirationCanBeConfigured() {
        assertThat(new JwtIssuer(properties(SECRET, 5L)).expirationSeconds()).isEqualTo(300);
    }

    private static BytebankProperties properties(String secret, Long expirationMinutes) {
        return new BytebankProperties(
                new BytebankProperties.Jwt(secret, expirationMinutes), "clave-interna-de-test", null);
    }

    private static User user() {
        User user = new User("ana@bytebank.test", "hash", "Ana Cliente", Role.CLIENTE);
        ReflectionTestUtils.setField(user, "id", 7L); // el id lo asigna la base de datos
        return user;
    }
}

package dev.bytebank.common.security;

import dev.bytebank.common.BytebankProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class JwtVerifierTest {

    private static final String SECRET = "secreto-jwt-solo-para-tests-0123456789abcdef";

    /**
     * Secreto que estuvo escrito en SecurityConstants (SEG-02). Es público y ningún entorno lo usa ya:
     * se conserva solo para comprobar que los tokens firmados con él se rechazan.
     */
    private static final String LEAKED_SECRET = "bytebank-secret-key-que-nadie-va-a-adivinar-2024";

    private final JwtVerifier verifier = new JwtVerifier(
            new BytebankProperties(new BytebankProperties.Jwt(SECRET, null), "clave-interna-de-test", null));

    @Test
    void acceptsTokenSignedWithConfiguredSecret() {
        String token = token(SECRET, "GESTOR", Instant.now().plus(1, ChronoUnit.HOURS));

        assertThat(verifier.verify(token))
                .contains(new AuthUser(7L, "ana@bytebank.test", Role.GESTOR));
    }

    @Test
    void rejectsTokenSignedWithTheLeakedSecret() {
        String forged = token(LEAKED_SECRET, "GESTOR", Instant.now().plus(1, ChronoUnit.HOURS));

        assertThat(verifier.verify(forged)).isEmpty();
    }

    @Test
    void rejectsTokenWhosePayloadHasBeenTampered() {
        String cliente = token(SECRET, "CLIENTE", Instant.now().plus(1, ChronoUnit.HOURS));
        String gestor = token(LEAKED_SECRET, "GESTOR", Instant.now().plus(1, ChronoUnit.HOURS));
        // Cabecera y firma del token legítimo, con el contenido de otro que dice ser GESTOR.
        String[] valid = cliente.split("\\.");
        String tampered = valid[0] + "." + gestor.split("\\.")[1] + "." + valid[2];

        assertThat(verifier.verify(tampered)).isEmpty();
    }

    @Test
    void rejectsExpiredToken() {
        String expired = token(SECRET, "CLIENTE", Instant.now().minus(1, ChronoUnit.MINUTES));

        assertThat(verifier.verify(expired)).isEmpty();
    }

    @Test
    void rejectsTextThatIsNotAToken() {
        assertThat(verifier.verify("esto-no-es-un-jwt")).isEmpty();
        assertThat(verifier.verify("")).isEmpty();
    }

    private static String token(String secret, String role, Instant expiration) {
        return Jwts.builder()
                .subject("7")
                .claim("email", "ana@bytebank.test")
                .claim("role", role)
                .expiration(Date.from(expiration))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}

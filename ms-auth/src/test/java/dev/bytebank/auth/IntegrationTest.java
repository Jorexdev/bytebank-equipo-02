package dev.bytebank.auth;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base de los tests de integración de ms-auth.
 *
 * El contenedor es singleton: se arranca una vez por JVM y lo comparten todos los contextos de Spring,
 * también los que activan perfiles distintos (por ejemplo, demo).
 */
@SpringBootTest(properties = {"bytebank.internal-key=" + IntegrationTest.INTERNAL_KEY,
        "bytebank.jwt.secret=clave-secreta-de-test-que-debe-ser-muy-larga-para-pasar-la-validacion"})
public abstract class IntegrationTest {

    protected static final String INTERNAL_KEY = "clave-interna-de-test";

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    static {
        POSTGRES.start();
    }
}

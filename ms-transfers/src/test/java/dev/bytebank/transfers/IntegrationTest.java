package dev.bytebank.transfers;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base de los tests de integración de ms-transfers.
 *
 * El contenedor es singleton: se arranca una vez por JVM y lo comparten todas las clases, igual que en ms-auth
 * y ms-accounts. ms-transfers no usa Kafka, y los clientes de ms-accounts y ms-fraud no llaman a nadie al
 * arrancar (sus URL tienen valor por defecto en application.yml), así que basta con PostgreSQL.
 */
@SpringBootTest(properties = {"bytebank.internal-key=clave-interna-de-test",
        "bytebank.jwt.secret=clave-secreta-de-test-que-debe-ser-muy-larga-para-pasar-la-validacion"})
public abstract class IntegrationTest {

    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    static {
        POSTGRES.start();
    }
}

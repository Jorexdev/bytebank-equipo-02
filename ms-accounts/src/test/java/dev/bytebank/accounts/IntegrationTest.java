package dev.bytebank.accounts;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base de los tests de integración.
 *
 * Los contenedores son singleton: se arrancan una vez por JVM y no se paran entre clases.
 * Así el contexto de Spring cacheado (que apunta a su puerto) sigue válido en todas las clases.
 * Con @Container/@Testcontainers se paraban al terminar cada clase y las siguientes fallaban
 * con "Connection refused".
 */
@SpringBootTest(properties = "bytebank.internal-key=clave-interna-de-test")
public abstract class IntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    static {
        POSTGRES.start();
        KAFKA.start();
    }
}

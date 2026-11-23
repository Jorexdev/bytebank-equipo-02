package dev.bytebank.transfers;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAM-02: la migración V3 renombra el estado de las transferencias que ya estaban pendientes.
 *
 * Usa Flyway directamente sobre un esquema propio del contenedor compartido: primero deja la base como
 * estaba en V2, inserta filas con los estados antiguos y después aplica el resto de migraciones.
 * Extiende {@link IntegrationTest} solo para reutilizar el contenedor de PostgreSQL.
 */
class PendingStatusMigrationTest extends IntegrationTest {

    private static final String SCHEMA = "pending_status_migration";

    @Test
    void v3RenamesExistingPendingTransfers() throws SQLException {
        flyway().target("2").load().migrate();
        insertTransfer(1, "PENDIENTE");
        insertTransfer(2, "COMPLETADA");
        insertTransfer(3, "RECHAZADA");

        flyway().load().migrate();

        assertThat(statusesById()).containsExactly(
                Map.entry(1L, "PENDIENTE_REVISION"),
                Map.entry(2L, "COMPLETADA"),
                Map.entry(3L, "RECHAZADA"));
    }

    private static org.flywaydb.core.api.configuration.FluentConfiguration flyway() {
        return Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .schemas(SCHEMA)
                .defaultSchema(SCHEMA)
                .cleanDisabled(false);
    }

    private static void insertTransfer(long id, String status) throws SQLException {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("insert into " + SCHEMA + ".transfers "
                    + "(id, origin_iban, destination_iban, amount, status, created_by, created_at) values ("
                    + id + ", 'ES0000000000000000000001', 'ES0000000000000000000002', 6000.00, '"
                    + status + "', 2, now())");
        }
    }

    private static Map<Long, String> statusesById() throws SQLException {
        Map<Long, String> statuses = new LinkedHashMap<>();
        try (Connection connection = connect(); Statement statement = connection.createStatement();
             var rows = statement.executeQuery("select id, status from " + SCHEMA + ".transfers order by id")) {
            while (rows.next()) {
                statuses.put(rows.getLong("id"), rows.getString("status"));
            }
        }
        return statuses;
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}

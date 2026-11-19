package dev.bytebank.accounts.outbox;

import dev.bytebank.accounts.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxCleanerTest extends IntegrationTest {

    static final String TOPIC = "kaf02.cleanup-test";

    @Autowired OutboxCleaner cleaner;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.update("delete from outbox_events");
    }

    @Test
    void deletesOnlyPublishedEventsOlderThanTheRetention() {
        insert("publicado-hace-4-dias", "now() - interval '4 days'");
        insert("publicado-hace-2-dias", "now() - interval '2 days'");
        insert("pendiente-de-hace-30-dias", "null");

        int deleted = cleaner.deleteOldPublished();

        assertThat(deleted).isEqualTo(1);
        assertThat(jdbc.queryForList("select message_key from outbox_events", String.class))
                .containsExactlyInAnyOrder("publicado-hace-2-dias", "pendiente-de-hace-30-dias");
    }

    private void insert(String key, String publishedAt) {
        jdbc.update("insert into outbox_events (topic, message_key, payload, created_at, published_at) "
                + "values (?, ?, '{}', now() - interval '30 days', " + publishedAt + ")", TOPIC, key);
    }
}

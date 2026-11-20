package dev.bytebank.accounts.outbox;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Activa las tareas programadas de ms-accounts: el relay del outbox y su limpieza. */
@Configuration
@EnableScheduling
class OutboxConfig {
}

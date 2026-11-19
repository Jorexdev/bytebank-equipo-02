-- KAF-02: eventos pendientes de publicar en Kafka. Se guardan en la misma transacción que el movimiento,
-- así que si la operación se deshace, el evento desaparece con ella. Un proceso aparte (OutboxRelay)
-- los publica después del commit.
CREATE TABLE outbox_events (
    id           BIGSERIAL    PRIMARY KEY,
    topic        VARCHAR(100) NOT NULL,
    message_key  VARCHAR(100) NOT NULL,
    payload      TEXT         NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    published_at TIMESTAMPTZ,
    attempts     INT          NOT NULL DEFAULT 0,
    last_error   VARCHAR(500)
);

-- El relay busca los pendientes en orden de id.
CREATE INDEX idx_outbox_events_pending ON outbox_events (id) WHERE published_at IS NULL;

-- La limpieza borra los publicados antiguos.
CREATE INDEX idx_outbox_events_published ON outbox_events (published_at) WHERE published_at IS NOT NULL;

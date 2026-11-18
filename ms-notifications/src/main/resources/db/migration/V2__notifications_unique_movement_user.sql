-- KAF-03: un titular recibe como mucho un aviso por movimiento, aunque Kafka reentregue el evento.

-- Avisos duplicados que ya existen por reentregas anteriores: se conserva el primero (id más bajo).
DELETE FROM notifications n
USING notifications older
WHERE n.movement_id = older.movement_id
  AND n.user_id = older.user_id
  AND n.id > older.id;

ALTER TABLE notifications
    ADD CONSTRAINT uq_notifications_movement_user UNIQUE (movement_id, user_id);

-- CAM-02: el estado de las transferencias pendientes de aprobación pasa a llamarse PENDIENTE_REVISION.
-- La columna status es VARCHAR(30) sin restricción CHECK, así que basta con actualizar las filas existentes.
UPDATE transfers SET status = 'PENDIENTE_REVISION' WHERE status = 'PENDIENTE';

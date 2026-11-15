# Contrato de ms-notifications

Avisos al cliente de cada movimiento de sus cuentas: ingresos, transferencias enviadas y recibidas, comisiones y pagos
con tarjeta. ms-accounts publica un evento en Kafka por cada movimiento y ms-notifications lo convierte en un aviso.

```
ms-accounts  --(topic bytebank.movements)-->  ms-notifications  <--  GET /api/notifications (cliente)
```

## Evento de movimiento

- Topic: `bytebank.movements`. Clave del mensaje: el IBAN de la cuenta (así los eventos de una misma cuenta van a la
  misma partición y llegan en orden).
- Valor: JSON.
- Grupo de consumidores de ms-notifications: `ms-notifications`.

```json
{
  "movementId": 154,
  "iban": "ES7639990001000000000001",
  "holderIds": [2, 3],
  "type": "TRANSFERENCIA_EMITIDA",
  "amount": -50.00,
  "balanceAfter": 2450.00,
  "concept": "Alquiler",
  "reference": "TRF-8f2c",
  "createdAt": "2026-06-12T10:05:00Z"
}
```

- `amount` lleva signo: positivo si entra dinero, negativo si sale (igual que en el extracto).
- `type`: `INGRESO`, `TRANSFERENCIA_EMITIDA`, `TRANSFERENCIA_RECIBIDA`, `COMISION` o `PAGO_TARJETA`.

## Reglas

- Cada movimiento genera un aviso por cada titular de la cuenta.
- Un evento sin `holderIds` (mensaje antiguo) se ignora y no genera avisos.
- Solo se avisa de movimientos que existen: si una operación falla, el cliente no recibe aviso de ella.
- Un cliente no recibe dos veces el aviso del mismo movimiento.

## Consultar los avisos

`GET /api/notifications?page=0&size=20` (usuario autenticado, rol `CLIENTE` o `GESTOR`)

- Devuelve solo los avisos del usuario que llama, los más recientes primero.
- `page` desde 0; `size` entre 1 y 100 (por defecto 20). Fuera de rango: `400`.
- Sin token: `401`.

Respuesta `200`:

```json
{
  "content": [
    {
      "id": 31,
      "iban": "ES7639990001000000000001",
      "type": "TRANSFERENCIA_EMITIDA",
      "amount": -50.00,
      "balanceAfter": 2450.00,
      "reference": "TRF-8f2c",
      "movementId": 154,
      "message": "Has enviado 50,00 € desde tu cuenta ···0001 (Alquiler). Saldo: 2.450,00 €",
      "createdAt": "2026-06-12T10:05:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

## Arrancar Kafka en local

Va en el `docker-compose.yml` (imagen `apache/kafka`, modo KRaft, sin ZooKeeper). Desde los contenedores se llega por
`kafka:9092`; si lanzas un servicio desde el IDE, por `localhost:29092`. Para ver los mensajes del topic:

```bash
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic bytebank.movements --from-beginning --property print.key=true
```

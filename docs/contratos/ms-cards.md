# Contrato de ms-cards

Tarjetas de débito asociadas a una cuenta. Todas las rutas son para usuarios con rol `CLIENTE` y pasan por el gateway.

## Emitir tarjeta

`POST /api/cards`

```json
{ "iban": "ES7639990001000000000001" }
```

- Cualquier titular de la cuenta puede pedir una tarjeta. La tarjeta queda a nombre de quien la pide. Si no es titular o la cuenta no existe: `404 CUENTA_NO_ENCONTRADA`.
- La cuenta tiene que estar `ACTIVA`. Si no: `409 CUENTA_NO_OPERATIVA`.
- Se genera un número del que solo se guardan los 4 últimos dígitos.
- Límite diario por defecto: 600 € (`bytebank.cards.default-daily-limit`).

Respuesta `201`:

```json
{
  "id": 1,
  "accountIban": "ES7639990001000000000001",
  "maskedNumber": "**** **** **** 4821",
  "status": "ACTIVA",
  "dailyLimit": 600.00,
  "createdAt": "2026-06-12T10:00:00Z"
}
```

## Pagar con la tarjeta

`POST /api/cards/{id}/payments` (simula el cobro de un comercio)

```json
{ "amount": 42.50, "merchant": "Supermercado" }
```

- Importe positivo, con 2 decimales como mucho: si no, `400`.
- La tarjeta tiene que ser del usuario: si no, `404 TARJETA_NO_ENCONTRADA`.
- El pago se rechaza si:
  - la tarjeta está bloqueada: `TARJETA_BLOQUEADA`;
  - con este pago se supera el límite diario: `LIMITE_DIARIO_SUPERADO`. El límite cuenta los pagos **aprobados**
    desde las 00:00 (hora de Madrid);
  - la cuenta no tiene saldo o no está activa: el código que devuelva ms-accounts (`FONDOS_INSUFICIENTES`,
    `CUENTA_NO_OPERATIVA`).
- Un pago aprobado genera un movimiento `PAGO_TARJETA` en la cuenta.
- Los pagos rechazados también se guardan, para que el cliente los vea en su historial.

Respuesta `201`, tanto si se aprueba como si se rechaza:

```json
{
  "id": 7,
  "cardId": 1,
  "amount": 42.50,
  "merchant": "Supermercado",
  "status": "APROBADO",
  "rejectionReason": null,
  "createdAt": "2026-06-12T10:05:00Z"
}
```

## Ya implementado

- `GET /api/cards`: tarjetas del usuario.
- `GET /api/cards/{id}`: una tarjeta.
- `PATCH /api/cards/{id}/status` con `{ "status": "BLOQUEADA" | "ACTIVA" }`.
- `GET /api/cards/{id}/payments`: historial de pagos de la tarjeta.

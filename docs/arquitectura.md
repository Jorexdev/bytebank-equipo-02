# Arquitectura

```
                         +-------------+
   cliente  ---------->  | ms-gateway  |  :8080   (solo /api/**)
                         +------+------+
                                |
      +-----------+-------------+-------------+-------------+
      |           |             |             |             |
 +----v----+ +----v------+ +----v-------+ +---v------+ +----v-----+
 | ms-auth | |ms-accounts| |ms-transfers| | ms-cards | | ms-fraud |
 |  :8081  | |   :8082   | |   :8083    | |  :8084   | |  :8085   |
 +---------+ +-----^-----+ +-----+------+ +----+-----+ +----------+
                   |             |             |
                   +-------------+-------------+
                     llamadas internas /internal/**
```

Cada servicio tiene su propia base de datos (`auth_db`, `accounts_db`, `transfers_db`, `cards_db`, `fraud_db`),
todas en la misma instancia de PostgreSQL. Ningún servicio lee la base de datos de otro.

## Seguridad

- **Clientes**: ms-auth emite un JWT HS256 con el id del usuario (`sub`), su `email` y su `role`
  (`CLIENTE` o `GESTOR`). El resto de servicios valida el token en cada petición (módulo `common`).
- **Entre servicios**: las rutas `/internal/**` no pasan por el gateway y exigen la cabecera `X-Internal-Key`
  con la clave compartida (`INTERNAL_KEY`).

## Módulo common

Código compartido por los servicios de dominio:

- `security`: filtro JWT, filtro de clave interna, `CurrentUser` para saber quién llama.
- `error`: `ApiException` y el formato único de error.
- `http`: `InternalClients` para llamar a otros servicios con la clave interna.
- `iban`: validación y generación de IBAN (ByteBank es la entidad 3999).

## Formato de error

Todos los servicios devuelven los errores así:

```json
{
  "timestamp": "2026-06-10T09:30:00Z",
  "status": 409,
  "error": "Conflict",
  "code": "FONDOS_INSUFICIENTES",
  "message": "Saldo insuficiente",
  "path": "/internal/accounts/debit"
}
```

`code` es estable y es lo que deben mirar los clientes y los demás servicios; `message` es para personas.

## Flujo de una transferencia

1. El cliente hace `POST /api/transfers` con origen, destino, importe y concepto.
2. ms-transfers comprueba los datos y registra la transferencia.
3. Si el importe supera los 5.000 €, la transferencia queda `PENDIENTE` y no se mueve dinero hasta que un gestor la aprueba o la rechaza. Los gestores consultan las pendientes en `GET /api/transfers/pending`.
4. Carga el importe en la cuenta de origen (`POST /internal/accounts/debit` en ms-accounts).
5. Si el destino es de ByteBank, lo abona en la cuenta de destino (`POST /internal/accounts/credit`).
6. La transferencia ejecutada queda `COMPLETADA` o `RECHAZADA` con el código del error.

Las transferencias a otras entidades solo cargan en origen: la liquidación con el otro banco queda fuera de este sistema.

## Cuentas compartidas

Una cuenta puede tener hasta cuatro titulares. El creador es el primer titular y cualquier titular puede añadir a otro
cliente por su email con `POST /api/accounts/{iban}/holders`.

- Todos los titulares ven la cuenta y el extracto, transfieren desde ella y piden tarjetas.
- El alta bloquea la fila de la cuenta para que dos altas simultáneas no superen el máximo.
- La relación vive en `account_holders`; `accounts` ya no guarda `owner_id`.
- `GET /api/accounts/{iban}/holders` devuelve los datos de los titulares.

## Pagos con tarjeta

`ms-cards` serializa los pagos de una misma tarjeta bloqueando su fila antes de consultar el límite diario y debitar la
cuenta. El límite cuenta únicamente pagos aprobados desde las 00:00 de `Europe/Madrid`; los pagos rechazados también
se guardan en el historial, pero no consumen límite.

- Cada intento recibe una referencia interna `CARD-<uuid>`, que también se guarda en `card_payments` para rastrear el
  movimiento en `ms-accounts`.
- Un pago aprobado se debita mediante `POST /internal/accounts/debit` con tipo `PAGO_TARJETA`.
- `FONDOS_INSUFICIENTES` y `CUENTA_NO_OPERATIVA` son rechazos de negocio y se guardan. Si `ms-accounts` no responde,
  se devuelve `503 SERVICIO_NO_DISPONIBLE` y no se guarda el intento.
- Si el débito tiene éxito pero falla el guardado local, se intenta compensar con un crédito `INGRESO` usando la
  referencia `<referencia>-REV`. El concepto identifica el pago original para que el movimiento sea trazable.
- No hay idempotencia de pagos: un reintento del cliente representa otro intento.

## Roles

| Rol | Puede |
|---|---|
| CLIENTE | Abrir cuentas, ver las cuentas de las que es titular, hacer transferencias desde ellas, gestionar sus tarjetas |
| GESTOR | Todo lo de consulta, bloquear y desbloquear cuentas, hacer ingresos en ventanilla, aprobar o rechazar transferencias pendientes, ver las validaciones antifraude |

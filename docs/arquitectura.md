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

  ms-accounts --(outbox)--> Kafka (bytebank.movements) --> ms-notifications :8086
```

Cada servicio tiene su propia base de datos (`auth_db`, `accounts_db`, `transfers_db`, `cards_db`, `fraud_db`,
`notifications_db`), todas en la misma instancia de PostgreSQL. Ningún servicio lee la base de datos de otro.

## Seguridad

- **Clientes**: ms-auth emite un JWT HS256 con el id del usuario (`sub`), su `email` y su `role`
  (`CLIENTE` o `GESTOR`). El resto de servicios valida el token en cada petición (módulo `common`).
- **Entre servicios**: las rutas `/internal/**` no pasan por el gateway y exigen la cabecera `X-Internal-Key`
  con la clave compartida (`INTERNAL_KEY`) y el rol `INTERNAL`. Ese rol no se da a los usuarios: solo lo reciben las
  peticiones internas entre microservicios.

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
2. ms-transfers comprueba los datos y que la cuenta de origen pertenezca al cliente.
3. Envía la operación a ms-fraud (`POST /internal/fraud/check`).
4. Si el veredicto es `BLOQUEADA`, la transferencia queda `RECHAZADA` con el código de la regla o reglas que hayan saltado y no se mueve dinero.
5. Si el veredicto es `REVISAR`, queda `PENDIENTE_REVISION` y no se mueve dinero hasta que un gestor la aprueba o la rechaza. Los gestores consultan las pendientes en `GET /api/transfers/pending`.
6. Si el veredicto es `APROBADA`, sigue el flujo normal. Si el importe supera los 5.000 €, también queda `PENDIENTE_REVISION` por la regla de CAM-02.
7. Calcula la comisión: 0,5 % del importe con un mínimo de 1,00 € si el destino es de otra entidad; cero si es de ByteBank.
8. Carga el importe y la comisión en la cuenta de origen (`POST /internal/accounts/debit`). ms-accounts crea el movimiento de la transferencia y otro de tipo `COMISION` (`TRF-<id>-COM`) en la misma transacción.
9. Si el destino es de ByteBank, lo abona en la cuenta de destino (`POST /internal/accounts/credit`).
10. La transferencia ejecutada queda `COMPLETADA` o `RECHAZADA` con el código del error.

Si ms-fraud no responde, ms-transfers devuelve `503 SERVICIO_NO_DISPONIBLE` y no crea la transferencia ni mueve
dinero. La aprobación de un gestor no repite la validación antifraude. La comisión no cuenta para las reglas
antifraude ni para el umbral de 5.000 €.

El estado de espera se llamaba `PENDIENTE` hasta CAM-02; ahora es `PENDIENTE_REVISION`, y la migración V3 de
ms-transfers cambió las filas existentes (ver [diagnostico.md](diagnostico.md), sección 4.11).

Las transferencias a otras entidades solo cargan en origen, incluida la comisión: la liquidación con el otro banco queda fuera de este sistema.

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

## Avisos y outbox

Los avisos van por Kafka, pero no se envían desde la transacción que mueve el dinero:

- El movimiento y su evento se guardan en la misma transacción (tabla `outbox_events`). Si la operación se deshace, el evento desaparece con ella.
- `OutboxRelay` publica los pendientes cada segundo, en orden, y solo marca un evento como publicado cuando Kafka lo confirma. Si falla, lo deja pendiente y lo reintenta.
- La entrega es **al menos una vez**. ms-notifications ignora los eventos repetidos por `movementId` y titular, así que el cliente no ve dos avisos del mismo movimiento.
- Los eventos publicados se borran a los 3 días; los pendientes no se borran nunca.

Detalle y verificación: [diagnostico.md](diagnostico.md), KAF-02 (4.18) y KAF-03 (4.17). Contrato: [contratos/ms-notifications.md](contratos/ms-notifications.md).

## Roles

| Rol | Puede |
|---|---|
| CLIENTE | Abrir cuentas, ver las cuentas de las que es titular, hacer transferencias desde ellas, gestionar sus tarjetas |
| GESTOR | Todo lo de consulta, bloquear y desbloquear cuentas, hacer ingresos en ventanilla, aprobar o rechazar transferencias pendientes, ver las validaciones antifraude |
| INTERNAL | Solo peticiones entre servicios a `/internal/**`, con la clave `X-Internal-Key`; no es un rol de usuario |

## Fuentes

- Gateway y rutas: `ms-gateway/src/main/resources/application.yml`.
- Filtros de seguridad y roles: `common/src/main/java/dev/bytebank/common/security/`.
- Servicios y controladores: `ms-*/src/main/java/dev/bytebank/`.
- Esquemas y migraciones: `ms-*/src/main/resources/db/migration/`.
- Kafka y outbox: `ms-accounts/src/main/java/dev/bytebank/accounts/event/`, `ms-notifications/src/main/java/dev/bytebank/notifications/notification/`.
- Workflow de CI: `.github/workflows/ci.yml`.

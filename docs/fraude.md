# Validación antifraude (ms-fraud)

**Estado:** implementado y verificado (reglas e integración, 4.13 y 4.14 del [diagnostico.md](diagnostico.md)). La decisión de fallo cerrado está recogida en [decisiones.md](decisiones.md).

ms-fraud recibe cada transferencia antes de ejecutarse y devuelve un veredicto:

| Veredicto | Qué significa | Qué hace ms-transfers |
|---|---|---|
| `APROBADA` | Se puede ejecutar | Sigue el flujo normal; si supera 5.000 €, queda pendiente por la regla de CAM-02 |
| `REVISAR` | Un gestor tiene que revisarla antes de ejecutarla | Queda `PENDIENTE`, sin mover dinero, hasta que un gestor la aprueba o la rechaza |
| `BLOQUEADA` | Se rechaza | Queda `RECHAZADA` con el código de la regla o reglas que hayan saltado; no se mueve dinero |

Si saltan varias reglas, gana el veredicto más grave. Cada validación queda guardada (tabla `fraud_checks`).
Solo los gestores pueden consultarlas con `GET /api/fraud/checks?iban=...`.

## Reglas acordadas con negocio

| Regla | Código | Condición | Veredicto | Estado |
|---|---|---|---|---|
| Importe máximo por operación | `IMPORTE_MAXIMO` | Importe de 20.000 € o más | BLOQUEADA | Hecha |
| Transferencia a otra entidad | `EXTERNA_IMPORTE_ALTO` | Destino de otro banco e importe de 1.000 € o más | REVISAR | Hecha |
| Frecuencia | `FRECUENCIA_ALTA` | Más de 5 validaciones desde la misma cuenta en 10 minutos (se bloquea la sexta) | BLOQUEADA | Hecha |

La regla de frecuencia cuenta las validaciones de la cuenta de origen en los últimos 10 minutos, incluidas las
bloqueadas o rechazadas. Si ya hay 5, la siguiente se bloquea.

Los umbrales se configuran en `application.yml` (`bytebank.fraud.*`):

```yaml
block-threshold: 20000.00
external-review-threshold: 1000.00
frequency-max: 5
frequency-window: 10m
```

## Contrato

`POST /internal/fraud/check` (con `X-Internal-Key`)

```json
{ "originIban": "ES...", "destinationIban": "ES...", "userId": 2, "amount": 1500.00 }
```

Respuesta:

```json
{ "checkId": 17, "verdict": "REVISAR", "reasons": ["EXTERNA_IMPORTE_ALTO"] }
```

## Integración con ms-transfers

ms-transfers llama a `POST /internal/fraud/check` después de comprobar los datos y la titularidad de la cuenta de
origen, y antes de guardar o ejecutar la transferencia.

La aprobación de un gestor no repite la validación: cuando aprueba una transferencia `PENDIENTE`, ejecuta el
movimiento con el veredicto que ya se registró al crearla.

## Si ms-fraud no responde

Decisión del equipo: **fallo cerrado**. Si ms-fraud no responde, tarda demasiado o devuelve un error de servidor,
ms-transfers responde `503 SERVICIO_NO_DISPONIBLE` y no crea la transferencia ni mueve dinero.

Motivos:

- No se ejecuta una transferencia sin la validación antifraude.
- No se guarda un rechazo falso: la operación no llegó a existir.
- El cliente puede reintentar cuando el servicio se recupere.
- Es el mismo comportamiento que cuando no responde ms-accounts.

## Límites

- La frecuencia se cuenta sobre `fraud_checks`, así que un intento fallido también acerca el bloqueo.
- No hay bloqueo por cuenta: dos peticiones simultáneas pueden leer el mismo historial y dejar pasar como mucho una
  operación extra. Si se necesita un conteo estricto, habría que añadir un bloqueo por cuenta.

## Fuentes

- Reglas: `ms-fraud/src/main/java/dev/bytebank/fraud/rules/`.
- Contrato interno: `ms-fraud/src/main/java/dev/bytebank/fraud/check/FraudController.java`.
- Servicio y umbrales: `ms-fraud/src/main/java/dev/bytebank/fraud/check/FraudService.java`, `ms-fraud/src/main/java/dev/bytebank/fraud/FraudProperties.java`.
- Llamada desde transferencias: `ms-transfers/src/main/java/dev/bytebank/transfers/transfer/TransferService.java`.

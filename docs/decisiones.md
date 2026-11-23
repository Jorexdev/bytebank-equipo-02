# Decisiones de ByteBank

Una decisión es una elección ya tomada y comprobada en el código o en las pruebas. Aquí solo consta el **porqué**; los detalles y la verificación están en el documento que se indica en cada fila.

| Decisión | Por qué | Qué implica | Dónde se explica |
|---|---|---|---|
| El dinero se maneja con `BigDecimal` y se guarda como `NUMERIC(19,2)` | Los importes `double` daban saldos como 0,30000000000000004 € | Todos los importes y saldos van a céntimos exactos; la migración `V4` convirtió las columnas existentes | `diagnostico.md`, BUG-05 (4.21) |
| La cuenta se bloquea mientras se mueve dinero | Varias transferencias simultáneas podían gastar dos veces el mismo saldo | Cargo y abono se atienden de uno en uno; el que no tiene fondos se rechaza | `diagnostico.md`, CON-01 (4.10) |
| Fallo cerrado si no responde el antifraude, o ms-accounts antes de crear la transferencia | No se ejecuta dinero sin validar, ni se guarda un rechazo falso | La operación no se crea y el cliente recibe `503` para poder reintentar. Si ms-accounts deja de responder durante el cargo o el abono, la transferencia queda `RECHAZADA` (ver `diagnostico.md`, sección 7) | `fraude.md`, "Si ms-fraud no responde"; `diagnostico.md` (4.13) |
| Avisos con outbox transaccional y Kafka | Antes podía salir el aviso de un movimiento que luego se deshacía, o perderse si Kafka fallaba | El evento se guarda en la misma transacción; el relay lo publica después; la entrega es al menos una vez | `diagnostico.md`, KAF-02 (4.18); `arquitectura.md`, "Avisos y outbox" |
| Los avisos repetidos se ignoran | Kafka puede entregar el mismo mensaje más de una vez | No aparecen dos avisos del mismo movimiento para el mismo titular | `diagnostico.md`, KAF-03 (4.17) |
| Más de 5.000 € y el veredicto `REVISAR` quedan pendientes de un gestor | Negocio quiere revisión humana de los importes altos y de lo que marca el antifraude | El dinero no se mueve hasta que un gestor aprueba o rechaza | `diagnostico.md`, CAM-02 (4.11) |
| Cuentas compartidas, máximo cuatro titulares | Varias personas pueden usar la misma cuenta | El creador es el primer titular; el alta bloquea la fila para no pasarse del máximo | `diagnostico.md`, CAM-01 (4.22); `arquitectura.md`, "Cuentas compartidas" |
| Comisión del 0,5 % con mínimo de 1,00 € | Las transferencias a otras entidades tienen coste; las internas, no | Se cobra como movimiento `COMISION` aparte en la cuenta de origen | `diagnostico.md`, SOR-01 (4.19) |
| Fechas del extracto en hora de Madrid | El fin de rango en UTC dejaba fuera el último día y movía pagos de las 00:30 al día anterior | `from` y `to` se calculan en `bytebank.zone`; `to` es inclusivo | `diagnostico.md`, BUG-04 (4.9) |
| Secretos por variables de entorno, sin valor por defecto | El secreto de los JWT estaba escrito en el código, igual en todos los entornos | Si falta `JWT_SECRET`, `INTERNAL_KEY` o la contraseña, los servicios no arrancan | `diagnostico.md`, SEG-02 (4.12); `README.md`, "Seguridad" |
| Perfiles `demo` y `prod` separados, primer gestor por variable de entorno | Evitar que un entorno de prueba con contraseñas públicas se use con datos reales | Los perfiles no se pueden activar a la vez; en `prod` no se siembran cuentas | `diagnostico.md`, SEG-03 (4.20); `README.md`, "Entorno con datos reales" |

## Límites asumidos

Son consecuencias conocidas de estas decisiones. No son defectos nuevos: están aceptados y escritos para que quien continúe sepa dónde están.

- **Pagos de tarjeta sin idempotencia:** un reintento del cliente es otro intento de pago. Ver `diagnostico.md` (4.16) y `arquitectura.md`, "Pagos con tarjeta".
- **Comisión recalculada:** no se guarda en la transferencia, así que una tarifa futura cambiaría lo que muestra una operación antigua. La solución está descrita en `diagnostico.md` (4.19).
- **Fila bloqueada durante una llamada por red:** en `CardService#pay` y `AccountService#addHolder` se bloquea la fila y se llama a otro servicio. Si el otro tarda, quienes esperan esa fila esperan también. Ver `diagnostico.md`, sección 7.
- **Conteo de fraude sin bloqueo por cuenta:** dos peticiones simultáneas pueden dejar pasar como mucho una operación extra. Ver `fraude.md`, "Límites".

# Diagnóstico del proyecto ByteBank

**Fecha de revisión inicial:** 10 de noviembre de 2026  
**Estado:** diagnóstico inicial, pendiente de completar durante los sprints.

## 1. Objetivo

Este documento recoge el estado técnico del proyecto ByteBank después de recibir el repositorio del equipo anterior.

Se documentan la configuración general, el procedimiento de arranque, los problemas reproducidos, las funcionalidades incompletas y los riesgos que requieren investigación.

El diagnóstico es progresivo: se actualizará conforme se corrijan incidencias y se realicen nuevas pruebas.

Para conocer el diseño previsto de los microservicios, las comunicaciones y la seguridad, consultar también [arquitectura.md](arquitectura.md).

## 2. Arquitectura y tecnologías

El proyecto utiliza una arquitectura de microservicios desarrollada con:

- Java 17 y Spring Boot 3.3.5.
- Maven y módulos independientes.
- PostgreSQL 15, con una base de datos por servicio.
- Spring Security y JWT para autenticación.
- Spring Cloud Gateway como punto de entrada.
- Apache Kafka para la comunicación mediante eventos.
- Flyway para las migraciones de base de datos.
- Docker Compose para levantar el entorno.
- GitHub Actions para integración continua.
- JUnit 5, Mockito y Testcontainers para pruebas.

Los servicios identificados son:

| Servicio         | Puerto | Responsabilidad                      |
| ---------------- | -----: | ------------------------------------ |
| ms-gateway       |   8080 | Entrada y enrutamiento de peticiones |
| ms-auth          |   8081 | Registro, autenticación y JWT        |
| ms-accounts      |   8082 | Cuentas, saldos y movimientos        |
| ms-transfers     |   8083 | Transferencias                       |
| ms-cards         |   8084 | Tarjetas y pagos                     |
| ms-fraud         |   8085 | Validación antifraude                |
| ms-notifications |   8086 | Avisos mediante Kafka                |

El módulo `common` contiene componentes compartidos entre los servicios.

## 3. Procedimiento de arranque

El README establece el siguiente procedimiento:

```bash
cp .env.example .env
# Modificar las claves y credenciales locales.
docker compose up --build
```

La API debe quedar accesible a través de `http://localhost:8080`.

También se documenta la ejecución de las pruebas mediante:

```bash
./mvnw clean verify
```

Las pruebas de integración que utilizan Testcontainers necesitan Docker en funcionamiento.

### 3.1. Resultado de la primera ejecución

En el entorno local se ha comprobado lo siguiente:

- Las imágenes Docker de los servicios se construyen correctamente.
- PostgreSQL y Kafka llegan a iniciar y muestran estado saludable.
- `ms-gateway`, `ms-auth`, `ms-transfers`, `ms-cards`, `ms-fraud` y `ms-notifications` llegan a completar su inicialización según sus registros de arranque.
- `ms-accounts` no consigue iniciar debido a un error en su tercera migración de Flyway.

Por tanto, aunque los contenedores se creen, el sistema todavía no está operativo de extremo a extremo. El estado `Up` de un contenedor no garantiza que su servicio esté preparado ni que sus operaciones funcionen.

## 4. Incidencias confirmadas

### 4.1. Error en la migración de ms-accounts

**Estado:** corregido y verificado en el entorno local.  
**Impacto:** bloqueaba el arranque de `ms-accounts`.

La migración `V3__accounts_owner_index.sql` intenta crear un índice sobre una columna llamada `owner`:

```sql
CREATE INDEX idx_accounts_owner ON accounts (owner);
```

Sin embargo, la tabla `accounts`, creada en la migración V1, contiene la columna `owner_id`, no `owner`.

El código Java también utiliza `ownerId`, y el repositorio consulta las cuentas mediante `findByOwnerIdOrderByCreatedAt`.

PostgreSQL devuelve el error:

```text
SQL State: 42703
ERROR: column "owner" does not exist
```

**Causa identificada:** inconsistencia entre el nombre de columna utilizado en la migración V3 y el definido en el esquema de la tabla.

**Verificación mediante pruebas automatizadas:**

Se ejecutó `./mvnw test` desde la raíz del proyecto.

En el módulo `ms-accounts`:

- `LedgerServiceIntegrationTest`: 4 errores.
- `MovementServiceTest`: 2 tests ejecutados, 1 omitido y 0 errores.
- Los cuatro errores de integración se producen al intentar cargar el contexto de Spring Boot.
- La causa raíz es el fallo de la migración `V3__accounts_owner_index.sql`, que intenta crear un índice sobre la columna inexistente `owner`.

Los cuatro errores no corresponden a fallos independientes de la lógica de negocio: los tests no llegan a ejecutarla porque el contexto de Spring Boot no consigue inicializarse.

**Corrección aplicada:** la migración crea el índice sobre la columna correcta:

```sql
CREATE INDEX idx_accounts_owner ON accounts (owner_id);
```

**Verificación:** con el entorno levantado, Flyway aplica la versión 3 sin errores, `ms-accounts` arranca, la consulta de cuentas responde y las pruebas funcionales del Gateway se ejecutan con normalidad.

**Pendiente:** confirmar el resultado en CI. En local, `LedgerServiceIntegrationTest` ya se ejecuta sin errores (5 tests, 0 fallos).

### 4.2. Puerto incorrecto en la comunicación entre Gateway y autenticación

**Estado:** corregido y verificado funcionalmente.

En la configuración original de `docker-compose.yml` se establecía:

```yaml
AUTH_URL: http://ms-auth:8018
```

Sin embargo, ms-auth escucha en el puerto 8081, según su configuración y sus registros de arranque.

**Causa raíz confirmada:** el puerto configurado para la comunicación interna no coincidía con el puerto real de escucha de ms-auth.

**Corrección aplicada:** se ha cambiado `AUTH_URL` a:

```yaml
AUTH_URL: http://ms-auth:8081
```

**Verificación funcional a través del Gateway:**

- Credenciales correctas: HTTP 200.
- Contraseña incorrecta: HTTP 401 Unauthorized.
- Se ha comprobado que el Gateway puede comunicarse con ms-auth después de que ambos servicios hayan terminado de arrancar.

**Observación:** inmediatamente después de recrear el Gateway puede ser necesario esperar a que termine su arranque antes de enviar peticiones. Se observó una respuesta HTTP 000 seguida de respuestas correctas (200 y 401).

**Resultado:** la incidencia original del puerto incorrecto queda resuelta. El problema de la migración Flyway V3 de ms-accounts permanece pendiente y es independiente de esta corrección.

### 4.3. Configuración de integración continua

**Estado:** configuración anterior corregida en la rama `ci/CI-01-real-tests-docker`. Pendiente de verificar mediante GitHub Actions.

En el archivo `.github/workflows/ci.yml` se identificó una configuración que ejecutaba:

```yaml
- name: Build
  run: mvn -B -q package -DskipTests || true
```

Se identificaron dos problemas:

- `-DskipTests` impedía ejecutar las pruebas durante la compilación.
- `|| true` hacía que el comando devolviera un resultado satisfactorio aunque Maven hubiera fallado.

**Corrección implementada:**

Se ha sustituido la configuración anterior por dos trabajos independientes:

- **Maven tests:** configura Java 17 y ejecuta `./mvnw -B -fae verify`, incluyendo las pruebas automatizadas.
- **Build Docker images:** construye las imágenes Docker de los siete microservicios mediante Docker Buildx.

Ambos trabajos se ejecutan en paralelo. Si cualquiera falla, la ejecución global de CI queda marcada como fallida.

**Verificación local:**

- La ejecución de `./mvnw -B -fae verify` detectó los errores de inicialización de `ms-accounts` causados por la migración Flyway V3.
- Los demás módulos independientes completaron correctamente su ejecución.
- Se construyeron correctamente las imágenes Docker de los siete microservicios.

**Pendiente:** publicar la rama, comprobar la ejecución de ambos trabajos en GitHub Actions y configurar, si procede, los controles de protección de la rama para exigir que las comprobaciones sean satisfactorias antes de integrar cambios.

### 4.4. BUG-01 — Transferencia con importe negativo

**Estado:** corregido en código, pendiente de probar en el entorno.
**Impacto:** permite mover dinero en el sentido contrario al esperado.

**Causa:** el importe no se validaba. Restar un negativo suma, y sumar un negativo resta.

**Corrección:**

- `ms-transfers`: `TransferService.create` comprueba `amount.signum() <= 0` y responde 400 con `IMPORTE_NO_VALIDO`.
- `ms-accounts`: `MovementRequest.amount` lleva `@Positive`, de modo que el endpoint interno también rechaza importes de cero o negativos.

**Verificación:** tests automáticos con -100 €: se rechaza en transferencias (5 tests) y en la validación del importe del ledger (1 test).

**Pendiente:** los saldos ya alterados hay que corregirlos a mano.

### 4.5. BUG-02 — IBAN inexistente devuelve 500

**Estado:** corregido en código, pendiente de probar en el entorno.
**Impacto:** consultar una cuenta que no existe devuelve error de servidor, y una transferencia desde una cuenta inexistente responde 503.

**Causa:** `AccountService.get` sacaba el valor de un `Optional` vacío con `.get()`. Eso lanzaba una excepción no controlada y acababa en `500 ERROR_INTERNO`. Al transferir, el 500 remoto se traducía en `503 SERVICIO_NO_DISPONIBLE`.

**Corrección:** `AccountService.get` responde `404 CUENTA_NO_ENCONTRADA` cuando el IBAN no existe. Cubre la consulta de cuenta, el extracto, el cambio de estado, el ingreso y la consulta interna.

**Verificación:** tests automáticos: un IBAN desconocido da 404, un IBAN conocido devuelve la cuenta y una transferencia desde una cuenta inexistente da 404 sin crearse.

**Pendiente:** probar con la API real a través del Gateway.

### 4.6. SEG-01 — Acceso no autorizado a cuentas y transferencias

**Estado:** corregido y verificado mediante pruebas automatizadas.  
**Fecha de corrección:** 11 de noviembre de 2026.

**Problema detectado:**

La auditoría identificó que un cliente podía acceder a información de cuentas y extractos de otros usuarios modificando el IBAN de las peticiones. También podía intentar realizar transferencias desde cuentas de las que no era titular.

Además, el cambio de estado de una cuenta no estaba restringido al rol `GESTOR`.

**Correcciones aplicadas:**

- Se ha incorporado el control de titularidad en las consultas públicas de cuentas y movimientos.
- Los clientes solo pueden listar y consultar sus propias cuentas.
- Los gestores pueden consultar todas las cuentas y sus movimientos.
- El cambio de estado de una cuenta está restringido al rol `GESTOR`.
- Antes de crear una transferencia, se comprueba que el usuario autenticado sea titular de la cuenta de origen.
- Se mantiene la separación entre las operaciones públicas y las llamadas internas entre microservicios. Estas últimas continúan protegidas mediante `X-Internal-Key` y el rol `INTERNAL`.

**Pruebas automatizadas:**

Se han ampliado las pruebas de los servicios y se ha incorporado `AccountControllerSecurityTest`, que comprueba los permisos mediante peticiones HTTP con MockMvc.

| Clase de prueba                 | Tests superados |
| ------------------------------- | --------------: |
| `AccountServiceTest`            |               7 |
| `AccountControllerSecurityTest` |               8 |
| `TransferServiceTest`           |               7 |

Las pruebas verifican tanto los accesos autorizados como los intentos de acceso no permitido.

**Verificación global:**

Se ha ejecutado desde la raíz del proyecto:

```bash
./mvnw -B -fae verify
```

Resultado: `BUILD SUCCESS`. Los nueve módulos del proyecto han completado correctamente la compilación y sus pruebas automatizadas.

**Resultado:** la incidencia SEG-01 queda corregida y verificada. Se mantienen las restricciones de acceso por rol y titularidad sin alterar el mecanismo de comunicación interna entre microservicios.

### 4.7. BUG-06 — Transferencia a cuenta bloqueada pierde el dinero

**Estado:** corregido y verificado en el entorno local.  
**Fecha de corrección:** 12 de noviembre de 2026.

**Problema detectado:**

Al transferir hacia una cuenta bloqueada, el sistema cobraba a la cuenta de origen y después intentaba abonar al destino. Como el abono fallaba, la transferencia quedaba rechazada pero el dinero cobrado no se devolvía.

**Correcciones aplicadas:**

- Se comprueba el estado de la cuenta destino **antes** de cobrar al origen. Si no está operativa, la transferencia se rechaza con `CUENTA_NO_OPERATIVA` sin registrar ningún movimiento.
- Si el abono falla más adelante (por ejemplo, la cuenta se bloquea durante la operación), se devuelve el importe a la cuenta de origen con el concepto "Reverso de la transferencia \<id\>".

**Verificación:**

- Prueba por la API: transferencia de 50 € a una cuenta bloqueada devuelve `RECHAZADA` con `CUENTA_NO_OPERATIVA`; los saldos no cambian y no se registra ningún movimiento.
- Pruebas automatizadas: destino bloqueado se rechaza sin débito ni abono, y si el abono falla se ejecuta el reverso hacia el origen.

**Pendiente:** el reverso no se puede provocar manualmente con la API actual; su verificación se realiza en la prueba unitaria.

### 4.8. Extracto: falta un movimiento en cada página

**Estado:** corregido en código (rama `fix/transactions-missing-statement`), pendiente de probar en el entorno.
**Impacto:** el extracto se saltaba un movimiento al principio de cada página, así que el cliente nunca veía todos sus movimientos.

**Causa:** `MovementService.statement` calculaba el desplazamiento como `page * size + 1`, suponiendo que `OFFSET` en PostgreSQL empieza en 1. Pero empieza en 0, así que la primera página arrancaba en el segundo movimiento.

**Corrección:** el desplazamiento pasa a ser `page * size`.

**Verificación:** test `firstPageStartsAtOffsetZero` en `MovementServiceTest`.

### 4.9. Extracto: el filtro por fechas no incluye el último día

**Estado:** corregido en código (rama `fix/date-filter-does-not-show-the-most-recent-day`), pendiente de probar en el entorno.
**Impacto:** al filtrar el extracto con `to`, los movimientos de ese día no aparecían. Además, los límites del rango se calculaban en UTC, de modo que los movimientos hechos entre las 00:00 y las 01:00/02:00 (hora de Madrid) caían en el día anterior.

**Causa:** el fin del rango se calculaba como `to` a las 00:00 UTC y se trataba como exclusivo. El test `filterByDateIncludesLastDay` que lo detectaba estaba deshabilitado (ver 6.2).

**Corrección:**

- `to` pasa a ser inclusivo: el rango termina al inicio del día siguiente.
- Los límites `from` y `to` se calculan en la zona configurada en `bytebank.zone` (`Europe/Madrid`), no en UTC.

**Verificación:** el test deshabilitado se ha sustituido por `rangeIncludesLastDayInMadridTime`, y se ha añadido `singleDayFilterIncludesTheWholeDay` para comprobar que un único día se incluye completo. `MovementServiceTest`: 4 tests ejecutados, 0 fallos, 0 omitidos.

**Pendiente:** probar con la API real a través del Gateway.

### 4.10. CON-01 — Transferencias simultáneas gastan más que el saldo

**Estado:** corregido y verificado en el entorno local.
**Impacto:** un cliente con 100 € lanzó varias transferencias de 30 € a la vez y se ejecutaron todas, dejando la cuenta sin fondos suficientes para cubrirlas.

**Causa:** al cobrar o abonar, `LedgerService` leía el saldo sin bloquear la cuenta. Varias peticiones simultáneas veían el mismo importe, pasaban la comprobación y escribían su propio resultado, pisándose unas a otras.

**Corrección:** la cuenta se bloquea mientras se hace el cargo o el abono (`SELECT ... FOR UPDATE`). Las operaciones sobre la misma cuenta se atienden de una en una, así que cada una ve el saldo actualizado y la que no tiene fondos se rechaza con `FONDOS_INSUFICIENTES`.

El ingreso en ventanilla (`AccountService.deposit`) usa el mismo bloqueo, porque también modifica el saldo.

**Verificación:**

- Prueba por la API: cuenta con 100 € y 4 transferencias de 30 € lanzadas a la vez. Resultado: 3 `COMPLETADA`, 1 `RECHAZADA` con `FONDOS_INSUFICIENTES`; saldo final 10 € y solo 3 movimientos.
- Test de integración: el mismo caso con 4 débitos simultáneos. Sin el bloqueo el test falla porque pasan los 4.
- Test de integración de ingresos: 5 ingresos simultáneos de 10 € sobre una cuenta vacía. Sin el bloqueo el saldo final era 10 €; con él es 50 €.

### 4.11. CAM-02 — Transferencias de más de 5.000 € pendientes de aprobación

**Estado:** implementado y verificado en el entorno local.

**Comportamiento:** las transferencias de más de 5.000 € no se ejecutan al crearse. Quedan en estado `PENDIENTE`, sin mover dinero, hasta que un gestor las aprueba o las rechaza.

**Cambios aplicados:**

- Nuevo estado `PENDIENTE` para la transferencia.
- Al crear una transferencia de más de 5.000 € se guarda como pendiente y no se llama a ms-accounts.
- Nuevos endpoints `POST /api/transfers/{id}/approve` y `POST /api/transfers/{id}/reject`, solo para el rol `GESTOR`.
- Aprobar ejecuta el mismo flujo que una transferencia normal, incluida la comprobación de destino y el reverso de BUG-06. Rechazar deja el motivo `RECHAZADA_POR_GESTOR`.
- El cambio de estado es condicional: si dos gestores actúan a la vez, solo el primero lo consigue.
- Nuevo endpoint `GET /api/transfers/pending`, solo para el rol `GESTOR`: lista paginada de las transferencias pendientes de aprobación.
- Un gestor puede consultar cualquier transferencia con `GET /api/transfers/{id}`. Los clientes siguen viendo solo las suyas.

**Verificación:**

- Tests automáticos: 17 en `TransferServiceTest`.
- Prueba por la API: transferencia de 6.000 € → `PENDIENTE` con los saldos intactos; Ana recibe 403 al intentar aprobarla; el gestor la aprueba y el dinero se mueve; otra transferencia se rechaza con `RECHAZADA_POR_GESTOR` sin mover dinero; aprobar una ya rechazada devuelve 409.

**Límite:** si el proceso se interrumpe entre el cambio de estado y la ejecución, la transferencia puede quedar `COMPLETADA` sin haber movido el dinero; se detecta por el extracto y se corrige a mano.

### 4.12. SEG-02 — Secretos en el repositorio

**Estado:** corregido y verificado mediante pruebas automatizadas y pruebas por la API.  
**Fecha de corrección:** 14 de noviembre de 2026.

**Problema detectado:**

La auditoría indicaba que había credenciales escritas en el código. Se ha revisado el repositorio completo (código, configuración, Docker, CI e historial de git) y se han encontrado dos fallos:

1. **El secreto con el que se firman los JWT estaba escrito en el código**, en la clase `SecurityConstants` del módulo `common`. ms-auth firma los tokens con él y el resto de servicios los valida con él.
2. **La contraseña de los usuarios se escribía en claro en los logs de ms-auth.** `AuthService.login` registraba el objeto `LoginRequest` completo, y el `toString()` que Java genera para un `record` incluye todos sus campos.

**Impacto:**

- Con el secreto JWT, cualquier persona con acceso al repositorio, a un JAR o a una imagen Docker podía fabricar un token válido con el usuario y el rol que quisiera, incluido `GESTOR`. Esto anulaba toda la autenticación y también los controles de titularidad de SEG-01.
- El secreto era el mismo en todos los entornos y no se podía cambiar sin recompilar.
- Los logs guardaban la contraseña de cada intento de login, también de los fallidos.

**Origen:**

Los dos fallos entraron en commits del equipo anterior, en junio de 2026:

- `a25881d` «Constantes de seguridad compartidas entre servicios»: hasta ese commit el secreto se leía de la variable de entorno `JWT_SECRET`. El commit la eliminó de la configuración y del `docker-compose.yml`, y fijó además la caducidad del token en 24 horas (antes era de 60 minutos y configurable).
- `9d62626` «Log de intentos de login»: añadió la línea de log con el `LoginRequest` completo.

**Resultado de la auditoría:**

| Hallazgo                                                           | ¿Es un secreto?                                                 | Decisión                                                       |
| ------------------------------------------------------------------ | --------------------------------------------------------------- | -------------------------------------------------------------- |
| `JWT_SECRET` en `SecurityConstants`                                | Sí                                                              | Corregido                                                      |
| Contraseña en claro en el log de login                             | Sí (dato del usuario)                                           | Corregido                                                      |
| Contraseñas de los usuarios de demostración (`DemoSeeder`, README) | No: datos de prueba del perfil `demo`, documentados a propósito | No se cambian. Se corrige la activación por defecto del perfil |
| Clave interna y secreto JWT de los tests                           | No: solo existen en los tests                                   | Sin cambios                                                    |
| Valores de `.env.example`                                          | No: marcadores de relleno                                       | Sin cambios                                                    |
| `DB_PASSWORD` e `INTERNAL_KEY`                                     | Sí, pero ya llegaban por variable de entorno                    | Correcto, sin cambios                                          |
| Fichero `.env`                                                     | Sí, pero está en `.gitignore` y nunca se ha subido              | Correcto, sin cambios                                          |
| Workflow de CI                                                     | No contiene secretos                                            | Sin cambios                                                    |

**Correcciones aplicadas:**

- Se ha eliminado `SecurityConstants`. El secreto se lee de `bytebank.jwt.secret`, que toma su valor de la variable de entorno `JWT_SECRET`, sin valor por defecto.
- `BytebankProperties` valida la configuración al arrancar: si el secreto falta o tiene menos de 32 caracteres, el servicio no arranca.
- La caducidad del token vuelve a ser configurable (`JWT_EXPIRATION_MINUTES`) y de 60 minutos por defecto. Si se configura con cero o un valor negativo, el servicio no arranca.
- `docker-compose.yml` exige `JWT_SECRET` y `.env.example` explica cómo generarlo.
- `AuthService.login` registra solo el email. Además, `LoginRequest` y `RegisterRequest` sobrescriben `toString()` para no mostrar nunca la contraseña, aunque alguien vuelva a registrar el objeto completo.
- El perfil `demo` ya no se activa por defecto en `docker-compose.yml`: hay que pedirlo en el `.env`.

**Rotación del secreto:**

El secreto antiguo debe considerarse comprometido, porque sigue en el historial de git de todas las ramas. Por eso no se ha reutilizado: cada entorno genera uno nuevo y aleatorio, que vive solo en su `.env`. Los tokens firmados con el secreto antiguo dejan de ser válidos.

No se ha reescrito el historial de git. Reescribirlo obligaría a forzar el push sobre las ramas compartidas y rompería el trabajo en curso del equipo, sin eliminar las copias que ya existen. Una vez rotado, el secreto antiguo no da acceso a nada.

**Pruebas automatizadas:**

| Clase de prueba          | Módulo    | Tests | Qué comprueba                                                                                                                                                    |
| ------------------------ | --------- | ----: | ---------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `JwtVerifierTest`        | `common`  |     5 | Acepta tokens firmados con el secreto configurado. Rechaza los firmados con cualquier otro secreto, los manipulados, los caducados y el texto que no es un token |
| `BytebankPropertiesTest` | `common`  |     8 | La configuración se rechaza si el secreto es corto, está en blanco o falta, si falta la clave interna, o si la caducidad es cero o negativa                      |
| `JwtIssuerTest`          | `ms-auth` |     4 | El token emitido se valida con el mismo secreto y se rechaza con otro. Caducidad de 60 minutos por defecto                                                       |
| `AuthDtosTest`           | `ms-auth` |     3 | El `toString()` de los DTOs no muestra la contraseña                                                                                                             |

El secreto antiguo no aparece en ningún fichero del repositorio, tampoco en los tests: para comprobar que una firma incorrecta se rechaza basta con usar cualquier secreto distinto del configurado.

**Verificación:**

- `./mvnw -B -fae verify`: `BUILD SUCCESS` en los nueve módulos, 73 tests, 0 fallos.
- Sin `JWT_SECRET`, `docker compose` no arranca y muestra «Falta JWT_SECRET en .env».
- Con `JWT_SECRET=corto`, ms-auth no arranca y muestra «el secreto JWT debe tener al menos 32 caracteres».
- Sin `JWT_EXPIRATION_MINUTES`, los servicios reciben el valor por defecto de 60 minutos.
- Sin `SPRING_PROFILES_ACTIVE`, los servicios reciben el perfil `default` y no se crean datos de demostración.
- Por la API, a través del Gateway: el login devuelve `expiresInSeconds: 3600` y `GET /api/accounts` responde con el token emitido.

**Pendiente:**

- Cada miembro del equipo debe añadir `JWT_SECRET` a su `.env` para poder arrancar el proyecto.
- En un entorno real habría que purgar los logs anteriores de ms-auth y pedir a los usuarios afectados que cambien su contraseña.
- Ver en la sección 7 el riesgo sobre el alta de gestores.
- El valor de ejemplo de `JWT_SECRET` en `.env.example` supera los 32 caracteres, así que el sistema arranca aunque no se cambie. Es cómodo para la demo, pero quien no lo sustituya funciona con un secreto público.
- Cuando la validación rechaza un secreto, Spring escribe el valor rechazado en el log de arranque. Un secreto real demasiado corto quedaría registrado.

### 4.13. Antifraude — ms-transfers no validaba las transferencias

**Estado:** corregido y verificado en el entorno local.

**Impacto:** ms-fraud tenía las reglas y el contrato, pero ms-transfers no lo llamaba. Las transferencias se ejecutaban sin comprobar el importe máximo ni las transferencias externas de importe alto. La regla de frecuencia tampoco estaba implementada.

**Causa:** `TransferService.create` solo validaba el IBAN, el importe y la titularidad; no existía ningún cliente hacia ms-fraud. El `TODO` de `TransferService` dejaba la integración pendiente.

**Corrección:**

- Nuevo `FraudClient` en ms-transfers; llama a `POST /internal/fraud/check` con la clave interna.
- Cada transferencia se valida después de comprobar la titularidad y antes de guardarse o ejecutarse.
- `BLOQUEADA`: la transferencia queda `RECHAZADA` con el código de la regla o reglas que hayan saltado. No se mueve dinero.
- `REVISAR`: queda `PENDIENTE`, igual que las de más de 5.000 €. Un gestor la aprueba o la rechaza.
- `APROBADA`: sigue el flujo normal; si supera 5.000 €, CAM-02 la deja pendiente.
- Si ms-fraud no responde, ms-transfers devuelve `503 SERVICIO_NO_DISPONIBLE` y no crea la transferencia ni mueve dinero. La decisión es de **fallo cerrado**: no se ejecuta una operación sin control antifraude y no se guarda un rechazo falso; el cliente puede reintentar.
- La aprobación del gestor no repite la validación: se hizo al crear la transferencia.
- `GET /api/fraud/checks` queda restringido al rol `GESTOR`.
- `AmountRule` bloquea a partir de 20.000 €, como dice la regla acordada; antes solo bloqueaba por encima de 20.000 (tarjeta FRA-03).

**Verificación:**

- Tests automáticos: `./mvnw -pl ms-transfers,ms-fraud -am test` → 41 tests, 0 fallos. `TransferServiceTest` pasa de 18 a 22 tests; `AmountRuleTest` tiene 3.
- API: 21.000 € → `RECHAZADA` / `IMPORTE_MAXIMO`; 20.000 € exactos → `RECHAZADA` / `IMPORTE_MAXIMO`; 1.500 € a otra entidad → `PENDIENTE` con veredicto `REVISAR` / `EXTERNA_IMPORTE_ALTO`; el gestor la aprueba y el dinero se mueve; 100 € interna → `COMPLETADA`; 6.000 € interna → `PENDIENTE` por CAM-02 y el gestor la rechaza.
- Con ms-fraud parado, una transferencia devuelve `503 SERVICIO_NO_DISPONIBLE`, no crea la transferencia y no cambia el saldo.
- `GET /api/fraud/checks` con un cliente devuelve 403; con un gestor, 200.

**Límites:**

- La regla de frecuencia se implementó después; ver 4.14.
- El listado de transferencias pendientes no muestra el motivo del fraude; el gestor puede consultarlo en `GET /api/fraud/checks?iban=...`.
- `rejection_reason` era `VARCHAR(40)`. Se amplió a `VARCHAR(255)` en 4.14, porque con la frecuencia un bloqueo puede juntar tres motivos.

### 4.14. FRA-02 — Regla de frecuencia antifraude

**Estado:** corregido y verificado en el entorno local.

**Impacto:** ms-fraud no limitaba el número de validaciones por cuenta. Una cuenta podía encadenar transferencias de importe bajo sin que saltara ninguna regla.

**Causa:** la regla `FRECUENCIA_ALTA` estaba acordada y documentada, pero no implementada.

**Corrección:**

- Nueva `FrequencyRule`: cuenta las validaciones de la cuenta de origen en los últimos 10 minutos y bloquea la sexta con `BLOQUEADA` / `FRECUENCIA_ALTA`.
- La ventana y el máximo se configuran en `application.yml` (`frequency-window: 10m`, `frequency-max: 5`).
- Se reutiliza la tabla `fraud_checks` y su índice; no hay tabla nueva.
- Se cuentan todas las validaciones, incluidas las bloqueadas o rechazadas, para que un atacante no pueda seguir probando.
- La migración `V2` de ms-transfers amplía `rejection_reason` a `VARCHAR(255)`: con la nueva regla, un bloqueo puede juntar tres motivos y superar los 40 caracteres.

**Verificación:**

- `FrequencyRuleTest`: 4 validaciones previas → pasa; 5 → `BLOQUEADA`; la consulta usa `now - 10m`.
- `./mvnw -pl ms-fraud,ms-transfers -am test` → 44 tests, 0 fallos.
- API: 5 transferencias de 1 € → `COMPLETADA`; la 6ª → `RECHAZADA` / `FRECUENCIA_ALTA`, sin mover dinero. Una externa de 21.000 € tras 6 checks → `RECHAZADA` con `IMPORTE_MAXIMO`, `EXTERNA_IMPORTE_ALTO` y `FRECUENCIA_ALTA`; la migración `V2` guardó el motivo de 51 caracteres sin error.

**Límites:**

- La frecuencia se cuenta sobre `fraud_checks`, así que un intento fallido también acerca el bloqueo.
- Dos peticiones simultáneas pueden leer el mismo historial y dejar pasar como mucho una operación extra; no hay bloqueo por cuenta.

### 4.15. KAF-01 — Los clientes no pueden consultar sus avisos

**Estado:** corregido y verificado en el entorno local.

**Impacto:** ms-notifications recibía los movimientos de Kafka y guardaba un aviso por titular, pero `GET /api/notifications` respondía 501. El cliente no tenía forma de ver sus avisos.

**Causa:** el endpoint estaba sin implementar (`TODO` en `NotificationController`), y el repositorio no tenía ninguna consulta por usuario.

**Corrección:**

- `GET /api/notifications` devuelve los avisos del usuario autenticado, los más recientes primero, con el formato de `docs/contratos/ms-notifications.md`.
- El usuario se obtiene del token, nunca de un parámetro, de modo que nadie puede consultar los avisos de otro.
- `page` desde 0 y `size` entre 1 y 100; fuera de rango responde 400.
- El `Dockerfile` incluye `jdk.localedata` en el JRE recortado, limitado al español y al inglés. Sin ese módulo, Java no tenía los formatos del español y los importes de los avisos salían en formato inglés (`10.00 €`) en lugar de `10,00 €`. El fallo solo aparecía en Docker: los tests usan el JDK completo.

**Verificación:**

- API: tras una transferencia de 10 € de Ana a Luis, Ana ve solo su `TRANSFERENCIA_EMITIDA` y Luis solo su `TRANSFERENCIA_RECIBIDA`; `size=101` y `page=-1` dan 400; sin token, 401.
- Mensaje con el formato del contrato: «Has enviado 1.234,50 € desde tu cuenta ···0001 (Prueba formato). Saldo: 1.265,50 €».
- Una transferencia rechazada por fondos insuficientes no genera aviso.
- Tests automáticos: en `NotificationServiceTest`, un test comprueba que se piden los avisos del usuario y en orden descendente; `NotificationControllerTest` cubre el endpoint. ms-notifications: 12 tests, 0 fallos. `./mvnw clean verify`: 110 tests, 0 fallos.

**Pendiente:** nada impide guardar dos veces el aviso de un mismo movimiento si Kafka reentrega el mensaje, aunque el contrato lo prohíbe. Resuelto en KAF-03 (ver 4.17).

### 4.16. TAR-01 — Pagar con tarjeta de débito

**Estado:** implementado y verificado en el entorno local.

**Impacto:** la emisión de tarjetas ya estaba hecha (CAM-01), pero `POST /api/cards/{id}/payments` respondía 501. Los clientes no podían pagar con sus tarjetas.

**Corrección:**

- El pago comprueba que la tarjeta es del usuario, que está activa y el límite diario, y después hace el cargo en ms-accounts con un movimiento `PAGO_TARJETA`.
- Los rechazos (`TARJETA_BLOQUEADA`, `LIMITE_DIARIO_SUPERADO`, `FONDOS_INSUFICIENTES`, `CUENTA_NO_OPERATIVA`) se guardan y se devuelven con 201, para que queden en el historial.
- El límite diario (600 € por defecto) cuenta solo los pagos aprobados desde las 00:00 de Madrid.
- La fila de la tarjeta se bloquea durante el pago, para que dos pagos simultáneos no superen el límite.
- Cada pago lleva una referencia única `CARD-<uuid>` (migración `V2` de ms-cards). Si falla el guardado después del cargo, se compensa con un abono.
- Si ms-accounts no responde, se devuelve 503 y no se guarda el pago.

**Verificación:**

- `./mvnw clean verify`: 131 tests, 0 fallos. ms-cards pasa de 4 a 25 tests, incluidos los de integración con PostgreSQL.
- Prueba por la API: 42,50 € y 557,50 € aprobados, con el día justo en 600 €; 0,01 € rechazado por `LIMITE_DIARIO_SUPERADO`; 200 € con 150,75 € de saldo, `FONDOS_INSUFICIENTES`; tarjeta bloqueada, `TARJETA_BLOQUEADA`; importe negativo, 400. El historial guarda los rechazados y solo los pagos aprobados generan aviso.

**Límites:**

- Si ms-accounts hace el cargo pero su respuesta no llega, el cliente recibe 503 y el pago no queda registrado. Tampoco se compensa si el proceso cae entre el cargo y el guardado.
- La tarjeta queda bloqueada durante la llamada a ms-accounts: si tarda, los pagos de esa tarjeta esperan.
- No hay idempotencia: si el cliente reintenta, es un pago nuevo.

### 4.17. KAF-03 — Avisos repetidos tras reiniciar ms-notifications

**Estado:** corregido y verificado en el entorno local.

**Impacto:** después de reiniciar ms-notifications, varios clientes volvieron a recibir avisos de movimientos antiguos. El contrato dice que un cliente no recibe dos veces el aviso del mismo movimiento.

**Causa:**

- Kafka entrega cada evento **al menos una vez**. Spring Kafka confirma los offsets por lotes, después de procesarlos; si el servicio se para con mensajes ya procesados pero sin confirmar, al arrancar se vuelven a entregar. Con `auto-offset-reset: earliest`, si el grupo `ms-notifications` pierde sus offsets (caducan o cambia el `group-id`), se vuelve a leer el topic desde el principio.
- ms-notifications guardaba un aviso por cada entrega sin comprobar si ya existía, y la tabla `notifications` no tenía ninguna restricción que lo impidiera.

**Corrección:**

- `NotificationService.notifyHolders` comprueba, por cada titular, si ya tiene el aviso de ese movimiento; si lo tiene, ignora la reentrega. Si un evento llega con parte de los avisos ya guardados, solo se añaden los que faltan.
- Migración `V2__notifications_unique_movement_user.sql`: restricción única `(movement_id, user_id)`. Es la garantía final: si dos entregas del mismo evento se procesan a la vez, una falla, su transacción se deshace entera y, al reintentarla, encuentra los avisos ya guardados.
- La misma migración borra los avisos duplicados que ya existían, conservando el más antiguo de cada movimiento y titular.
- No se cambia la configuración del consumidor: las reentregas son parte del funcionamiento normal de Kafka y es el consumidor el que debe ser idempotente. Esto también es necesario para KAF-02 (outbox), que puede publicar un evento más de una vez.

**Verificación:**

- `NotificationDeduplicationTest` (PostgreSQL real con Testcontainers): el mismo evento entregado dos veces genera un solo aviso por titular; una reentrega con parte de los avisos guardados solo añade los que faltan; dos movimientos distintos generan dos avisos; la base de datos rechaza un aviso duplicado. Antes de la corrección fallaban 3 de los 4 tests.
- `NotificationServiceTest`: el titular que ya tiene el aviso no recibe otro.
- Migración probada sobre una base con duplicados: de 5 avisos quedan 3 (el más antiguo de cada par) y un duplicado posterior se rechaza.
- ms-notifications: 17 tests, 0 fallos. `./mvnw clean verify`: 115 tests, 0 fallos.
- Entorno (Docker Compose): ms-notifications aplica la migración V2 al arrancar. Con el servicio parado, se reinician los offsets del grupo `ms-notifications` al principio del topic (`--reset-offsets --to-earliest`) y se vuelve a arrancar: se releen todos los movimientos, el número de avisos no cambia (4) y no hay ningún par movimiento-titular repetido. El log muestra «ya avisado … se ignora la reentrega» por cada evento releído.

### 4.18. KAF-02 — Avisos de operaciones que no han llegado a hacerse

**Estado:** corregido y verificado en el entorno local.

**Impacto:** un cliente podía recibir el aviso de un movimiento que no existe. ms-accounts enviaba el evento a Kafka dentro de la transacción, antes del commit: si la operación se deshacía después, el aviso ya había salido. Con transferencias atómicas (cargo y abono en una sola transacción) habría pasado siempre que fallara el abono. Además, si Kafka fallaba, el aviso se perdía sin dejar rastro.

**Causa:** `MovementPublisher` llamaba a `kafka.send` desde `LedgerService.debit`, `LedgerService.credit` y `AccountService.deposit`, todos `@Transactional`. El envío no esperaba la confirmación de Kafka, y un error al serializar solo se registraba en el log.

**Corrección (patrón transactional outbox):**

- Migración `V6__outbox_events.sql`: tabla `outbox_events` con índices parciales para los pendientes y para la limpieza.
- `MovementPublisher` ya no usa Kafka: guarda el evento en el outbox en la misma transacción que el movimiento (`Propagation.MANDATORY`, así que no se puede llamar sin transacción). Si la operación se deshace, el evento desaparece con ella. Un error al serializar deshace la operación.
- `OutboxRelay` publica los pendientes cada segundo, en orden, con `FOR UPDATE SKIP LOCKED`. Solo marca un evento como publicado cuando Kafka lo confirma. Si un envío falla, por cualquier motivo, se queda pendiente con `attempts` y `last_error`, la vuelta se para para no alterar el orden, y se reintenta en la siguiente.
- `OutboxCleaner` borra cada día a las 04:00 (Europe/Madrid) los eventos publicados hace más de 3 días; los pendientes no se borran nunca. El outbox es una tabla temporal, no un histórico: 3 días bastan para investigar una incidencia de fin de semana, y el histórico de movimientos y de avisos se conserva en las tablas `movements` (ms-accounts) y `notifications` (ms-notifications).
- Productor de Kafka con `acks: all`, idempotencia y tiempos de espera acotados (`max.block.ms`, `delivery.timeout.ms`). La espera del relay (12 s) es mayor que la del productor (10 s), para que el fallo registrado sea siempre el definitivo y no quede en el productor una copia del evento que se envíe además del reintento.
- La entrega pasa a ser **al menos una vez**: si ms-accounts cae entre la confirmación de Kafka y el commit, el evento se reenvía. ms-notifications ignora los repetidos (KAF-03, 4.17). Queda documentado en el contrato.
- ms-cards (pagos con tarjeta) y ms-transfers no cambian: sus movimientos pasan por `LedgerService` y usan el outbox.

**Verificación:**

- `MovementEventPublicationTest` (PostgreSQL y Kafka reales): un movimiento deshecho no deja evento ni mensaje; una transferencia atómica simulada que falla en el abono no publica el cargo; un movimiento confirmado se guarda en el outbox y llega a Kafka; llamar al publicador sin transacción lanza `IllegalTransactionStateException`. Antes de la corrección, los dos primeros fallaban mostrando el aviso fantasma.
- `OutboxRelayTest`: Kafka con error, sin respuesta o con un fallo inmediato en `send()` deja el evento pendiente y registra el intento; el evento siguiente no se adelanta; en la siguiente vuelta se publica.
- `OutboxCleanerTest`: se borra el publicado hace 4 días; se conservan el publicado hace 2 días y el pendiente de hace 30.
- ms-accounts: 55 tests, 0 fallos. `./mvnw clean verify`: 148 tests, 0 fallos.
- Entorno (Docker Compose):
  - Con Kafka funcionando: una transferencia de 11 € genera dos eventos (cargo y abono), que se publican en menos de 2 s (el primer envío tras el arranque; después, en torno a 1 s). Ana recibe «Has enviado 11,00 €» y Luis «Has recibido 11,00 €».
  - Con Kafka parado: la transferencia responde `COMPLETADA` en menos de 1 s. Sus eventos quedan pendientes: el primero con los intentos fallidos y el error registrados, el segundo sin intentar para no adelantarlo. Al arrancar Kafka, los dos se publican solos y en orden, el error se limpia y cada aviso llega una sola vez.
  - Las pruebas con Kafka parado revelaron que un envío puede fallar de tres formas según el estado del productor: en la respuesta de Kafka (`TimeoutException`), o de forma inmediata en `send()` si no tiene los metadatos del topic (`Topic ... not present in metadata`) o si ni siquiera puede resolver el broker (`ConfigException: No resolvable bootstrap urls`). En los dos últimos casos el relay no registraba el intento: la transacción se deshacía y `attempts` y `last_error` quedaban vacíos. El evento no se perdía, pero el fallo no quedaba registrado. Se corrigió capturando cualquier excepción del envío y se añadió el test `kafkaThatFailsImmediatelyCountsAsAFailure`. Repetida la prueba, el evento acumuló 10 intentos en 15 s con el error registrado y se publicó al volver Kafka.

**Pendiente:**

- El caso del rollback no se puede provocar desde la API; está cubierto por los tests de integración.
- La espera de 12 s del relay solo actúa cuando Kafka acepta el envío y no responde. En el entorno no se ha llegado a provocar; está cubierta por `OutboxRelayTest`.
- Con Kafka caído y un fallo inmediato, el relay reintenta cada segundo y escribe un aviso en el log por intento. Si las caídas largas son un problema, convendría un backoff exponencial según `attempts`.

### 4.19. SOR-01 — Comisión en transferencias a otras entidades

**Estado:** corregido y verificado en el entorno local.

**Impacto:** las transferencias a otras entidades no cobraban comisión. Ahora se aplica un 0,5 % del importe con un mínimo de 1,00 €, redondeado a céntimos. Las transferencias entre cuentas de ByteBank siguen sin comisión. La comisión se cobra en la cuenta de origen como movimiento aparte de tipo `COMISION` y la respuesta de la transferencia incluye el campo `fee`. Si el saldo no cubre el importe más la comisión, la transferencia se rechaza sin mover dinero.

**Corrección:**

- `Transfer.fee()` calcula la comisión con el importe y el IBAN de destino: 0,5 %, mínimo 1,00 €, redondeo `HALF_UP` a dos decimales. Si el destino es de ByteBank, devuelve cero.
- `TransferResponse` incluye `fee`. Las transferencias rechazadas devuelven `0.00`.
- `POST /internal/accounts/debit` acepta un `fee` opcional; si falta, vale cero, así que ms-cards no cambia. `LedgerService.debit` comprueba el saldo contra importe más comisión y, si hay saldo, crea el movimiento `COMISION` con referencia `TRF-<id>-COM` en la misma transacción y en el mismo outbox.
- No hay migración en ms-transfers: la comisión se recalcula y el cargo real queda registrado en ms-accounts.
- Las reglas antifraude y el umbral de 5.000 € siguen mirando solo el importe de la transferencia.
- En las pendientes, la comisión se cobra al aprobarlas.

**Verificación:**

- Tests: `./mvnw -pl ms-transfers -am test` → 44, 0 fallos (`TransferServiceTest` 28). `./mvnw -pl ms-accounts -am test` → 61 en ms-accounts, 0 fallos (`LedgerServiceIntegrationTest` 8, `LedgerDtosTest` 5). `./mvnw clean verify` → 160 tests, 0 fallos.
- API con el entorno local:
  - Externa de 100 € → `fee 1.00`; movimientos `-100.00` (`TRF-1`) y `-1.00` (`TRF-1-COM`), saldo final 2.399,00 €.
  - Externa de 900 € → `fee 4.50`.
  - Interna de 100 € → `fee 0.00`, sin movimiento de comisión.
  - Externa de 99 € con saldo 99,75 € → `RECHAZADA` / `FONDOS_INSUFICIENTES`, `fee 0.00` y sin movimientos.
  - Pendiente externa de 6.000 € → `fee 30.00`; al aprobarla se cobran 6.030 € y el saldo queda en 364,50 €.

**Límites:**

- **Deuda técnica asumida:** la comisión se recalcula con la tarifa vigente y no se guarda en la transferencia. Si la tarifa cambiara, una transferencia antigua mostraría una comisión distinta de la cobrada, y una pendiente se cobraría con la tarifa nueva en vez de con la que se mostró al crearla.
- **Solución si llega el caso:** guardar `fee` en `transfers` (columna `DECIMAL(19,2)` y migración `V3`), fijarla al crear la transferencia y usar ese valor tanto al ejecutarla como al responder.
- El aviso de la comisión usa el texto genérico de ms-notifications, que no tiene un caso `COMISION`; el concepto «Comisión por transferencia» deja claro qué es.

### 4.20. SEG-03 — Perfil demo y alta del primer gestor

**Estado:** corregido y verificado en el entorno local.

**Impacto:**

- La única forma de crear un `GESTOR` era el seeder del perfil `demo`, con la contraseña publicada en el README. Si un entorno con datos reales arrancaba una sola vez con `demo` sobre una base vacía, quedaba un gestor con contraseña conocida que seguía existiendo aunque después se quitara el perfil. Ese gestor puede ver todas las cuentas y sus movimientos, bloquear cuentas y hacer ingresos.
- El seeder de ms-accounts daba las cuentas de demo (3.450 € en total) a los ids 2 y 3 sin comprobar quiénes eran, y solo miraba su propia base. Con `demo` en ms-accounts y la tabla de cuentas vacía, los usuarios 2 y 3 de un entorno real habrían recibido dinero que nunca entró en el banco.

**Corrección:**

*Primer gestor (ms-auth):*

- `GestorBootstrap` se ejecuta en cada arranque, con cualquier perfil, y lee `BOOTSTRAP_GESTOR_EMAIL` y `BOOTSTRAP_GESTOR_PASSWORD`, que no tienen valor por defecto. Sin ellas no hace nada.
- Si solo llega una, o si la contraseña tiene menos de 12 caracteres o más de 72 bytes (lo que BCrypt tiene en cuenta), ms-auth no arranca. La configuración se valida antes de consultar la base de datos, así que un error de configuración falla en cualquier arranque.
- Si ya existe un gestor, no crea otro y avisa de que hay que quitar las variables. Si el email ya es de otro usuario, ms-auth no arranca: no convierte a un cliente en gestor. El email se normaliza igual que en el registro.
- La contraseña no aparece nunca en el log ni en los mensajes de error; solo se registra el email.
- Se ejecuta después del seeder de demo (`@Order`): con `demo`, el gestor de la demo ya existe y aquí solo se avisa.
- `docker-compose.yml` pasa las dos variables solo a ms-auth, no al resto de servicios.

*Cuentas de la demo:*

- ms-auth tiene `GET /internal/demo/holders`, que solo existe con el perfil `demo` (sin él responde 404). Devuelve los ids de Ana y Luis si `auth_db` solo tiene usuarios de la demo, `409 BASE_NO_DEMO` si hay algún otro usuario y `503 DEMO_NO_PREPARADA` si todavía falta Ana o Luis.
- El seeder de ms-accounts ya no usa ids fijos: pide los titulares a ese endpoint.
  - Con un 409 o un 404 no crea nada y lo registra como error, sin reintentar.
  - Con un 503 o sin conexión, reintenta con esperas de 2, 4, 8 y 15 s, durante 3 minutos como máximo (`bytebank.demo.seed-timeout`). Si se agota el plazo, lo registra como error y explica que hay que reiniciar ms-accounts.
- El seeder se ejecuta en un hilo aparte para no bloquear el arranque: en el equipo de pruebas, cada servicio tarda entre 75 y 97 s en arrancar cuando se levantan todos a la vez.
- Las cuentas se crean en `DemoAccounts`, una clase aparte con `@Transactional`. Si el hilo llamara a un método de la propia clase, la llamada no pasaría por el proxy de Spring y no habría transacción.
- El 503 corrige una carrera encontrada al diseñar el seeder. Spring Boot abre el puerto antes de ejecutar los `ApplicationRunner`, así que ms-auth puede responder antes de que su seeder haya creado a Ana y Luis. Si en ese caso respondiera 409, ms-accounts entendería que hay usuarios reales y la demo se quedaría sin cuentas.

*Entornos con datos reales:*

- Nuevo perfil `prod` para cualquier entorno con datos reales. `.env.example` explica qué perfil usar en cada entorno, y el README cómo crear el primer gestor.
- `DemoProfileGuard` (`common`) es un bean que solo existe con `demo` y `prod` a la vez y que, al crearse, impide el arranque. Se carga desde `CommonAutoConfiguration`, así que protege los seis servicios de negocio. ms-gateway no depende de `common` ni tiene perfil `demo`.
- `DemoUsersInProductionCheck` (ms-auth): con `prod`, si existe alguno de los tres usuarios de la demo, ms-auth no arranca. El mensaje solo da sus emails e ids y remite al procedimiento de limpieza de esta sección. No borra nada: borrar datos en producción lo decide una persona.
- Las dos comprobaciones se hacen al crear los beans (constructor y `@PostConstruct`), antes de que Tomcat acepte peticiones. Con un `ApplicationRunner` habría unos instantes en que se podría iniciar sesión con la contraseña pública.

**Procedimiento de limpieza** (si ms-auth no arranca con `prod` porque hay usuarios de la demo):

La limpieza es manual y la decide una persona. El código solo detecta el problema y no borra nada.

1. **Parar los servicios y hacer una copia** de todas las bases de datos antes de tocar nada.
2. **Localizar los datos de la demo.** El mensaje de error da los ids de los usuarios. Con ellos se obtienen sus cuentas (en ms-accounts, por sus titulares) y los IBAN de esas cuentas, que es lo que usan el resto de servicios.
3. **Comprobar que el dinero de la demo no ha llegado a clientes reales.** Hay que revisar si alguna cuenta de la demo es compartida con un cliente real, si hay transferencias completadas entre una cuenta de la demo y una real, y si se han aprobado pagos con tarjeta desde esas cuentas. **Si ocurre cualquiera de las tres cosas, no se borra nada y se escala:** se ha movido dinero que nunca entró en el banco, y deshacerlo es una decisión contable, no técnica.
4. **Si no ha salido dinero, borrar los datos de la demo** en todos los servicios (tabla siguiente). Todo lo que haya quedado en el outbox de esas cuentas también se borra, incluidos los eventos pendientes: si no, al arrancar se publicarían y volverían a crear avisos para usuarios que ya no existen.
5. **Arrancar con `prod`.** Si queda algún usuario de la demo, ms-auth no arranca y lo vuelve a indicar.

| Servicio         | Datos de la demo que hay que revisar o borrar                                   |
| ---------------- | ------------------------------------------------------------------------------- |
| ms-auth          | Los usuarios de la demo                                                         |
| ms-accounts      | Sus cuentas, titulares y movimientos, y los eventos del outbox de esas cuentas  |
| ms-transfers     | Las transferencias en las que aparece una cuenta de la demo                     |
| ms-cards         | Las tarjetas de esas cuentas y sus pagos                                        |
| ms-fraud         | Las comprobaciones antifraude de esos usuarios o cuentas                        |
| ms-notifications | Los avisos de esos usuarios o cuentas                                           |

Las tablas y columnas de cada servicio están en sus migraciones de Flyway (`src/main/resources/db/migration`).

**Verificación:**

- `GestorBootstrapTest` (8 tests): sin variables no hace nada; crea el gestor con el email normalizado y la contraseña cifrada; no crea un segundo gestor; no arranca con solo el email, con solo la contraseña, con una contraseña corta, con una de más de 72 bytes ni con un email de otro usuario. La contraseña no aparece en el log ni en los mensajes.
- `DemoHoldersControllerTest` (5 tests, con PostgreSQL real): devuelve los ids de Ana y Luis, que no son 2 y 3 porque el test vuelve a sembrar la demo en cada caso; 409 con un usuario real; 503 con la tabla vacía y con Luis borrado; 401 sin clave interna. `DemoHoldersWithoutDemoProfileTest`: 404 sin el perfil `demo`.
- `DemoSeederTest` (7 tests, con un reloj falso que recorre los 3 minutos sin esperarlos): crea las cuentas con los ids que da ms-auth; no pregunta si ya hay cuentas; se para al primer intento con 409 o 404; reintenta con 503 y sin conexión; respeta el plazo y las esperas; y `run()` no espera a ms-auth.
- `DemoProfileGuardTest` (4 tests): no arranca con `demo` y `prod`; arranca con uno solo; está en el `@Import` de `CommonAutoConfiguration`. `DemoUsersInProductionCheckTest` (2 tests): el mensaje solo incluye emails e ids, no nombres ni el hash de la contraseña.
- ms-auth: 23 tests, ms-accounts: 68 tests, 0 fallos. `./mvnw clean verify`: 187 tests, 0 fallos.
- Entorno (Docker Compose):
  - Demo desde cero: las cuentas se crean al primer intento para los ids reales de Ana (2) y Luis (3). ms-auth había terminado de arrancar antes que ms-accounts.
  - ms-auth parado mientras arranca ms-accounts: 7 reintentos con esperas de 2, 4, 8 y 15 s, ms-accounts responde `UP` mientras tanto, y las cuentas se crean en cuanto vuelve ms-auth.
  - Un cliente real registrado en `auth_db` y la tabla de cuentas vacía: un solo intento, `409 BASE_NO_DEMO` y ninguna cuenta creada.
  - `demo,prod`: ms-cards no arranca por `DemoProfileGuard`. ms-auth tampoco arranca, pero lo para antes la comprobación de usuarios de demo, porque Spring crea los beans del servicio antes que los de `common`.
  - `prod` sobre la base de la demo: ms-auth no arranca, el mensaje da `gestor@bytebank.test (id 1), ana@bytebank.test (id 2), luis@bytebank.test (id 3)` y el puerto 8081 no llega a abrirse.
  - `prod` sobre una base vacía con `BOOTSTRAP_GESTOR_*`: se crea la gestora, inicia sesión con rol `GESTOR` y no se siembra ninguna cuenta. En el arranque siguiente con las mismas variables avisa de que ya existe un gestor. La contraseña aparece 0 veces en los logs de todos los servicios.
- Dos observaciones de las pruebas, ajenas a esta tarjeta: si se para y se vuelve a arrancar un servicio, ms-gateway puede seguir usando su IP antigua (ver la sección 7); y desde Git Bash en Windows, un `curl` con acentos en el JSON puede llegar mal codificado y devolver `400 DATOS_NO_VALIDOS`.

**Límites:**

- No existe ningún endpoint para cambiar la contraseña ni para dar de alta más gestores. El primer gestor conserva la contraseña de la variable de entorno, y mientras siga en el `.env` se puede ver en la configuración del contenedor (`docker inspect`). Por eso hay que quitarla después del primer arranque.
- Los movimientos no guardan quién los hizo. Si el gestor de la demo hizo ingresos en cuentas reales, bloqueó cuentas o aprobó transferencias, el procedimiento de limpieza no lo detecta: hay que revisar las operaciones de gestor del periodo en que el entorno tuvo el perfil `demo`.
- El procedimiento de limpieza no se ha ejecutado contra una base con datos reales mezclados. La lista de datos afectados se ha revisado contra las migraciones de cada servicio.

## 5. Funcionalidades incompletas identificadas

El README, cuyo estado del proyecto está fechado en junio de 2026, reconoce las siguientes limitaciones:

| Área             | Estado descrito en el README                                                     |
| ---------------- | -------------------------------------------------------------------------------- |
| ms-cards         | Permite listar y bloquear tarjetas, pero emitir y pagar devuelve HTTP 501.       |
| ms-fraud         | Integrado en `ms-transfers` con las reglas de importe, externa y frecuencia.     |
| Antifraude       | Reglas de importe máximo, transferencia externa y frecuencia implementadas.      |
| ms-notifications | Consume eventos Kafka y guarda avisos, pero falta el endpoint para consultarlos. |
| Extractos        | Se han recibido quejas sobre movimientos que no aparecen correctamente.          |

Estas limitaciones están documentadas por el equipo anterior, pero deben verificarse con pruebas durante el desarrollo.

Las quejas sobre el extracto se corresponden con dos defectos ya corregidos: ver 4.8 y 4.9.

**Actualización:** ms-cards ya permite emitir tarjetas (CAM-01) y pagar con ellas (TAR-01, 4.16), y ms-notifications tiene el endpoint para consultar los avisos (KAF-01, 4.15).

## 6. Pruebas y cobertura inicial

Se han localizado clases de prueba en los siguientes módulos:

- `common`: pruebas de validación de IBAN.
- `ms-accounts`: pruebas de integración y de movimientos.
- `ms-fraud`: pruebas de reglas antifraude.
- `ms-notifications`: pruebas relacionadas con los mensajes de notificación.
- `ms-transfers`: pruebas del servicio de transferencias.

En la inspección inicial no se han localizado clases de prueba en `ms-auth`, `ms-gateway` ni `ms-cards`.

La existencia de pruebas no garantiza que cubran todos los escenarios de negocio. Es necesario revisar su contenido, ejecución y resultados antes de valorar la cobertura real.

### 6.1. Resultados de las pruebas automatizadas

**Nota:** las cifras de esta sección corresponden a la ejecución inicial del 10/11/2026 y no incluyen las correcciones posteriores. Para el estado actual, ver las secciones 4.x.

**Fecha de ejecución:** 10/11/2026.

Se ejecutaron las pruebas mediante Maven. La primera ejecución general se detuvo al encontrar errores en `ms-accounts`. Posteriormente, se ejecutaron los módulos restantes utilizando `-am` para incluir las dependencias del reactor Maven.

| Módulo             | Resultado | Observaciones                                                                      |
| ------------------ | --------- | ---------------------------------------------------------------------------------- |
| `common`           | Correcto  | 3 tests de `IbanTest` superados                                                    |
| `ms-gateway`       | Correcto  | Ejecución completada en la ejecución general                                       |
| `ms-auth`          | Correcto  | Ejecución completada en la ejecución general                                       |
| `ms-accounts`      | Error     | 4 errores en `LedgerServiceIntegrationTest` por el fallo de Flyway V3              |
| `ms-transfers`     | Correcto  | 4 tests de `TransferServiceTest` superados                                         |
| `ms-cards`         | Correcto  | Ejecución completada; no se han localizado clases de test en el inventario inicial |
| `ms-fraud`         | Correcto  | 2 tests de `AmountRuleTest` superados                                              |
| `ms-notifications` | Correcto  | 2 tests de `NotificationMessagesTest` superados                                    |

La ejecución de los módulos `common`, `ms-transfers`, `ms-cards`, `ms-fraud` y `ms-notifications` finalizó con `BUILD SUCCESS`.

La primera ejecución general terminó con `BUILD FAILURE` al fallar `ms-accounts`, por lo que Maven no llegó a ejecutar los módulos posteriores.

### 6.2. Test omitido por incidencia de zona horaria

En `MovementServiceTest` de `ms-accounts` se ha identificado un test deshabilitado:

- **Test:** `filterByDateIncludesLastDay`.
- **Motivo indicado en el código:** «Falla en el portátil de Iván por la zona horaria, revisar».
- **Estado:** resuelto. Ver 4.9.

El origen estaba en la implementación: el fin del rango era exclusivo y se calculaba en UTC. El test se ha sustituido por `rangeIncludesLastDayInMadridTime`, que se ejecuta y pasa sin depender de la zona horaria de la máquina.

### 6.3. Resumen de resultados

En los informes de pruebas revisados se han identificado:

- 12 tests superados con resultados detallados.
- 4 errores en pruebas de integración de `ms-accounts`.
- 1 test omitido por una incidencia de zona horaria.

Además, `ms-gateway` y `ms-auth` completaron correctamente la ejecución general, aunque no se dispone en este inventario de un recuento individual de sus tests.

**Conclusión:** el principal bloqueo de las pruebas automatizadas se encuentra en `ms-accounts`, debido a la migración V3. También queda pendiente revisar el test de filtrado por fecha que está deshabilitado.

**Actualización:** la migración V3 está corregida (4.1) y el test de filtrado por fecha se ha sustituido por uno que ya no se omite (4.9).

## 7. Riesgos y aspectos pendientes de investigación

Los siguientes puntos requieren una revisión específica:

- **Seguridad:** las incidencias SEG-01 (acceso a cuentas, movimientos y transferencias de otros usuarios) y SEG-02 (secretos en el repositorio) están corregidas y verificadas.
- **Credenciales:** revisado en SEG-02 (4.12). No quedan secretos en el código ni en configuraciones versionadas. Sería conveniente añadir a la CI un escáner de secretos (por ejemplo, gitleaks) para evitar que vuelva a ocurrir.
- **Alta de gestores:** resuelto (SEG-03, 4.20). El primer gestor de un entorno real se crea con `BOOTSTRAP_GESTOR_*`, sin contraseña por defecto. Los servicios no arrancan con `demo` y `prod` a la vez, y ms-auth no arranca con `prod` si hay usuarios de la demo. Queda pendiente un endpoint para cambiar la contraseña y otro para dar de alta más gestores.
- **JWT con secreto compartido:** los tokens se firman con HS256, así que todos los servicios conocen el secreto y cualquiera de ellos podría emitir tokens. Una alternativa es RS256: ms-auth firma con una clave privada y el resto valida con la pública.
- **Transferencias:** los importes negativos (BUG-01, 4.4) y el abono fallido que perdía el dinero (BUG-06, 4.7) están corregidos. Queda verificar los redondeos.
- **Concurrencia:** los cargos, abonos e ingresos simultáneos sobre una misma cuenta están resueltos (CON-01, 4.10). Queda comprobar el resto de operaciones.
- **Antifraude:** la integración con `ms-fraud` está hecha y verificada (4.13) y la regla de frecuencia está implementada (4.14). El comportamiento ante un fallo de ms-fraud está decidido: `503` y no se ejecuta la transferencia.
- **Kafka:** resuelto. Los eventos se publican después del commit mediante un outbox (KAF-02, 4.18) y ms-notifications ignora los repetidos (KAF-03, 4.17). Queda pendiente vigilar el tamaño de `outbox_events` y los eventos con muchos intentos fallidos si se añade monitorización, y valorar un backoff en los reintentos del relay.
- **Notificaciones:** consulta de avisos (KAF-01, 4.15) e idempotencia (KAF-03, 4.17) resueltas y verificadas.
- **Docker:** revisar las dependencias de arranque, la configuración de los servicios y los tamaños de las imágenes. Con los siete servicios arrancando a la vez, cada uno tarda entre 75 y 97 s en el equipo de pruebas (SEG-03, 4.20).
- **Gateway y reinicios de servicios:** en las pruebas de SEG-03 se paró ms-auth y, al volver, Docker le asignó otra IP. ms-gateway siguió enviando las peticiones a la IP antigua (`Connection refused: ms-auth/172.20.0.7:8081`) y respondía 500 hasta que se reinició el propio gateway. Probablemente es la caché de DNS o el pool de conexiones de Reactor Netty; no se ha investigado. Mientras tanto, después de recrear un servicio hay que reiniciar ms-gateway.
- **CI:** verificar la ejecución del nuevo workflow en GitHub Actions y la obligatoriedad de sus comprobaciones antes de integrar cambios.
- **Zonas horarias:** la incidencia del test `filterByDateIncludesLastDay` está resuelta (4.9). Queda comprobar que el resto de servicios trate las fechas con la misma zona (`bytebank.zone`).

Estos puntos son líneas de investigación y no deben considerarse defectos confirmados hasta reproducirlos o verificar su causa.

## 8. Próximas actuaciones

1. Resolver los problemas que impiden el arranque completo del entorno.
2. Verificar las comunicaciones entre los microservicios y completar las pruebas funcionales del Gateway.
3. Verificar la nueva configuración de integración continua en GitHub Actions y establecer los controles de integración correspondientes.
4. Ejecutar nuevamente las pruebas existentes y analizar sus resultados.
5. ~~Investigar el test omitido por la incidencia de zona horaria.~~ Resuelto (4.9).
6. Continuar investigando y documentando las incidencias funcionales y de seguridad pendientes.
7. Implementar las funcionalidades pendientes según las tarjetas del backlog.
8. Actualizar este documento con las correcciones realizadas y las pruebas que acrediten su funcionamiento.

---

**Nota:** este documento representa el diagnóstico inicial del equipo. Los hallazgos se irán actualizando conforme avance la investigación y se completen las tareas.

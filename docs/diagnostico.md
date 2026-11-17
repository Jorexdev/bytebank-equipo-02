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

**Pendiente:** repetir la ejecución de `LedgerServiceIntegrationTest` y confirmar el resultado en CI.

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

El ingreso en ventanilla (AccountService.deposit) usa el mismo bloqueo, porque también modifica el saldo.

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

| Hallazgo | ¿Es un secreto? | Decisión |
| --- | --- | --- |
| `JWT_SECRET` en `SecurityConstants` | Sí | Corregido |
| Contraseña en claro en el log de login | Sí (dato del usuario) | Corregido |
| Contraseñas de los usuarios de demostración (`DemoSeeder`, README) | No: datos de prueba del perfil `demo`, documentados a propósito | No se cambian. Se corrige la activación por defecto del perfil |
| Clave interna y secreto JWT de los tests | No: solo existen en los tests | Sin cambios |
| Valores de `.env.example` | No: marcadores de relleno | Sin cambios |
| `DB_PASSWORD` e `INTERNAL_KEY` | Sí, pero ya llegaban por variable de entorno | Correcto, sin cambios |
| Fichero `.env` | Sí, pero está en `.gitignore` y nunca se ha subido | Correcto, sin cambios |
| Workflow de CI | No contiene secretos | Sin cambios |

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

| Clase de prueba | Módulo | Tests | Qué comprueba |
| --- | --- | ---: | --- |
| `JwtVerifierTest` | `common` | 5 | Acepta tokens firmados con el secreto configurado. Rechaza los firmados con cualquier otro secreto, los manipulados, los caducados y el texto que no es un token |
| `BytebankPropertiesTest` | `common` | 8 | La configuración se rechaza si el secreto es corto, está en blanco o falta, si falta la clave interna, o si la caducidad es cero o negativa |
| `JwtIssuerTest` | `ms-auth` | 4 | El token emitido se valida con el mismo secreto y se rechaza con otro. Caducidad de 60 minutos por defecto |
| `AuthDtosTest` | `ms-auth` | 3 | El `toString()` de los DTOs no muestra la contraseña |

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

- Tests automáticos: nuevo test en `NotificationServiceTest` que comprueba que se piden los avisos del usuario y en orden descendente. ms-notifications: 5 tests, 0 fallos.
- API: tras una transferencia de 10 € de Ana a Luis, Ana ve solo su `TRANSFERENCIA_EMITIDA` y Luis solo su `TRANSFERENCIA_RECIBIDA`; `size=101` y `page=-1` dan 400; sin token, 401.
- Mensaje con el formato del contrato: «Has enviado 1.234,50 € desde tu cuenta ···0001 (Prueba formato). Saldo: 1.265,50 €».
- Una transferencia rechazada por fondos insuficientes no genera aviso.
- Tests automáticos: en `NotificationServiceTest`, un test comprueba que se piden los avisos del usuario y en orden descendente; `NotificationControllerTest` cubre el endpoint. ms-notifications: 12 tests, 0 fallos. `./mvnw clean verify`: 110 tests, 0 fallos.

**Pendiente:** nada impide guardar dos veces el aviso de un mismo movimiento si Kafka reentrega el mensaje, aunque el contrato lo prohíbe. Resuelto en KAF-03 (ver 4.16).

### 4.16. KAF-03 — Avisos repetidos tras reiniciar ms-notifications

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
- **Alta de gestores:** el único sitio del código que crea un usuario con rol `GESTOR` es el seeder del perfil `demo`, que usa una contraseña pública. El registro crea siempre usuarios `CLIENTE` y no existe ningún endpoint para dar de alta gestores. Si un entorno con datos reales se inicializara con el perfil `demo`, quedaría un gestor con contraseña conocida y acceso a todas las cuentas. En SEG-02 se ha evitado que el perfil se active por defecto, pero falta un mecanismo de alta de gestores que no dependa de la demo.
- **JWT con secreto compartido:** los tokens se firman con HS256, así que todos los servicios conocen el secreto y cualquiera de ellos podría emitir tokens. Una alternativa es RS256: ms-auth firma con una clave privada y el resto valida con la pública.
- **Transferencias:** verificar que los importes negativos, los redondeos y los errores durante una operación no provoquen alteraciones incorrectas de saldos.
- **Concurrencia:** comprobar que las operaciones simultáneas no permitan saldos inconsistentes.
- **Antifraude:** la integración con `ms-fraud` está hecha y verificada (4.13) y la regla de frecuencia está implementada (4.14). El comportamiento ante un fallo de ms-fraud está decidido: `503` y no se ejecuta la transferencia.
- **Kafka:** comprobar que los eventos solo se publiquen cuando la operación correspondiente se haya confirmado y que los consumidores gestionen correctamente los mensajes repetidos.
- **Notificaciones:** verificar la persistencia y consulta de avisos, así como su idempotencia.
- **Docker:** revisar las dependencias de arranque, la configuración de los servicios y los tamaños de las imágenes.
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

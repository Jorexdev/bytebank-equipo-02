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

### 4.7. Extracto: falta un movimiento en cada página

**Estado:** corregido en código (rama `fix/transactions-missing-statement`), pendiente de probar en el entorno.
**Impacto:** el extracto se saltaba un movimiento al principio de cada página, así que el cliente nunca veía todos sus movimientos.

**Causa:** `MovementService.statement` calculaba el desplazamiento como `page * size + 1`, suponiendo que `OFFSET` en PostgreSQL empieza en 1. Pero empieza en 0, así que la primera página arrancaba en el segundo movimiento.

**Corrección:** el desplazamiento pasa a ser `page * size`.

**Verificación:** test `firstPageStartsAtOffsetZero` en `MovementServiceTest`.

### 4.8. Extracto: el filtro por fechas no incluye el último día

**Estado:** corregido en código (rama `fix/date-filter-does-not-show-the-most-recent-day`), pendiente de probar en el entorno.
**Impacto:** al filtrar el extracto con `to`, los movimientos de ese día no aparecían. Además, los límites del rango se calculaban en UTC, de modo que los movimientos hechos entre las 00:00 y las 01:00/02:00 (hora de Madrid) caían en el día anterior.

**Causa:** el fin del rango se calculaba como `to` a las 00:00 UTC y se trataba como exclusivo. El test `filterByDateIncludesLastDay` que lo detectaba estaba deshabilitado (ver 6.2).

**Corrección:**

- `to` pasa a ser inclusivo: el rango termina al inicio del día siguiente.
- Los límites `from` y `to` se calculan en la zona configurada en `bytebank.zone` (`Europe/Madrid`), no en UTC.

**Verificación:** el test deshabilitado se ha sustituido por `rangeIncludesLastDayInMadridTime`. `MovementServiceTest`: 3 tests ejecutados, 0 fallos, 0 omitidos.

**Pendiente:** probar con la API real a través del Gateway.

## 5. Funcionalidades incompletas identificadas

El README, cuyo estado del proyecto está fechado en junio de 2026, reconoce las siguientes limitaciones:

| Área             | Estado descrito en el README                                                     |
| ---------------- | -------------------------------------------------------------------------------- |
| ms-cards         | Permite listar y bloquear tarjetas, pero emitir y pagar devuelve HTTP 501.       |
| ms-fraud         | Funciona de forma independiente, pero `ms-transfers` todavía no lo integra.      |
| Antifraude       | Falta implementar la regla de frecuencia.                                        |
| ms-notifications | Consume eventos Kafka y guarda avisos, pero falta el endpoint para consultarlos. |
| Extractos        | Se han recibido quejas sobre movimientos que no aparecen correctamente.          |

Estas limitaciones están documentadas por el equipo anterior, pero deben verificarse con pruebas durante el desarrollo.

Las quejas sobre el extracto se corresponden con dos defectos ya corregidos: ver 4.7 y 4.8.

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
- **Estado:** resuelto. Ver 4.8.

El origen estaba en la implementación: el fin del rango era exclusivo y se calculaba en UTC. El test se ha sustituido por `rangeIncludesLastDayInMadridTime`, que se ejecuta y pasa sin depender de la zona horaria de la máquina.

### 6.3. Resumen de resultados

En los informes de pruebas revisados se han identificado:

- 12 tests superados con resultados detallados.
- 4 errores en pruebas de integración de `ms-accounts`.
- 1 test omitido por una incidencia de zona horaria.

Además, `ms-gateway` y `ms-auth` completaron correctamente la ejecución general, aunque no se dispone en este inventario de un recuento individual de sus tests.

**Conclusión:** el principal bloqueo de las pruebas automatizadas se encuentra en `ms-accounts`, debido a la migración V3. También queda pendiente revisar el test de filtrado por fecha que está deshabilitado.

**Actualización:** la migración V3 está corregida (4.1) y el test de filtrado por fecha se ha sustituido por uno que ya no se omite (4.8).

## 7. Riesgos y aspectos pendientes de investigación

Los siguientes puntos requieren una revisión específica:

- **Seguridad:** la incidencia SEG-01 relativa al acceso a cuentas, movimientos y transferencias de otros usuarios está corregida y verificada. Queda pendiente continuar revisando otros aspectos, como la gestión de credenciales y secretos.
- **Credenciales:** revisar que no existan secretos o claves sensibles escritos directamente en el código o en configuraciones versionadas.
- **Transferencias:** verificar que los importes negativos, los redondeos y los errores durante una operación no provoquen alteraciones incorrectas de saldos.
- **Concurrencia:** comprobar que las operaciones simultáneas no permitan saldos inconsistentes.
- **Antifraude:** revisar la integración con `ms-fraud`, las reglas de validación y el comportamiento ante errores o falta de respuesta.
- **Kafka:** comprobar que los eventos solo se publiquen cuando la operación correspondiente se haya confirmado y que los consumidores gestionen correctamente los mensajes repetidos.
- **Notificaciones:** verificar la persistencia y consulta de avisos, así como su idempotencia.
- **Docker:** revisar las dependencias de arranque, la configuración de los servicios y los tamaños de las imágenes.
- **CI:** verificar la ejecución del nuevo workflow en GitHub Actions y la obligatoriedad de sus comprobaciones antes de integrar cambios.
- **Zonas horarias:** la incidencia del test `filterByDateIncludesLastDay` está resuelta (4.8). Queda comprobar que el resto de servicios trate las fechas con la misma zona (`bytebank.zone`).

Estos puntos son líneas de investigación y no deben considerarse defectos confirmados hasta reproducirlos o verificar su causa.

## 8. Próximas actuaciones

1. Resolver los problemas que impiden el arranque completo del entorno.
2. Verificar las comunicaciones entre los microservicios y completar las pruebas funcionales del Gateway.
3. Verificar la nueva configuración de integración continua en GitHub Actions y establecer los controles de integración correspondientes.
4. Ejecutar nuevamente las pruebas existentes y analizar sus resultados.
5. ~~Investigar el test omitido por la incidencia de zona horaria.~~ Resuelto (4.8).
6. Continuar investigando y documentando las incidencias funcionales y de seguridad pendientes.
7. Implementar las funcionalidades pendientes según las tarjetas del backlog.
8. Actualizar este documento con las correcciones realizadas y las pruebas que acrediten su funcionamiento.

---

**Nota:** este documento representa el diagnóstico inicial del equipo. Los hallazgos se irán actualizando conforme avance la investigación y se completen las tareas.

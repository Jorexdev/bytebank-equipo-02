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

**Estado:** reproducido y causa identificada.  
**Impacto:** bloquea el arranque de `ms-accounts`.

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

**Pendiente:** acordar y aplicar la corrección de la migración y comprobar que el servicio inicia correctamente y que las pruebas de integración se ejecutan.

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

**Estado:** problema confirmado mediante revisión del workflow.

El archivo `.github/workflows/ci.yml` ejecuta:

```yaml
- name: Build
  run: mvn -B -q package -DskipTests || true
```

Se han identificado dos problemas:

- `-DskipTests` impide ejecutar las pruebas durante la compilación.
- `|| true` hace que el comando devuelva un resultado satisfactorio aunque Maven haya fallado.

Por tanto, el workflow puede aparecer como correcto sin haber ejecutado las pruebas e incluso aunque la compilación falle.

**Pendiente:** revisar las pruebas existentes y configurar una CI que ejecute las comprobaciones necesarias y bloquee las integraciones cuando fallen.

### 4.4. BUG-01 — Transferencia con importe negativo

**Estado:** corregido en código, pendiente de probar en el entorno.
**Impacto:** permite mover dinero en el sentido contrario al esperado.

**Causa:** el importe no se validaba. Restar un negativo suma, y sumar un negativo resta.

**Corrección:**

- `ms-transfers`: `TransferService.create` comprueba `amount.signum() <= 0` y responde 400 con `IMPORTE_NO_VALIDO`.
- `ms-accounts`: `MovementRequest.amount` lleva `@Positive`, de modo que el endpoint interno también rechaza importes de cero o negativos.

**Verificación:** test automático con -100 €; los 5 tests de transferencias pasan.

**Pendiente:** los saldos ya alterados hay que corregirlos a mano.

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
- **Estado:** pendiente de revisión.

El test no se ejecuta porque está omitido expresamente. No se ha determinado todavía si el origen del problema está en la implementación, en el propio test o en la configuración de zona horaria del entorno.

No se considera un fallo confirmado de funcionalidad hasta investigar y reproducir el problema.

### 6.3. Resumen de resultados

En los informes de pruebas revisados se han identificado:

- 12 tests superados con resultados detallados.
- 4 errores en pruebas de integración de `ms-accounts`.
- 1 test omitido por una incidencia de zona horaria.

Además, `ms-gateway` y `ms-auth` completaron correctamente la ejecución general, aunque no se dispone en este inventario de un recuento individual de sus tests.

**Conclusión:** el principal bloqueo de las pruebas automatizadas se encuentra en `ms-accounts`, debido a la migración V3. También queda pendiente revisar el test de filtrado por fecha que está deshabilitado.

## 7. Riesgos y aspectos pendientes de investigación

Los siguientes puntos requieren una revisión específica:

- **Seguridad:** comprobar la autorización sobre cuentas y movimientos para evitar accesos a recursos de otros usuarios.
- **Credenciales:** revisar que no existan secretos o claves sensibles escritos directamente en el código o en configuraciones versionadas.
- **Transferencias:** verificar que los importes negativos, los redondeos y los errores durante una operación no provoquen alteraciones incorrectas de saldos.
- **Concurrencia:** comprobar que las operaciones simultáneas no permitan saldos inconsistentes.
- **Antifraude:** revisar la integración con `ms-fraud`, las reglas de validación y el comportamiento ante errores o falta de respuesta.
- **Kafka:** comprobar que los eventos solo se publiquen cuando la operación correspondiente se haya confirmado y que los consumidores gestionen correctamente los mensajes repetidos.
- **Notificaciones:** verificar la persistencia y consulta de avisos, así como su idempotencia.
- **Docker:** revisar las dependencias de arranque, la configuración de los servicios y los tamaños de las imágenes.
- **CI:** comprobar que las pruebas unitarias y de integración se ejecutan de forma fiable.
- **Zonas horarias:** investigar la incidencia del test `filterByDateIncludesLastDay` y comprobar que el tratamiento de fechas sea consistente entre entornos.

Estos puntos son líneas de investigación y no deben considerarse defectos confirmados hasta reproducirlos o verificar su causa.

## 8. Próximas actuaciones

1. Resolver los problemas que impiden el arranque completo del entorno.
2. Verificar las comunicaciones entre los microservicios y completar las pruebas funcionales del Gateway.
3. Revisar y corregir la configuración de integración continua.
4. Ejecutar nuevamente las pruebas existentes y analizar sus resultados.
5. Investigar el test omitido por la incidencia de zona horaria.
6. Investigar y documentar las incidencias funcionales y de seguridad.
7. Implementar las funcionalidades pendientes según las tarjetas del backlog.
8. Actualizar este documento con las correcciones realizadas y las pruebas que acrediten su funcionamiento.

---

**Nota:** este documento representa el diagnóstico inicial del equipo. Los hallazgos se irán actualizando conforme avance la investigación y se completen las tareas.

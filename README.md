# ByteBank

Backend de la banca online de ByteBank: cuentas, transferencias, tarjetas de débito, validación antifraude y avisos
de movimientos. Microservicios con Spring Boot que se comunican por HTTP (y por Kafka para los avisos), con una base
de datos PostgreSQL por servicio.

## Servicios

| Servicio         | Puerto | Qué hace                                                        |
| ---------------- | ------ | --------------------------------------------------------------- |
| ms-gateway       | 8080   | Punto de entrada. Solo enruta las rutas `/api/**`               |
| ms-auth          | 8081   | Registro, login y emisión de tokens JWT                         |
| ms-accounts      | 8082   | Cuentas, saldo y movimientos (extracto)                         |
| ms-transfers     | 8083   | Transferencias entre cuentas                                    |
| ms-cards         | 8084   | Tarjetas de débito y pagos                                      |
| ms-fraud         | 8085   | Validación antifraude de transferencias                         |
| ms-notifications | 8086   | Avisos al cliente de cada movimiento (consume eventos de Kafka) |

Más detalle en [docs/arquitectura.md](docs/arquitectura.md).

## Seguridad

La API utiliza autenticación mediante JWT y distingue dos roles:

- **CLIENTE:** puede consultar las cuentas de las que es titular y sus movimientos, realizar transferencias desde ellas y pedir tarjetas.
- **GESTOR:** puede consultar todas las cuentas y sus movimientos, y cambiar el estado de las cuentas.

Las operaciones internas entre microservicios utilizan rutas `/internal/**`, protegidas mediante la cabecera `X-Internal-Key` y el rol `INTERNAL`.

Los secretos (`JWT_SECRET`, `INTERNAL_KEY` y la contraseña de la base de datos) llegan siempre por variables de entorno y
nunca se escriben en el código ni en ficheros versionados. No tienen valor por defecto: si falta alguno, el sistema no arranca.

## Stack

Java 17, Spring Boot 3.3, Spring Security con JWT, Spring Data JPA, Flyway, PostgreSQL 15, Spring Cloud Gateway,
Apache Kafka (modo KRaft), JUnit 5, Mockito, Testcontainers, Docker Compose y GitHub Actions.

## Arrancar en local

```bash
cp .env.example .env      # y cambia las claves
docker compose up --build
```

El `.env` no se sube al repositorio y cada persona tiene el suyo. Variables obligatorias:

| Variable            | Para qué sirve                                                       |
| ------------------- | -------------------------------------------------------------------- |
| `POSTGRES_PASSWORD` | Contraseña de PostgreSQL                                             |
| `JWT_SECRET`        | Secreto con el que se firman y validan los JWT. Mínimo 32 caracteres |
| `INTERNAL_KEY`      | Clave compartida de las llamadas entre servicios (`/internal/**`)    |

Genera un `JWT_SECRET` aleatorio y distinto en cada entorno, por ejemplo con `openssl rand -base64 48`.
Si falta o tiene menos de 32 caracteres, los servicios no arrancan.

La API queda en `http://localhost:8080`. Cada servicio publica su Swagger en
`http://localhost:<puerto>/swagger-ui.html` (por ejemplo, `http://localhost:8082/swagger-ui.html` para ms-accounts).

El perfil `demo` no se activa por defecto: hay que pedirlo con `SPRING_PROFILES_ACTIVE=demo` en el `.env`
(`.env.example` ya lo trae así). Sobre una base de datos vacía crea estos usuarios y unas cuentas con saldo:

| Usuario              | Contraseña   | Rol                 |
| -------------------- | ------------ | ------------------- |
| gestor@bytebank.test | Gestor1234!  | GESTOR              |
| ana@bytebank.test    | Cliente1234! | CLIENTE (2 cuentas) |
| luis@bytebank.test   | Cliente1234! | CLIENTE (1 cuenta)  |

Estas contraseñas son públicas, así que el perfil `demo` es solo para entornos locales. No lo actives nunca en un
entorno con datos reales: el usuario gestor puede consultar y operar sobre todas las cuentas.

Ejemplo:

```bash
curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"ana@bytebank.test","password":"Cliente1234!"}'
# usa el token devuelto en la cabecera Authorization: Bearer <token>
curl -s localhost:8080/api/accounts -H "Authorization: Bearer $TOKEN"
```

## Compilar y tests

```bash
./mvnw clean verify
```

Los tests de integración usan Testcontainers, así que necesitan Docker arrancado.

## Integración continua (CI)

El proyecto utiliza **GitHub Actions** para validar automáticamente los cambios.

El workflow se ejecuta en cada `push` y en cada Pull Request, mediante dos trabajos independientes que se ejecutan en paralelo:

- **Maven tests:** configura Java 17 y ejecuta `./mvnw -B -fae verify` para compilar el proyecto y ejecutar las pruebas automatizadas de los módulos.
- **Docker build:** construye las imágenes Docker de los siete microservicios utilizando el Dockerfile principal. Las imágenes se generan localmente en el entorno de CI y no se publican en ningún registro.

Si alguna de las comprobaciones falla, la ejecución global de la CI se marca como fallida.

## Estado del proyecto (junio de 2026)

Notas para quien coja el proyecto:

- **ms-cards**: se pueden listar, bloquear y emitir tarjetas; pagar sigue devolviendo 501.
  El contrato acordado está en [docs/contratos/ms-cards.md](docs/contratos/ms-cards.md).
- **ms-fraud** está integrado en ms-transfers: cada transferencia se valida antes de ejecutarse y las tres reglas
  acordadas (importe máximo, transferencia externa y frecuencia) están implementadas. Si ms-fraud no responde, la
  operación se rechaza con 503. Ver [docs/fraude.md](docs/fraude.md).
- **ms-notifications** ya recibe los movimientos de Kafka y guarda los avisos, pero falta el endpoint para
  consultarlos. Contrato en [docs/contratos/ms-notifications.md](docs/contratos/ms-notifications.md).
- Algunos clientes se han quejado del extracto de movimientos. No me ha dado tiempo a mirarlo.

Raúl

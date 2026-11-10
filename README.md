# ByteBank

Backend de la banca online de ByteBank: cuentas, transferencias, tarjetas de débito, validación antifraude y avisos
de movimientos. Microservicios con Spring Boot que se comunican por HTTP (y por Kafka para los avisos), con una base
de datos PostgreSQL por servicio.

## Servicios

| Servicio | Puerto | Qué hace |
|---|---|---|
| ms-gateway | 8080 | Punto de entrada. Solo enruta las rutas `/api/**` |
| ms-auth | 8081 | Registro, login y emisión de tokens JWT |
| ms-accounts | 8082 | Cuentas, saldo y movimientos (extracto) |
| ms-transfers | 8083 | Transferencias entre cuentas |
| ms-cards | 8084 | Tarjetas de débito y pagos |
| ms-fraud | 8085 | Validación antifraude de transferencias |
| ms-notifications | 8086 | Avisos al cliente de cada movimiento (consume eventos de Kafka) |

Más detalle en [docs/arquitectura.md](docs/arquitectura.md).

## Stack

Java 17, Spring Boot 3.3, Spring Security con JWT, Spring Data JPA, Flyway, PostgreSQL 15, Spring Cloud Gateway,
Apache Kafka (modo KRaft), JUnit 5, Mockito, Testcontainers, Docker Compose y GitHub Actions.

## Arrancar en local

```bash
cp .env.example .env      # y cambia las claves
docker compose up --build
```

La API queda en `http://localhost:8080`. Cada servicio publica su Swagger en
`http://localhost:<puerto>/swagger-ui.html` (por ejemplo, `http://localhost:8082/swagger-ui.html` para ms-accounts).

Con el perfil `demo` (el que viene en `.env.example`) se crean estos usuarios y unas cuentas con saldo:

| Usuario | Contraseña | Rol |
|---|---|---|
| gestor@bytebank.test | Gestor1234! | GESTOR |
| ana@bytebank.test | Cliente1234! | CLIENTE (2 cuentas) |
| luis@bytebank.test | Cliente1234! | CLIENTE (1 cuenta) |

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

- **ms-cards** está a medias: se pueden listar y bloquear tarjetas, pero emitir y pagar devuelven 501.
  El contrato acordado está en [docs/contratos/ms-cards.md](docs/contratos/ms-cards.md).
- **ms-fraud** funciona por su cuenta, pero ms-transfers todavía no lo llama. Falta también la regla de frecuencia.
  Ver [docs/fraude.md](docs/fraude.md).
- **ms-notifications** ya recibe los movimientos de Kafka y guarda los avisos, pero falta el endpoint para
  consultarlos. Contrato en [docs/contratos/ms-notifications.md](docs/contratos/ms-notifications.md).
- Algunos clientes se han quejado del extracto de movimientos. No me ha dado tiempo a mirarlo.

Raúl

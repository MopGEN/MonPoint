# ms-auth

Autenticación y gestión de usuarios de MonPoint (épica E-01: H-010 a H-014). Emite el **JWT** que el API Gateway
valida en cada petición. Es el primer servicio terminado y sirve de **referencia para construir los demás**: la
sección [Guía para tu microservicio](#guía-para-tu-microservicio) resume lo que todos deben respetar.

| | |
|---|---|
| Puerto | `8081`. **No se publica**: se entra por el Gateway (`http://localhost:8080/auth/**`) |
| Base de datos | `auth_db` (usuario `auth_user`), MongoDB replica set `rs0` |
| Nombre en Eureka | `MS-AUTH` (`spring.application.name=ms-auth`) |
| Eventos | Ninguno: ms-auth está **fuera del bus** (diagrama oficial `docs/arquitectura/01-componentes-y-trafico.jpg`) |
| Stack | Java 21 · Spring Boot 4.1.1 · Spring Cloud 2025.1.3 · Spring Security 7 · JJWT 0.12.6 · Spring Data MongoDB |

Documentación detallada:
- [`docs/CONTRATO_TOKEN.md`](docs/CONTRATO_TOKEN.md): campos del token, firma, errores 401/403 y responsabilidades del Gateway.
- [`docs/DESPLIEGUE.md`](docs/DESPLIEGUE.md): variables, Docker y registro en Eureka.
- [`docs/postman/`](docs/postman/): colección y entorno de Postman (21 pruebas).

---

## Endpoints

Todas las rutas empiezan con `/auth`; el Gateway las reenvía tal cual (sin `StripPrefix`).

| Método y ruta | Acceso | Respuesta |
|---|---|---|
| `POST /auth/login` | Pública | `200` con el token · `401` opaco si falla |
| `GET /auth/me` | Cualquier usuario autenticado | `200` perfil actual (leído de la BD) |
| `POST /auth/register` | `ADMIN` | `201` · `400` contraseña débil o datos inválidos · `409` correo duplicado |
| `GET /auth/usuarios` | `ADMIN` | `200` usuarios activos |
| `PUT /auth/usuarios/{id}` | Autenticado. Un `VENDEDOR` solo sobre su propio id; nadie cambia su propio rol | `200` · `400` · `403` · `404` · `409` |
| `PATCH /auth/usuarios/{id}/desactivar` | `ADMIN`, nunca sobre su propia cuenta | `204` |
| `GET /actuator/health` | Pública | `200 {"status":"UP"}` |

Login de ejemplo (a través del Gateway):

```bash
curl -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"correo":"admin@monpoint.com","password":"Admin123!"}'
```

```json
{"token":"eyJhbGciOiJIUzI1NiJ9...","tipo":"Bearer","correo":"admin@monpoint.com","nombre":"Administrador","rol":"ADMIN","expiraEn":86400000}
```

Después, en cada petición protegida: `Authorization: Bearer <token>`.

### Usuario inicial
Si la colección `usuarios` está vacía, al arrancar se crea `admin@monpoint.com` con rol `ADMIN` y la contraseña de
`ADMIN_DEFAULT_PASSWORD` (por omisión `Admin123!`).

### Reglas de negocio
- **Roles:** exclusivamente `ADMIN` o `VENDEDOR`, uno por usuario. Otro valor ➔ 400.
- **Correo:** siempre se llama `correo` (nunca `email`) y se normaliza con `trim().toLowerCase()`. Es único (índice declarado).
- **Contraseña:** 8 a 72 caracteres, con al menos una mayúscula, una minúscula, un dígito y un símbolo `@$!%*?&`. El 400 dice qué regla falló.
- **Login opaco:** correo inexistente, contraseña incorrecta o cuenta inactiva dan el mismo 401, en el mismo tiempo.
- **Nunca** se devuelve el hash de la contraseña.

### El token (resumen; detalle en el contrato)
- HS256 con `JWT_SECRET` en **texto plano UTF-8** (no Base64), mínimo 32 caracteres, **idéntica en el Gateway**.
- Claims: `sub` (correo), `userId` (ObjectId), `rol`, `iat`, `exp`. Vigencia 24 h.
- No se revocan: desactivar a un usuario no invalida los tokens que ya tenía hasta que vencen.

---

## Variables de entorno

| Variable | Por omisión | Para qué |
|---|---|---|
| `SERVER_PORT` | `8081` | Puerto HTTP |
| `MONGO_URI` | `mongodb://auth_user:auth_pass@localhost:27017/auth_db?replicaSet=rs0&authSource=auth_db&directConnection=true` | En Docker va **sin** `directConnection=true` |
| `EUREKA_URL` | `http://localhost:8761/eureka/` | Servidor Eureka |
| `JWT_SECRET` | Marcador de `infra/.env.example` | Clave compartida con el Gateway |
| `JWT_EXPIRATION_MS` | `86400000` (24 h) | Vigencia del token |
| `ADMIN_DEFAULT_PASSWORD` | `Admin123!` | Contraseña del admin inicial |

## Cómo correrlo

**Todo el sistema en Docker** (lo normal):

```bash
cd monpoint-ms/infra
cp .env.example .env          # solo la primera vez
docker compose up -d --build
```

Entra por `http://localhost:8080` (Gateway). Panel de Eureka: `http://localhost:8761`.

**En local, desde IntelliJ o la terminal** (para depurar):

```bash
cd monpoint-ms/infra && docker compose up -d mongodb eureka-server
cd ../ms-auth && ./mvnw spring-boot:run          # queda en http://localhost:8081
```

> ⚠️ Si dejas un ms-auth local corriendo en `8081` y además levantas el contenedor, tendrás **dos** instancias.
> Para probar el sistema real, apunta Postman al Gateway (`8080`), no a `8081`.

## Pruebas

```bash
cd monpoint-ms/infra && docker compose up -d mongodb     # las de integración usan MongoDB real
cd ../ms-auth && ./mvnw test
```

68 pruebas: unitarias (JUnit 5 + Mockito), de API y seguridad (`@WebMvcTest` + MockMvc con la seguridad real) y de
integración contra MongoDB. Postman: importa `docs/postman/` y corre la colección; con `baseUrl` en `8080` pasa por el Gateway.

## Estructura (capas)

```
com.monpoint.auth
├── controller/   AuthController, UsuarioController, GlobalExceptionHandler   ← delgados: solo delegan
├── service/      AuthService, UsuarioService (+ impl/)                       ← reglas de negocio, @Transactional
├── repository/   UsuarioRepository                                           ← solo Spring Data
├── model/        Usuario (@Document), Rol
├── dto/          request/ y response/ como records                           ← nunca se expone el documento
├── security/     JwtService, JwtAuthenticationFilter, SecurityConfig, handlers 401/403
├── config/       MongoConfig, SecurityBeansConfig, DataInitializer
├── audit/        RequestIdFilter, AuthAuditService                            ← X-Request-ID en logs y errores
├── event/        AuthEventPublisher (hoy solo log; ms-auth está fuera del bus)
└── validator/    PasswordValidator
```

---

## Guía para tu microservicio

Lo que todos los servicios deben cumplir para que el sistema sea coherente. Sale del backlog (Doc. 03), del
diagrama oficial y de lo que ya se verificó con ms-auth, Eureka y el Gateway.

### Versiones y proyecto
- Java 21, **Spring Boot 4.1.1**, **Spring Cloud 2025.1.3** (no uses `2026.0.0-M1`: apunta a Boot 4.2).
- Paquete `com.monpoint.<servicio>` (ej. `com.monpoint.inventario`).
- Capas: `Controller ➔ Service ➔ Repository ➔ Model`. Controladores delgados, DTOs como `record`, sin `@Autowired` en campos (constructor o `@RequiredArgsConstructor`).

### Spring Boot 4: trampas que ya costaron tiempo
- MongoDB se configura con **`spring.mongodb.uri`**. `spring.data.mongodb.uri` ya no se lee y la app se conecta en silencio a `localhost/test`.
- Jackson 3: paquetes `tools.jackson.*`.
- En pruebas: `@MockitoBean` (no existe `@MockBean`) y `@WebMvcTest` está en `org.springframework.boot.webmvc.test.autoconfigure`.
- Índices: declara `@Indexed(unique = true)` donde haya unicidad y `spring.data.mongodb.auto-index-creation=true`.
- Importes en **`Decimal128`**, declarado explícitamente.

### Rutas y Gateway
- Expón tus endpoints **con tu prefijo completo**: `/inventario/...`, `/ventas/...`, `/clientes/...`, `/reportes/...`, `/notificaciones/...`. El Gateway ya tiene esas rutas y **no** quita el prefijo.
- `spring.application.name` debe ser exactamente `ms-inventario`, `ms-ventas`, `ms-clientes`, `ms-reportes` o `ms-notificaciones`: así te encuentra el Gateway (`lb://ms-…`).
- **Identidad:** el Gateway ya validó el token y te manda `X-User-Id` y `X-User-Role`. Puedes confiar en ellos (borra los que mande el cliente). **El rol lo verificas tú** (403 si no alcanza).
- Usa `X-Request-ID` en tus logs y en tus errores (copia `audit/RequestIdFilter` de ms-auth).

### Errores
- Formato **RFC 9457 `ProblemDetail`** (`application/problem+json`), con `requestId`. Copia el `GlobalExceptionHandler` de ms-auth como base.
- Códigos: 200, 201, 202 (venta asíncrona), 204, 400, 401, 403, 404, 409. Nunca un 500 con la traza.

### Eureka y Actuator (copia el bloque de `ms-auth/src/main/resources/application.properties`)

```properties
eureka.client.service-url.defaultZone=${EUREKA_URL:http://localhost:8761/eureka/}
eureka.instance.prefer-ip-address=true
eureka.instance.instance-id=${spring.application.name}:${spring.cloud.client.ip-address}:${server.port}
eureka.instance.lease-renewal-interval-in-seconds=10
eureka.instance.lease-expiration-duration-in-seconds=30
eureka.client.healthcheck.enabled=true
eureka.client.instance-info-replication-interval-seconds=10
management.endpoints.web.exposure.include=health
management.endpoint.health.show-details=never
```

En tus pruebas apaga Eureka: `@SpringBootTest(properties = "eureka.client.enabled=false")`.

### Docker y Compose
- `Dockerfile` en dos etapas como el de ms-auth (usuario uid 10001, `HEALTHCHECK` con `curl` a `/actuator/health`) y su `.dockerignore`.
- En `infra/docker-compose.yml` copia la **plantilla comentada**. Lo esencial:
  - **sin `ports`** (solo el Gateway publica);
  - `depends_on` con `condition: service_healthy` (mongodb, eureka-server y rabbitmq si usas eventos);
  - **`stop_grace_period: 15s`**: sin esto Docker mata el servicio antes de que se dé de baja en Eureka;
  - credenciales solo por variables de `.env` (nunca en el código).

### Comunicación entre servicios
- **Ningún servicio llama a otro por HTTP.** Si lo necesitas, falta un evento o una réplica local: escálalo al Product Owner.
- Eventos por RabbitMQ según el catálogo (`docs/eventos/`), con estructura `eventoId, tipo, version, fecha, origen, datos`.
- **Todo consumidor es idempotente** por `eventoId`: un mensaje puede llegar dos veces.

### Ramas, commits y PR (`CONTRIBUTING.md`)
- Rama: `<tipo>/<actividad>-<descripcion>` (ej. `feat/3.2-ms-inventario-productos`).
- Commit: `<actividad> <descripción en presente>` (ej. `3.2 Agrega el alta de productos`).
- PR a `main` con la plantilla; se fusiona de inmediato y se revisa después. **Antes de fusionar, `docker compose up` debe seguir levantando todo.**

# ms-auth

Autenticación y gestión de usuarios de MonPoint (épica E-01: H-010 a H-014). Emite el **JWT** que el API Gateway
valida en cada petición. Es el primer servicio terminado y sirve de **referencia para construir los demás**: la
[Guía para tu microservicio](#guía-para-tu-microservicio) reúne lo que todos deben respetar, con código listo para copiar.

> **Si eres una IA leyendo esto para generar un servicio de MonPoint:** las reglas de la
> [guía](#guía-para-tu-microservicio) no se negocian. Las fuentes de verdad están en la sección
> [G2](#g2-fuentes-de-verdad). No inventes campos, eventos, colas ni endpoints que no estén en esas fuentes.

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
- [`../docs/modelo-datos/README.md`](../docs/modelo-datos/README.md): modelo de datos de **todos** los servicios (Doc 04).

---

## Qué hace y cómo funciona

### Propósito
Es el **dueño de la identidad**: guarda a los usuarios (`ADMIN` y `VENDEDOR`), verifica sus credenciales y emite un
token firmado que dice quién es cada uno y qué rol tiene. Es el único servicio que conoce contraseñas. **Ningún otro
servicio lo llama:** el Gateway valida sus tokens con la clave compartida y reenvía la identidad en cabeceras.

### Patrón
- **API REST en capas** (`Controller ➔ Service ➔ Repository ➔ Model`), sin estado: no hay sesión en el servidor; cada petición trae su token.
- **Emisor de tokens** (HS256): ms-auth firma y el Gateway verifica con la misma `JWT_SECRET`, sin consultarle.
- **Defensa en profundidad:** aunque el Gateway ya validó el token, ms-auth lo vuelve a validar con Spring Security, porque es quien administra cuentas y roles.

### Flujo de una petición

```
LOGIN (pública)
Cliente ──POST /auth/login──► Gateway :8080 ──(ruta pública; borra X-User-*)──► ms-auth :8081
                                                RequestIdFilter      → X-Request-ID en logs y respuesta
                                                AuthController       → valida el DTO (@Valid)
                                                AuthService          → busca por correo normalizado y compara BCrypt
                                                                       (mismo tiempo si el correo no existe)
                                                JwtService           → firma HS256 {sub, userId, rol, iat, exp}
Cliente ◄────────────── 200 {token, tipo, correo, nombre, rol, expiraEn} ──────────────┘

PETICIÓN PROTEGIDA (p. ej. GET /auth/me)
Cliente ──Bearer token──► Gateway: valida firma y exp, agrega X-User-Id/X-User-Role ──► ms-auth
                                                JwtAuthenticationFilter → valida el JWT otra vez y pone un UserPrincipal
                                                SecurityConfig          → reglas por ruta (hasRole) ➔ 401/403 en ProblemDetail
                                                UsuarioController ➔ UsuarioService (reglas finas, @Transactional)
                                                                  ➔ UsuarioRepository ➔ auth_db
```

### Tecnologías y librerías

| Librería | Versión | Para qué |
|---|---|---|
| `spring-boot-starter-webmvc`, `-validation`, `-actuator` | Boot 4.1.1 | API REST, validación de DTOs y `/actuator/health` |
| `spring-boot-starter-security` | Spring Security 7 | Cadena de filtros sin estado, BCrypt(12) y 401/403 |
| `spring-boot-starter-data-mongodb` | Spring Data MongoDB 5.1 | Repositorio, auditoría de fechas y transacciones (replica set `rs0`) |
| `jjwt-api` / `-impl` / `-jackson` | 0.12.6 | Firmar y validar HS256 |
| `spring-cloud-starter-netflix-eureka-client` | Spring Cloud 2025.1.3 | Registro como `MS-AUTH` |
| Lombok | gestionada por Boot | Constructores (`@RequiredArgsConstructor`) y `@Slf4j` |
| Jackson 3 | gestionada por Boot | JSON. Paquetes `tools.jackson.*` |

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
`ADMIN_DEFAULT_PASSWORD` (por omisión `Admin123!`, solo para desarrollo).

### Reglas de negocio
- **Roles:** exclusivamente `ADMIN` o `VENDEDOR`, uno por usuario. Otro valor ➔ 400.
- **Correo:** siempre se llama `correo` (nunca `email`) y se normaliza con `trim().toLowerCase()`. Es único (índice declarado).
- **Contraseña:** 8 a 72 caracteres, con al menos una mayúscula, una minúscula, un dígito y un símbolo `@$!%*?&`. El 400 dice qué regla falló.
- **Login opaco:** correo inexistente, contraseña incorrecta o cuenta inactiva dan el mismo 401, en el mismo tiempo.
- **Nunca** se devuelve el hash de la contraseña.

### El token (resumen; detalle en el contrato)
- HS256 con `JWT_SECRET` en **texto plano UTF-8** (no Base64), mínimo 32 caracteres, **idéntica en el Gateway**.
- Claims: `sub` (correo), `userId` (ObjectId), `rol`, `iat`, `exp`. Vigencia de 24 h.
- No se revocan: desactivar a un usuario no invalida los tokens que ya tenía hasta que vencen.

### Datos que guarda
Colección `usuarios` en `auth_db`: `_id`, `correo` (índice único), `password_hash`, `nombre`, `rol`, `activo`,
`creado_en` y `actualizado_en`.

> ⚠️ Esto **no coincide** con el modelo cerrado del Doc 04 (`contrasenaHash`, `fechaRegistro`, `fechaActualizacion`
> en camelCase, sin `activo` y con baja física). Está pendiente de decisión del Product Owner. **Tu servicio debe
> seguir el Doc 04**, no este detalle de ms-auth. Ver [`docs/modelo-datos`](../docs/modelo-datos/README.md).

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
cp .env.example .env          # solo la primera vez; luego CAMBIA las contraseñas y JWT_SECRET
docker compose up -d --build
```

Entra por `http://localhost:8080` (Gateway). Panel de Eureka: `http://localhost:8761`, solo desde tu máquina.

> 🔒 **No dejes los valores de `.env.example` en tu `.env`.** El repositorio es público: con el `JWT_SECRET` de
> ejemplo, cualquiera puede firmar tokens de ADMIN que tu Gateway aceptaría. Genera los tuyos con
> `openssl rand -hex 32` (JWT) y `openssl rand -hex 24` (contraseñas).

**En local, desde IntelliJ o la terminal** (para depurar):

```bash
cd monpoint-ms/infra && docker compose up -d mongodb eureka-server
cd ../ms-auth && JWT_SECRET='<el de tu .env>' ./mvnw spring-boot:run     # queda en http://localhost:8081
```

> ⚠️ Si dejas un ms-auth local corriendo en `8081` y además levantas el contenedor, tendrás **dos** instancias.
> Para probar el sistema real, apunta Postman al Gateway (`8080`), no a `8081`. Si el ms-auth local no usa el
> mismo `JWT_SECRET` que el `.env`, el Gateway rechazará sus tokens.

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

Lo que todos los servicios de dominio (`ms-inventario`, `ms-ventas`, `ms-clientes`, `ms-reportes`,
`ms-notificaciones`) deben cumplir para que el sistema sea coherente. Sale del backlog (Doc 03), del modelo de datos
(Doc 04), del diagrama oficial y de lo que ya se verificó funcionando con ms-auth, Eureka y el Gateway. Los
contratos del [Gateway](../api-gateway/README.md) y de [Eureka](../eureka-server/README.md) están en sus propios READMEs.

### G1. Reglas que no se negocian

1. **Ningún servicio llama a otro por HTTP.** Entre servicios solo hay eventos de RabbitMQ (G10). Si parece necesario llamar a otro servicio, falta un evento o una réplica local: se escala al Product Owner.
2. **Todo entra por el Gateway.** Tu servicio no publica puertos y no valida JWT: confía en `X-User-Id` / `X-User-Role` (G5).
3. **El rol se verifica en tu servicio** y, si no alcanza, respondes **403** en `ProblemDetail` (G5, G6).
4. **Errores en RFC 9457 `ProblemDetail`** con `requestId`, siempre. Nunca una traza al cliente (G6).
5. **`X-Request-ID` en cada log y en cada error** (G7).
6. **Datos según el Doc 04**: mismos nombres de colección y campo, `Decimal128` para importes, `Instant` UTC para fechas, índices declarados (G8).
7. **DTOs como `record`.** Nunca expongas el documento de Mongo (G9).
8. **Todo consumidor de eventos es idempotente** por `eventoId` (G10).
9. **Sin `@Autowired` en campos:** constructor o `@RequiredArgsConstructor`.
10. **Sin credenciales en el código:** todo sale de variables de entorno (`.env`).

### G2. Fuentes de verdad

Si dos fuentes chocan, manda la de arriba. Ante la ambigüedad, se escala al Product Owner; no se interpreta.

| Tema | Fuente |
|---|---|
| Qué se construye y cuándo (incluido el **alcance recortado**, §7.4) | `docs/planeacion/MonPoint_MS_01_Plan_de_Trabajo_del_Equipo.pdf` |
| Criterios de aceptación de cada historia (H-xxx) | `docs/planeacion/MonPoint_MS_03_Product_Backlog_y_Convenciones_de_Trabajo.pdf` |
| Colecciones, campos, tipos e índices | `docs/planeacion/MonPoint_MS_04_Modelo_de_Datos.pdf`, transcrito en [`docs/modelo-datos/`](../docs/modelo-datos/README.md) |
| Eventos (nombres, campos, routing keys, colas) | `docs/eventos/`: **catálogo pendiente** (H-008). Ver G10 |
| Rutas, cabeceras y errores del Gateway | [`api-gateway/README.md`](../api-gateway/README.md) |
| Registro en Eureka | [`eureka-server/README.md`](../eureka-server/README.md) |
| Ramas, commits y PR | [`CONTRIBUTING.md`](../CONTRIBUTING.md) |

**Recortado (Doc 01 §7.4), no se construye:** patrón outbox, gestión de proveedores, paginación, filtros y
ordenamiento en listados, comprobantes en PDF y pantallas de administración del cliente web.

### G3. Proyecto Maven

| Servicio | `spring.application.name` | Puerto | Prefijo | Base / usuario | Paquete |
|---|---|---|---|---|---|
| ms-inventario | `ms-inventario` | 8082 | `/inventario/**` | `inventario_db` / `inventario_user` | `com.monpoint.inventario` |
| ms-ventas | `ms-ventas` | 8083 | `/ventas/**` | `ventas_db` / `ventas_user` | `com.monpoint.ventas` |
| ms-clientes | `ms-clientes` | 8084 | `/clientes/**` | `clientes_db` / `clientes_user` | `com.monpoint.clientes` |
| ms-reportes | `ms-reportes` | 8085 | `/reportes/**` | `reportes_db` / `reportes_user` | `com.monpoint.reportes` |
| ms-notificaciones | `ms-notificaciones` | 8086 | `/notificaciones/**` | `notificaciones_db` / `notificaciones_user` | `com.monpoint.notificaciones` |

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.1.1</version>
</parent>

<properties>
    <java.version>21</java.version>
    <spring-cloud.version>2025.1.3</spring-cloud.version>
</properties>

<dependencies>
    <!-- Boot 4: el starter web se llama "webmvc" -->
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-webmvc</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-mongodb</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-actuator</artifactId></dependency>
    <dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-starter-netflix-eureka-client</artifactId></dependency>
    <!-- Solo si publicas o consumes eventos (todos menos ms-auth) -->
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-amqp</artifactId></dependency>
    <dependency><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId><optional>true</optional></dependency>

    <!-- Boot 4: un starter de pruebas por tecnología -->
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-webmvc-test</artifactId><scope>test</scope></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-mongodb-test</artifactId><scope>test</scope></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation-test</artifactId><scope>test</scope></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-amqp-test</artifactId><scope>test</scope></dependency>
</dependencies>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>${spring-cloud.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

- **No agregues JJWT:** tu servicio no valida tokens.
- Spring Security es opcional; lee G11 antes de dejarlo.
- Con Lombok, configura `annotationProcessorPaths` en el `maven-compiler-plugin`, como lo genera Spring Initializr.

### G4. `application.properties`

Reemplaza `<servicio>` y `<puerto>` (G3). Los valores por omisión sirven para correr desde el IDE; en Docker, el compose los sobrescribe.

```properties
spring.application.name=ms-<servicio>
server.port=${SERVER_PORT:<puerto>}

# X-Request-ID en cada línea de log (lo pone RequestIdFilter en el MDC, G7)
logging.pattern.level=%5p [%X{requestId:-}]
# API pura: una ruta inexistente responde 404 en ProblemDetail y no un 405
spring.web.resources.add-mappings=false

# MongoDB. Boot 4 lee spring.mongodb.uri (spring.data.mongodb.uri ya NO se lee)
spring.mongodb.uri=${MONGO_URI:mongodb://<servicio>_user:<servicio>_pass@localhost:27017/<servicio>_db?replicaSet=rs0&authSource=<servicio>_db&directConnection=true}
spring.data.mongodb.auto-index-creation=true

# Eureka (contrato en eureka-server/README.md)
eureka.client.service-url.defaultZone=${EUREKA_URL:http://localhost:8761/eureka/}
eureka.instance.prefer-ip-address=true
eureka.instance.instance-id=${spring.application.name}:${spring.cloud.client.ip-address}:${server.port}
eureka.instance.lease-renewal-interval-in-seconds=10
eureka.instance.lease-expiration-duration-in-seconds=30
eureka.client.healthcheck.enabled=true
eureka.client.instance-info-replication-interval-seconds=10

# Actuator: solo /actuator/health, sin detalles internos
management.endpoints.web.exposure.include=health
management.endpoint.health.show-details=never

# RabbitMQ (si publicas o consumes eventos)
spring.rabbitmq.host=${RABBITMQ_HOST:localhost}
spring.rabbitmq.username=${RABBITMQ_USER:monpoint}
spring.rabbitmq.password=${RABBITMQ_PASSWORD:cambiar_esta_clave}
```

Si corres el servicio desde el IDE y tu `.env` tiene otra contraseña de RabbitMQ, define `RABBITMQ_PASSWORD` en la
configuración de ejecución.

### G5. Identidad y roles

El Gateway ya validó el token y te manda la identidad en cabeceras. Borra las que intente mandar el cliente, así que **puedes confiar en ellas**:

| Cabecera | Contenido | Tipo en Java |
|---|---|---|
| `X-User-Id` | Id del usuario en ms-auth (ObjectId en hexadecimal, 24 caracteres) | `String` |
| `X-User-Role` | `ADMIN` o `VENDEDOR` | `Rol` (enum) |

**No vuelvas a validar el JWT** (no tienes la clave ni la necesitas). Declara las dos cabeceras en **todo endpoint
protegido**: si faltan, la petición no pasó por el Gateway y tu servicio responde 401 (G6). Ponlas **antes** del
`@RequestBody`: Spring resuelve los parámetros en orden, y así la falta de identidad (401) se detecta antes que un
cuerpo inválido (400).

```java
// model/Rol.java: cada servicio declara el suyo (no hay librería compartida entre servicios)
public enum Rol { ADMIN, VENDEDOR }
```

```java
// security/UsuarioActual.java
/** Quién hace la petición, según las cabeceras que el Gateway sacó del token. */
public record UsuarioActual(String id, Rol rol) {
    public boolean esAdmin() {
        return rol == Rol.ADMIN;
    }
}
```

```java
// exception/AccesoDenegadoException.java  ➔ 403 (G6)
public class AccesoDenegadoException extends RuntimeException {
    public AccesoDenegadoException(String mensaje) {
        super(mensaje);
    }
}
```

```java
// controller/ProductoController.java: delgado, solo traduce HTTP y delega
@RestController
@RequestMapping("/inventario/productos")            // prefijo COMPLETO: el Gateway no lo quita
@RequiredArgsConstructor
public class ProductoController {

    private final ProductoService productoService;

    @PostMapping
    public ResponseEntity<ProductoResponse> crear(@RequestHeader("X-User-Id") String usuarioId,
                                                  @RequestHeader("X-User-Role") Rol rol,
                                                  @Valid @RequestBody ProductoRequest request) {
        ProductoResponse creado = productoService.crear(request, new UsuarioActual(usuarioId, rol));
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }
}
```

```java
// service/ProductoService.java: la regla de rol vive en el servicio
@Transactional
public ProductoResponse crear(ProductoRequest request, UsuarioActual ejecutor) {
    if (!ejecutor.esAdmin()) {                                   // H-020: alta y modificación exigen ADMIN
        throw new AccesoDenegadoException("Solo un ADMIN puede dar de alta productos");
    }
    // ... construir el documento, guardar y devolver ProductoResponse.from(guardado)
}
```

### G6. Errores (RFC 9457)

Toda respuesta de error es `application/problem+json` con esta forma. Es la que ya devuelven ms-auth y el 401 del
Gateway; sus 404 y 503 tienen otro formato, ver el [README del Gateway](../api-gateway/README.md):

```json
{"detail":"Solo un ADMIN puede dar de alta productos","instance":"/inventario/productos","status":403,"title":"Acceso denegado","requestId":"e2e-gateway-001"}
```

`type` no aparece porque vale `about:blank`, y RFC 9457 trata su ausencia como ese valor. `requestId` es el
`X-Request-ID` de la petición.

| Situación | Código | `title` sugerido | Extra |
|---|---|---|---|
| `@Valid` falla | 400 | Datos inválidos | `campos`: mapa campo ➔ mensaje |
| JSON mal formado o tipo incorrecto | 400 | Cuerpo de la petición inválido | — |
| Parámetro, cabecera o variable de ruta con tipo incorrecto | 400 | Parámetro inválido | — |
| Faltan `X-User-*` (no pasó por el Gateway) | 401 | No autenticado | — |
| El rol no alcanza | 403 | Acceso denegado | — |
| No existe el recurso o la ruta | 404 | Recurso no encontrado | — |
| Índice único violado (`DuplicateKeyException`) | 409 | Registro duplicado | No expongas el mensaje de MongoDB |
| Inesperado | 500 | Error interno | Detalle **solo** en el log |

Base para copiar, reducida del `GlobalExceptionHandler` de ms-auth. **No extiende** `ResponseEntityExceptionHandler`,
porque ya trae handlers para las mismas excepciones y el mapeo sería ambiguo:

```java
package com.monpoint.<servicio>.controller;

import com.monpoint.<servicio>.audit.RequestIdFilter;
import com.monpoint.<servicio>.exception.AccesoDenegadoException;
import com.monpoint.<servicio>.exception.RecursoNoEncontradoException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail datosInvalidos(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> campos = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        error -> Objects.requireNonNullElse(error.getDefaultMessage(), "Valor inválido"),
                        (primero, segundo) -> primero + "; " + segundo,
                        TreeMap::new));
        ProblemDetail problema = problema(HttpStatus.BAD_REQUEST, "Datos inválidos",
                "Uno o más campos no cumplen las validaciones", request);
        problema.setProperty("campos", campos);
        return problema;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail cuerpoIlegible(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return problema(HttpStatus.BAD_REQUEST, "Cuerpo de la petición inválido",
                "El cuerpo de la petición falta o no es un JSON válido", request);
    }

    /** Sin X-User-Id / X-User-Role la petición no pasó por el Gateway: 401. Otra cabecera faltante: 400. */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ProblemDetail cabeceraFaltante(MissingRequestHeaderException ex, HttpServletRequest request) {
        if (ex.getHeaderName().startsWith("X-User-")) {
            return problema(HttpStatus.UNAUTHORIZED, "No autenticado",
                    "Se requiere un token válido. Inicia sesión para obtener uno.", request);
        }
        return problema(HttpStatus.BAD_REQUEST, "Cabecera faltante",
                "Falta la cabecera " + ex.getHeaderName(), request);
    }

    /**
     * Parámetro, cabecera o variable de ruta con el tipo equivocado (p. ej. X-User-Role: OTRO, o letras en un
     * @PathVariable numérico). Sin este handler, el de Exception.class lo convertiría en un 500.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail tipoInvalido(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return problema(HttpStatus.BAD_REQUEST, "Parámetro inválido",
                "El valor de '" + ex.getName() + "' no tiene el formato esperado", request);
    }

    @ExceptionHandler(AccesoDenegadoException.class)
    public ProblemDetail accesoDenegado(AccesoDenegadoException ex, HttpServletRequest request) {
        return problema(HttpStatus.FORBIDDEN, "Acceso denegado", ex.getMessage(), request);
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ProblemDetail noEncontrado(RecursoNoEncontradoException ex, HttpServletRequest request) {
        return problema(HttpStatus.NOT_FOUND, "Recurso no encontrado", ex.getMessage(), request);
    }

    /** Índice único violado. El mensaje de MongoDB trae detalles internos: no se expone. */
    @ExceptionHandler(DuplicateKeyException.class)
    public ProblemDetail duplicado(DuplicateKeyException ex, HttpServletRequest request) {
        return problema(HttpStatus.CONFLICT, "Registro duplicado",
                "Ya existe un registro con ese valor único", request);
    }

    /** Red de seguridad: las excepciones de Spring MVC conservan su 4xx; lo demás es 500 opaco con requestId. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> inesperado(Exception ex, HttpServletRequest request) {
        if (ex instanceof ErrorResponse errorResponse) {
            ProblemDetail problema = errorResponse.getBody();
            problema.setInstance(URI.create(request.getRequestURI()));
            problema.setProperty("requestId", RequestIdFilter.obtener(request));
            return ResponseEntity.status(errorResponse.getStatusCode())
                    .headers(errorResponse.getHeaders())
                    .body(problema);
        }
        log.error("Error inesperado en {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.internalServerError().body(problema(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno",
                "Ocurrió un error inesperado. Si persiste, repórtalo con el requestId.", request));
    }

    private static ProblemDetail problema(HttpStatus status, String titulo, String detalle,
                                          HttpServletRequest request) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalle);
        problema.setTitle(titulo);
        problema.setInstance(URI.create(request.getRequestURI()));
        problema.setProperty("requestId", RequestIdFilter.obtener(request));
        return problema;
    }
}
```

### G7. Trazabilidad (`X-Request-ID`)

El Gateway garantiza un `X-Request-ID` por petición y lo reenvía. Este filtro, copiado de ms-auth, lo reutiliza (o
genera uno si no viene), lo pone en el MDC para los logs (G4: `logging.pattern.level`) y en la respuesta. El
`GlobalExceptionHandler` lo lee con `RequestIdFilter.obtener(request)`.

```java
package com.monpoint.<servicio>.audit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-ID";
    public static final String MDC_KEY = "requestId";

    private static final String ATRIBUTO = RequestIdFilter.class.getName() + ".requestId";
    /** Descarta valores con saltos de línea u otros caracteres que permitirían inyectar texto en logs o cabeceras. */
    private static final Pattern FORMATO_VALIDO = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = resolver(request.getHeader(HEADER));
        request.setAttribute(ATRIBUTO, requestId);
        response.setHeader(HEADER, requestId);
        MDC.put(MDC_KEY, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    public static String obtener(HttpServletRequest request) {
        Object requestId = request.getAttribute(ATRIBUTO);
        return requestId != null ? requestId.toString() : resolver(request.getHeader(HEADER));
    }

    private static String resolver(String recibido) {
        return recibido != null && FORMATO_VALIDO.matcher(recibido).matches()
                ? recibido
                : UUID.randomUUID().toString();
    }
}
```

Al publicar un evento, incluye el `requestId` en los logs del productor y del consumidor, para seguir la cadena completa.

### G8. Persistencia (MongoDB)

- **Colecciones, campos, tipos e índices:** [`docs/modelo-datos/README.md`](../docs/modelo-datos/README.md). Copia los nombres exactos (camelCase).
- **Tipos:** `ObjectId` ➔ `String` con `@Id`; `Decimal128` ➔ `BigDecimal` **con** `@Field(targetType = FieldType.DECIMAL128)`; `Date` ➔ `Instant` (UTC). Sin la anotación, Spring guarda el importe **como texto**.
- **Índices:** `@Indexed` / `@Indexed(unique = true)` / `@CompoundIndex` en el documento, más `spring.data.mongodb.auto-index-creation=true` (G4). Verificado: así se creó el índice único de `correo` en `auth_db`.
- **Transacciones:** `@Transactional` en el servicio. Funciona porque MongoDB es un replica set y porque existe este bean (copiado de ms-auth):

```java
@Configuration
@EnableMongoAuditing                      // habilita @CreatedDate y @LastModifiedDate
public class MongoConfig {

    @Bean
    public MongoTransactionManager transactionManager(MongoDatabaseFactory dbFactory) {
        return new MongoTransactionManager(dbFactory);
    }
}
```

Documento de ejemplo, con los campos del Doc 04 §5.1:

```java
@Document(collection = "productos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)   // lo usa Spring Data al leer
public class Producto {

    @Id
    private String id;

    private String nombre;
    private String descripcion;

    @Field(targetType = FieldType.DECIMAL128)        // RNF-08: importe exacto, nunca texto ni double
    private BigDecimal precio;

    private int existencia;
    private int umbralStock;

    @Indexed                                         // Doc 04 §11: excluir inactivos del listado
    private boolean activo;

    @CreatedDate
    private Instant fechaRegistro;

    @LastModifiedDate
    private Instant fechaActualizacion;

    // fábrica y métodos de dominio (p. ej. darDeBaja()) según tu diseño
}
```

### G9. DTOs (records)

- Petición y respuesta son `record` en `dto/request` y `dto/response`. **El documento nunca sale del servicio.**
- Los campos salen del Doc 04 y de los criterios de la historia. No agregues campos por iniciativa propia.
- Validación con Jakarta Validation en la petición (`@NotBlank`, `@NotNull`, `@PositiveOrZero`, `@Email`...). Un fallo da 400 con `campos` (G6).
- **Nombres en español y camelCase**, iguales al modelo: `correo` (nunca `email`), `fechaRegistro`, `precioUnitario`.
- Importes: `BigDecimal` (en JSON, un número exacto). Fechas: `Instant` (en JSON, ISO-8601 en UTC). Ids: `String`.
- La respuesta se arma con un método estático `from(documento)`.

Ejemplo derivado del Doc 04 §5.1 y de H-020 (alta de productos):

```java
public record ProductoRequest(
        @NotBlank(message = "El nombre es obligatorio") String nombre,
        String descripcion,
        @NotNull @PositiveOrZero(message = "El precio no puede ser negativo") BigDecimal precio,
        @NotNull @PositiveOrZero(message = "La existencia no puede ser negativa") Integer existencia,
        @PositiveOrZero Integer umbralStock) {            // null ➔ valor por omisión al crear (RF-2.6)
}

public record ProductoResponse(String id, String nombre, String descripcion, BigDecimal precio,
                               int existencia, int umbralStock, boolean activo,
                               Instant fechaRegistro, Instant fechaActualizacion) {

    public static ProductoResponse from(Producto p) {
        return new ProductoResponse(p.getId(), p.getNombre(), p.getDescripcion(), p.getPrecio(),
                p.getExistencia(), p.getUmbralStock(), p.isActivo(),
                p.getFechaRegistro(), p.getFechaActualizacion());
    }
}
```

### G10. Eventos (RabbitMQ)

**Lo que ya está decidido** (Doc 03 §2.3, H-006 y el diagrama oficial):

| Elemento | Definición |
|---|---|
| Sobre común de todo evento | `eventoId` (UUID en texto) · `tipo` (nombre del evento, p. ej. `VentaCreada`) · `version` · `fecha` (ISO-8601 UTC) · `origen` (servicio productor, p. ej. `ms-ventas`) · `datos` (contenido del evento) |
| Exchanges | Uno de tipo **topic** por dominio: `inventario.eventos`, `clientes.eventos`, `ventas.eventos` |
| Colas | Una por consumidor, **cada una con su cola de mensajes fallidos**, y reintento compartido con un máximo de intentos |
| Idempotencia | Antes de aplicar un evento, busca su `eventoId` en `eventos_procesados` (índice único). Si ya está, no hagas nada. Si no, aplica el efecto y registra el `eventoId` **en la misma transacción** |
| Orden | No está garantizado: réplicas y proyecciones descartan un evento con `fechaActualizacion` anterior a la guardada |
| Outbox | **Recortado** (Doc 01 §7.4): publica con `RabbitTemplate` después de confirmar la transacción |

**Quién publica y quién consume** (diagrama oficial):

| Servicio | Publica | Consume |
|---|---|---|
| ms-auth | — (fuera del bus) | — |
| ms-clientes | `ClienteCreado`, `ClienteActualizado` | — |
| ms-inventario | `ProductoCreado`, `ProductoActualizado`, `ProductoDadoDeBaja`, `StockReservado`, `StockRechazado`, `StockBajo` | `VentaCreada` |
| ms-ventas | `VentaCreada`, `VentaConfirmada`, `VentaCancelada` | `Producto*`, `Cliente*`, `StockReservado`, `StockRechazado` |
| ms-reportes | — | `Producto*`, `Cliente*`, `VentaConfirmada`, `VentaCancelada` |
| ms-notificaciones | — | `StockBajo`, `VentaConfirmada`, `VentaCancelada` |

> ⛔ **Lo que todavía NO está decidido:** los campos de `datos` de cada evento, las routing keys, los nombres de
> las colas, el formato de `version` y la configuración de reintentos. Los define el **catálogo de eventos**
> (`docs/eventos/`, H-008, lo aprueba el Product Owner), que **aún está vacío**. **No los inventes.** Mientras
> tanto, puedes construir la lógica del servicio y probar consumidores publicando mensajes a mano desde la
> consola de RabbitMQ (http://localhost:15672).

### G11. Spring Security en servicios de dominio

La autenticación ya ocurrió en el Gateway; tu servicio **no** valida tokens. Hasta que el Product Owner fije un criterio común:

- **Si no incluyes `spring-boot-starter-security`:** el control de acceso es el de G5 (cabeceras ➔ `UsuarioActual` ➔ regla en el servicio ➔ 403).
- **Si lo incluyes** (Spring Initializr lo agrega si lo marcas), tu `SecurityFilterChain` debe ser **sin estado** y sin `httpBasic` ni `formLogin`, y **no bloquear** las rutas: el control de rol sigue siendo el de G5. Sin una `SecurityFilterChain` propia, Spring Security pide usuario y contraseña en todas las rutas y el Gateway recibe 401.
- En las pruebas `@WebMvcTest`, importa tu configuración de seguridad (`@Import(SecurityConfig.class)`): el *slice* no carga clases `@Configuration` y aplicaría la seguridad por omisión.

### G12. Docker y Compose

`Dockerfile` (dos etapas, igual que ms-auth). Reemplaza `<servicio>` y `<puerto>`:

```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -q -B package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 monpoint
COPY --from=build /app/target/ms-<servicio>-*.jar app.jar
USER monpoint
EXPOSE <puerto>
HEALTHCHECK --interval=10s --timeout=5s --start-period=40s --retries=10 \
  CMD curl -fs http://localhost:${SERVER_PORT:-<puerto>}/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Copia también el `.dockerignore` de ms-auth (solo envía `pom.xml` y `src/` al build).

Bloque para `infra/docker-compose.yml`, dentro de `services:` (la plantilla comentada del archivo es la misma):

```yaml
  ms-<servicio>:
    build: ../ms-<servicio>
    container_name: monpoint-<servicio>
    environment:
      SERVER_PORT: <puerto>
      EUREKA_URL: http://eureka-server:8761/eureka/
      MONGO_URI: mongodb://<servicio>_user:<servicio>_pass@mongodb:27017/<servicio>_db?replicaSet=rs0&authSource=<servicio>_db
      RABBITMQ_HOST: rabbitmq
      RABBITMQ_USER: ${RABBITMQ_USER}
      RABBITMQ_PASSWORD: ${RABBITMQ_PASSWORD}
    depends_on:
      mongodb:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy
      eureka-server:
        condition: service_healthy
    stop_grace_period: 15s       # sin esto Docker te mata antes de darte de baja en Eureka
    networks:
      - monpoint-red             # red interna: así resuelves "mongodb", "rabbitmq", "eureka-server"
```

- **Sin `ports`.** Solo el Gateway publica (8080). Si tu servicio responde en `localhost:808x`, se rompe H-002.
- Dentro de Docker, la URI de Mongo va **sin** `directConnection=true`. Fuera de Docker es obligatoria.
- El *healthcheck* lo trae tu `Dockerfile`. Los demás esperan con `condition: service_healthy`.
- **Antes de fusionar a `main`:** `docker compose up -d --build` debe seguir levantando todo, y los servicios que ya existían deben seguir registrándose en Eureka.

### G13. Pruebas

| Tipo | Herramienta | Notas |
|---|---|---|
| Unitarias de servicio | JUnit 5 + Mockito (`@ExtendWith(MockitoExtension.class)`) | Sin contexto de Spring ni base de datos |
| Controlador y errores | `@WebMvcTest(MiController.class)` + `MockMvc` + `@MockitoBean` | Manda `X-User-Id` / `X-User-Role` como lo haría el Gateway |
| Integración | `@SpringBootTest(properties = "eureka.client.enabled=false")` | Con `docker compose up -d mongodb` |

Imports de Boot 4 (verificados en ms-auth):

```java
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;   // @MockBean ya no existe
import org.springframework.test.web.servlet.MockMvc;
```

```java
// Imports estáticos: Mockito.when, ArgumentMatchers.any,
// MockMvcRequestBuilders.post, MockMvcResultMatchers.status / jsonPath
@WebMvcTest(ProductoController.class)
class ProductoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductoService productoService;

    @Test
    void crear_ComoVendedor_Responde403ConProblemDetail() throws Exception {
        when(productoService.crear(any(), any()))
                .thenThrow(new AccesoDenegadoException("Solo un ADMIN puede dar de alta productos"));

        mockMvc.perform(post("/inventario/productos")
                        .header("X-User-Id", "6abf387781233486cd272c8e")
                        .header("X-User-Role", "VENDEDOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Aceite 1L","precio":45.50,"existencia":10}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Acceso denegado"))
                .andExpect(jsonPath("$.requestId").exists());
    }

    @Test
    void crear_SinCabecerasDeIdentidad_Responde401() throws Exception {
        mockMvc.perform(post("/inventario/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
```

Usa nombres de prueba descriptivos (`metodo_Condicion_Resultado`) y nunca JUnit 4.

### G14. Trampas de Spring Boot 4 y Spring Cloud 2025

Todas costaron tiempo de verdad en este proyecto:

| Síntoma | Causa | Solución |
|---|---|---|
| La app arranca, pero escribe en `localhost/test` | `spring.data.mongodb.uri` ya no se lee en Boot 4 | `spring.mongodb.uri` |
| `Timeout` al conectar a Mongo desde el IDE | El replica set anuncia `mongodb:27017`, que solo existe en Docker | `directConnection=true` en la URI local |
| `Transaction numbers are only allowed on a replica set` | Mongo no se inicializó como replica set | `docker compose logs mongodb`; si hace falta, `down -v` y volver a levantar |
| `@MockBean` no compila | Se eliminó | `@MockitoBean` (`org.springframework.test.context.bean.override.mockito`) |
| `@WebMvcTest` no se encuentra | Cambió de paquete | `org.springframework.boot.webmvc.test.autoconfigure` |
| No encuentra `com.fasterxml.jackson...` en tu código | Boot 4 usa Jackson 3 | Paquetes `tools.jackson.*` |
| El importe aparece como texto en Mongo | `BigDecimal` sin anotación | `@Field(targetType = FieldType.DECIMAL128)` |
| Un parámetro con tipo incorrecto (p. ej. letras en un `@PathVariable long`) da **500** | El `@ExceptionHandler(Exception.class)` atrapa `MethodArgumentTypeMismatchException`, que no es `ErrorResponse` | Handler específico que responda 400 (G6). Verificado al probar esta guía |
| Un duplicado no da 409 | El índice no se creó | `@Indexed(unique = true)` y `spring.data.mongodb.auto-index-creation=true` |
| Spring Cloud aborta el arranque por incompatibilidad | BOM que no corresponde a Boot 4.1 | Spring Cloud **2025.1.3** (no `2026.0.0-M1`, que apunta a Boot 4.2) |
| El servicio no aparece en Eureka o el Gateway da 503 | `spring.application.name` distinto del de la ruta | Nombres exactos de G3; espera unos 20 s tras registrarte |
| Queda `DOWN` en Eureka tras `docker compose stop` | Docker lo mató antes de mandar la baja | `stop_grace_period: 15s` |
| Las pruebas tardan y llenan el log de `Connection refused` | Intentan registrarse en Eureka | `eureka.client.enabled=false` en la prueba |
| `@Value` no llega al constructor con `@RequiredArgsConstructor` | Lombok no copia `@Value` al constructor | Constructor explícito, o un record `@ConfigurationProperties` |
| Advertencia de Mockito sobre *self-attaching* | El JDK 21 avisa que dejará de permitirlo | `-javaagent` de Mockito en Surefire (ver el `pom.xml` de ms-auth) |

### G15. Definición de terminado (Doc 01 §10.1)

- [ ] Criterios de aceptación de la historia cumplidos **sin excepción**
- [ ] Validación de entrada en todos los endpoints y códigos HTTP correctos
- [ ] Registrado en Eureka y accesible **a través del Gateway**
- [ ] Endpoints protegidos y públicos diferenciados; **el rol se verifica en el servicio**
- [ ] Publica y consume exactamente los eventos del catálogo, con su esquema; consumidores idempotentes; los mensajes fallidos terminan en su cola de fallidos
- [ ] **Ninguna llamada HTTP a otro servicio**
- [ ] Solo su propia base y su propio usuario; importes en `Decimal128`; índices declarados; sin credenciales en el código
- [ ] `Dockerfile` funcional; levanta con `docker compose up`; variables de entorno externalizadas
- [ ] Al menos una prueba automatizada por endpoint y por consumidor principal
- [ ] Endpoints y eventos documentados en el README del servicio, con instrucciones para correrlo
- [ ] Revisado por el revisor asignado (Doc 01 §8.1)

### G16. Ramas, commits y PR (`CONTRIBUTING.md`)

- Rama: `<tipo>/<actividad>-<descripcion>` (ej. `feat/3.2-ms-inventario-productos`, `infra/2.1-rabbitmq-topologia`).
- Commit: `<actividad> <descripción en presente, sin punto final>` (ej. `3.2 Agrega el alta de productos`).
- PR a `main` con la plantilla; se fusiona de inmediato y se revisa después. **Quien fusiona responde por que `docker compose up` siga levantando.**

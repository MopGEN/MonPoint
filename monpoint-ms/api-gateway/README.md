# api-gateway

Punto único de entrada de MonPoint (H-002, H-003, H-009; actividades 1.3, 2.3 y 2.4). Es el **único componente
abierto a la red** (8080). El cliente web y Postman le hablan a él, y él reenvía a cada servicio.

Hace tres cosas:

1. **Enruta por prefijo** al servicio registrado en Eureka (`lb://<servicio>`), sin direcciones fijas.
2. **Valida el JWT localmente**, con la misma clave que ms-auth y sin llamarle, y propaga la identidad.
3. **Responde CORS** para el cliente web.

Contrato del token: [`ms-auth/docs/CONTRATO_TOKEN.md`](../ms-auth/docs/CONTRATO_TOKEN.md). Cómo construir un servicio
que funcione detrás del Gateway: [guía de ms-auth](../ms-auth/README.md#guía-para-tu-microservicio).

> **Si eres una IA generando un servicio de MonPoint:** el Gateway ya existe y **no se modifica** para agregar un
> servicio de dominio; su ruta ya está declarada. Tu servicio: (1) se registra en Eureka con el nombre exacto de la
> tabla de [rutas](#rutas); (2) expone sus endpoints **con el prefijo completo**; (3) lee la identidad de
> `X-User-Id` / `X-User-Role` **sin validar el JWT**; (4) verifica el rol él mismo y responde 403 en `ProblemDetail`.

---

## Qué hace y cómo funciona

### Propósito
Es la **puerta del sistema**: concentra lo que el navegador necesita (una sola dirección, autenticación y CORS) para
que los servicios de dominio no tengan que resolverlo cada uno. No guarda datos ni tiene lógica de negocio.

### Patrón
- **API Gateway / edge service**, reactivo (Spring Cloud Gateway 5.0 sobre WebFlux y Netty).
- **Descubrimiento del lado del cliente:** el Gateway lee el registro de Eureka y elige una instancia `UP` (`lb://`).
- **Validación de token sin estado:** verifica la firma HS256 y la vigencia con la clave compartida; nunca consulta a ms-auth.
- **Anti-suplantación:** la identidad que reciben los servicios solo la pone el Gateway.

### Flujo de una petición

```
Navegador / Postman ──HTTP──► :8080
  1. RequestIdWebFilter        WebFilter, va primero. Asegura un X-Request-ID: el del cliente si es válido
                               (letras, dígitos, . _ -, hasta 64) o un UUID nuevo
  2. CorsWebFilter             Responde el preflight OPTIONS aquí mismo. Origen no permitido ➔ 403
  3. ¿Coincide con una ruta?   /auth/**, /inventario/**, ... ➔ si no, 404
  4. JwtAuthenticationGlobalFilter (orden −100)
       · borra siempre los X-User-Id / X-User-Role que mande el cliente
       · POST /auth/login y OPTIONS pasan sin token
       · sin token o token inválido ➔ 401 ProblemDetail, y la petición NO llega al servicio
       · token válido ➔ agrega X-User-Id y X-User-Role sacados del token
  5. ReactiveLoadBalancerClientFilter   lb://ms-<servicio> ➔ una instancia UP según Eureka (sin instancias ➔ 503)
  6. NettyRoutingFilter        reenvía con la ruta COMPLETA (sin StripPrefix) y devuelve la respuesta del servicio
```

### Tecnologías y librerías

| Librería | Versión | Para qué |
|---|---|---|
| `spring-cloud-starter-gateway-server-webflux` | Spring Cloud 2025.1.3 (Gateway 5.0) | Enrutamiento reactivo. Es el "Reactive Gateway" de Spring Initializr |
| `spring-cloud-starter-netflix-eureka-client` | Spring Cloud 2025.1.3 | Registro como `API-GATEWAY` y lectura del registro; trae Spring Cloud LoadBalancer para `lb://` |
| `jjwt-api` / `-impl` / `-jackson` | 0.12.6 | Validar el JWT (mismo contrato que ms-auth) |
| `spring-boot-starter-actuator` | Boot 4.1.1 | `/actuator/health` para Docker y Eureka |
| Lombok | gestionada por Boot | `@RequiredArgsConstructor`, `@Slf4j` |

| Clase | Qué hace |
|---|---|
| `filter/RequestIdWebFilter` | `X-Request-ID` en la petición reenviada y en la respuesta (una sola vez) |
| `config/CorsConfig` | `CorsWebFilter` con los orígenes de `CORS_ORIGENES` |
| `filter/JwtAuthenticationGlobalFilter` | Exenciones, anti-suplantación, propagación de identidad y 401 |
| `security/JwtValidador` | Verifica firma HS256, vigencia y los claims `sub`, `userId` y `rol` ∈ {`ADMIN`, `VENDEDOR`} |
| `security/JwtProperties` | `jwt.secret`; no arranca con menos de 32 bytes |

---

## Rutas

| Prefijo | Destino (`spring.application.name` del servicio) | Puerto interno | Estado |
|---|---|---|---|
| `/auth/**` | `lb://ms-auth` | 8081 | Operativo |
| `/inventario/**` | `lb://ms-inventario` | 8082 | 503 hasta que el servicio se registre en Eureka |
| `/ventas/**` | `lb://ms-ventas` | 8083 | 503 hasta que el servicio se registre en Eureka |
| `/clientes/**` | `lb://ms-clientes` | 8084 | 503 hasta que el servicio se registre en Eureka |
| `/reportes/**` | `lb://ms-reportes` | 8085 | 503 hasta que el servicio se registre en Eureka |
| `/notificaciones/**` | `lb://ms-notificaciones` | 8086 | 503 hasta que el servicio se registre en Eureka |
| Cualquier otro | — | — | 404 |

**Por qué sin `StripPrefix`:** el servicio recibe la ruta completa (`/inventario/productos`, no `/productos`). Así,
la URL que ve el cliente es la misma que atiende el servicio; los logs y los `instance` de los errores coinciden en
los dos lados, y ningún servicio puede "chocar" con la ruta de otro. Por eso **cada servicio declara su prefijo en
el `@RequestMapping`**.

- El destino se resuelve por el **nombre** registrado en Eureka. Si tu `spring.application.name` no coincide, el Gateway responde 503.
- Las rutas viven en `spring.cloud.gateway.server.webflux.routes` (Gateway 5.0). El prefijo antiguo `spring.cloud.gateway.routes` se ignora **sin dar error**.
- Las rutas están fijas y solo se cargan las declaradas. Un prefijo nuevo es una decisión del Product Owner: se agrega en `src/main/resources/application.properties` y se actualiza la prueba `ApiGatewayApplicationTests`, que exige exactamente las rutas declaradas.

## Qué recibe cada servicio

| Cabecera | Valor | Cómo leerla (Spring MVC) |
|---|---|---|
| `X-User-Id` | Claim `userId` del token (ObjectId del usuario en ms-auth, en hexadecimal) | `@RequestHeader("X-User-Id") String usuarioId` |
| `X-User-Role` | Claim `rol`: `ADMIN` o `VENDEDOR` | `@RequestHeader("X-User-Role") Rol rol` |
| `X-Request-ID` | El del cliente si es válido, o un UUID nuevo. También vuelve en la respuesta | Filtro de la guía (G7) |
| `Authorization` | Se conserva tal cual (ms-auth vuelve a validar el token) | Los servicios de dominio no lo usan |

**Anti-suplantación:** el Gateway **elimina siempre** los `X-User-Id` y `X-User-Role` que mande el cliente, también en
las rutas públicas. Un servicio puede confiar en esas cabeceras porque solo las pone el Gateway, y porque los
servicios no publican puertos (nadie puede saltarse el Gateway).

## Qué exige token

| Petición | Token |
|---|---|
| `POST /auth/login` | No |
| `OPTIONS` (verificación previa de CORS) | No |
| `GET /actuator/health` (del Gateway) | No |
| Todo lo demás que coincida con una ruta | **Sí**: `Authorization: Bearer <token>` (el prefijo `Bearer` no distingue mayúsculas) |

## Errores que responde el propio Gateway

Verificado contra el Gateway en ejecución:

| Código | Cuándo | Cuerpo |
|---|---|---|
| **401** | Sin token, o con uno vencido, alterado, sin firma (`alg: none`), sin `userId` o con un rol desconocido | `application/problem+json`, igual que ms-auth, con `WWW-Authenticate: Bearer` |
| **403** | CORS: origen que no está en `CORS_ORIGENES` | **Sin cuerpo** |
| **404** | Prefijo no declarado | `application/json` de Spring Boot: `{"timestamp","path","status","error","requestId"}` |
| **503** | Ruta declarada, pero el servicio no tiene instancias `UP` en Eureka | Igual que el 404 |

```http
HTTP/1.1 401 Unauthorized
WWW-Authenticate: Bearer
Content-Type: application/problem+json

{"detail":"Se requiere un token válido. Inicia sesión para obtener uno.","instance":"/auth/me","status":401,"title":"No autenticado","requestId":"…"}
```

> ⚠️ En los 404 y 503, el `requestId` **del cuerpo** es un id interno de WebFlux, **no** el `X-Request-ID`. Para
> correlacionar, usa la **cabecera** `X-Request-ID` de la respuesta. Unificarlos a `ProblemDetail` es una mejora
> pendiente; el contrato solo fija el formato del 401.

Los errores de un servicio (400, 403 por rol, 404 de un recurso, 409...) los genera **el servicio**, no el Gateway,
y llegan tal cual al cliente.

## Qué garantiza el Gateway y qué no

| Garantiza | No hace (le toca a cada servicio o queda fuera) |
|---|---|
| Firma HS256 válida, token vigente, claims completos y rol conocido | **Verificar si el rol alcanza** para la operación (403) |
| Identidad confiable en `X-User-*` (borra las del cliente) | Revocar tokens: un token sigue valiendo hasta su `exp` (24 h) |
| Un `X-Request-ID` por petición | Validar el cuerpo de las peticiones |
| CORS solo para orígenes permitidos | Limitar la tasa de peticiones (*rate limiting*) |

## CORS

- **Orígenes permitidos:** vienen de `CORS_ORIGENES` (en `infra/.env`). Un origen fuera de la lista recibe 403.
- **Métodos:** `GET, POST, PUT, PATCH, DELETE, OPTIONS`.
- **Cabeceras:** `Authorization`, `Content-Type` y `X-Request-ID`. `X-Request-ID` queda expuesta al navegador.
- **El preflight se responde antes de validar el token**, así que nunca da 401.
- **Los 401 también llevan cabeceras CORS**, para que el cliente web pueda leerlos y cerrar la sesión.
- **Tu servicio no configura CORS:** el navegador solo habla con el Gateway.

## Qué debe hacer tu microservicio para funcionar detrás del Gateway

1. **Tu ruta ya existe.** No hace falta tocar el Gateway; basta con que te registres en Eureka con el nombre de la tabla (ver [`eureka-server/README.md`](../eureka-server/README.md)).
2. **Usa tu prefijo completo en los controladores**, porque el Gateway no lo quita:
   ```java
   @RestController
   @RequestMapping("/inventario/productos")   // no "/productos"
   ```
3. **Lee la identidad de las cabeceras**, sin volver a validar el token. Decláralas **antes** del `@RequestBody`, para que la falta de identidad (401) se detecte antes que un cuerpo inválido (400):
   ```java
   @PostMapping
   public ResponseEntity<ProductoResponse> crear(@RequestHeader("X-User-Id") String usuarioId,
                                                 @RequestHeader("X-User-Role") Rol rol,
                                                 @Valid @RequestBody ProductoRequest request) { ... }
   ```
   **Tú decides qué rol puede hacer qué** y respondes 403 en `ProblemDetail` si no alcanza. Código completo y probado: guía G5 y G6.
4. **Propaga el `X-Request-ID`** en tus logs y en tus errores (guía G7).
5. **No publiques puertos** en el compose: si tu servicio responde en `localhost:808x` desde tu máquina, se rompe el criterio "los servicios no son accesibles desde fuera" (H-002), y cualquiera podría saltarse al Gateway y mandarte `X-User-*` falsos.
6. **Mientras no estés registrado**, tu ruta responde **503**. Al registrarte, el Gateway tarda unos 20 s en verte.
7. **Prueba siempre a través del Gateway:** `http://localhost:8080/<tu-prefijo>/...` con `Authorization: Bearer <token>` (pídelo en `POST /auth/login`).

## Variables de entorno

| Variable | Por omisión | Para qué |
|---|---|---|
| `SERVER_PORT` | `8080` | Puerto HTTP |
| `EUREKA_URL` | `http://localhost:8761/eureka/` | Servidor Eureka |
| `JWT_SECRET` | Marcador de `infra/.env.example` | Clave HS256 en texto plano UTF-8, al menos 32 caracteres, **idéntica a la de ms-auth**. Con menos, el Gateway no arranca. **Cambia el valor de ejemplo**: el repositorio es público |
| `CORS_ORIGENES` | `http://localhost:5173` | Orígenes permitidos, separados por comas |

## Cómo levantarlo

```bash
cd monpoint-ms/infra && docker compose up -d --build       # el sistema completo
```

Un servicio nuevo tarda unos 20 s en ser enrutable después de registrarse. El Gateway refresca su lista de Eureka
cada 10 s y la guarda en caché otros 10 s; mientras tanto responde 503.

En local, fuera de Docker: `cd monpoint-ms/api-gateway && ./mvnw spring-boot:run`. Necesita Eureka en `localhost:8761`
y el mismo `JWT_SECRET` que ms-auth.

## Pruebas

```bash
cd monpoint-ms/api-gateway && ./mvnw test
```

Son 27 pruebas y no necesitan Docker, Eureka ni ms-auth.

| Clase | Qué comprueba |
|---|---|
| `ApiGatewayApplicationTests` | Se cargan las 6 rutas de `application.properties`, todas `lb://` y sin `StripPrefix` |
| `JwtValidadorTest` | Token válido, vencido, de otra clave, alterado, sin firma, sin `userId`, con rol desconocido, vacío, y clave corta |
| `GatewayIntegracionTest` | El Gateway real frente a un servicio de destino falso que devuelve lo que recibió: rutas públicas, 401, identidad propagada, anti-suplantación, CORS, 404 y `X-Request-ID` |

En macOS, las pruebas imprimen un `ERROR` de Netty sobre `MacOSDnsServerAddressStreamProvider`. Es inofensivo:
usa el DNS del sistema. En Docker, que es Linux, no aparece.

## Verificación (2026-10-10)

- `./mvnw test`: 27 pruebas, 0 fallos. Prueba de mutación: si se quita la eliminación de `X-User-*`, falla la prueba de anti-suplantación.
- `docker compose up -d --build`: mongodb, rabbitmq, eureka-server, api-gateway y ms-auth, los 5 *healthy*. `API-GATEWAY` y `MS-AUTH` en `UP` en Eureka.
- Por `http://localhost:8080`:
  - login ➔ 200;
  - `/auth/me` sin token ➔ 401 del Gateway; con token ➔ 200; con token alterado ➔ 401;
  - `/noexiste` ➔ 404; `/inventario/…` ➔ 503 (servicio aún no registrado);
  - preflight desde `localhost:5173` ➔ 200 con cabeceras CORS; desde un origen ajeno ➔ 403 sin cuerpo;
  - `X-Request-ID: e2e-gateway-001` vuelve en la respuesta y aparece en el log de ms-auth.
- El contenedor de ms-auth no publica puertos (`docker port monpoint-auth` vacío).
- `docker compose stop api-gateway`: apagado limpio (exit 143) y baja inmediata en Eureka (`stop_grace_period: 15s`).

# api-gateway

Punto único de entrada de MonPoint (H-002, H-003, H-009; actividades 1.3, 2.3 y 2.4). Es el **único componente
que publica puerto** (8080). El cliente web y Postman le hablan a él, y él reenvía a cada servicio.

Hace tres cosas:

1. **Enruta por prefijo** al servicio registrado en Eureka (`lb://<servicio>`), sin direcciones fijas.
2. **Valida el JWT localmente**, con la misma clave que ms-auth y sin llamarle, y propaga la identidad.
3. **Responde CORS** para el cliente web.

Contrato del token: [`ms-auth/docs/CONTRATO_TOKEN.md`](../ms-auth/docs/CONTRATO_TOKEN.md).

---

## Rutas

| Prefijo | Destino | Estado |
|---|---|---|
| `/auth/**` | `lb://ms-auth` | Operativo |
| `/inventario/**` | `lb://ms-inventario` | 503 hasta que el servicio se registre en Eureka |
| `/ventas/**` | `lb://ms-ventas` | 503 hasta que el servicio se registre en Eureka |
| `/clientes/**` | `lb://ms-clientes` | 503 hasta que el servicio se registre en Eureka |
| `/reportes/**` | `lb://ms-reportes` | 503 hasta que el servicio se registre en Eureka |
| `/notificaciones/**` | `lb://ms-notificaciones` | 503 hasta que el servicio se registre en Eureka |
| Cualquier otro | — | 404 |

- **Sin `StripPrefix`:** el servicio recibe la ruta completa. Cada servicio debe exponer sus endpoints con su prefijo, como `ms-auth` (`/auth/login`, no `/login`).
- **Nombre del servicio:** debe coincidir con su `spring.application.name` (`ms-inventario`, `ms-clientes`, …).
- **Rutas en Gateway 5.0:** van en `spring.cloud.gateway.server.webflux.routes`. El prefijo antiguo `spring.cloud.gateway.routes` se ignora **sin dar error**.

## Qué recibe cada servicio

| Cabecera | Valor |
|---|---|
| `X-User-Id` | Claim `userId` del token (id del usuario en MongoDB) |
| `X-User-Role` | Claim `rol`: `ADMIN` o `VENDEDOR` |
| `X-Request-ID` | El que mandó el cliente si es válido (letras, dígitos, `.`, `_`, `-`, hasta 64), o un UUID nuevo. También vuelve en la respuesta |
| `Authorization` | Se conserva tal cual (ms-auth vuelve a validar el token) |

**Anti-suplantación:** el Gateway **elimina siempre** los `X-User-Id` y `X-User-Role` que mande el cliente, también en
las rutas públicas. Un servicio puede confiar en esas cabeceras porque solo las pone el Gateway.

## Qué exige token

| Petición | Token |
|---|---|
| `POST /auth/login` | No |
| `OPTIONS` (verificación previa de CORS) | No |
| `GET /actuator/health` | No |
| Todo lo demás que coincida con una ruta | **Sí**: `Authorization: Bearer <token>` |

Sin token, o con uno vencido, alterado, sin firma, sin `userId` o con un rol desconocido, la respuesta es la misma que da
ms-auth, y la petición **no llega** al servicio:

```http
HTTP/1.1 401 Unauthorized
WWW-Authenticate: Bearer
Content-Type: application/problem+json

{"detail":"Se requiere un token válido. Inicia sesión para obtener uno.","instance":"/auth/me","status":401,"title":"No autenticado","requestId":"…"}
```

El rol **no** se revisa en el Gateway: cada servicio verifica el suyo con `X-User-Role` (403).

## CORS

- **Orígenes permitidos:** vienen de `CORS_ORIGENES` (en `infra/.env`). Un origen fuera de la lista recibe 403.
- **Métodos:** `GET, POST, PUT, PATCH, DELETE, OPTIONS`.
- **Cabeceras:** `Authorization`, `Content-Type` y `X-Request-ID`. `X-Request-ID` queda expuesta al navegador.
- **El preflight se responde antes de validar el token**, así que nunca da 401.
- **Los 401 también llevan cabeceras CORS**, para que el cliente web pueda leerlos y cerrar la sesión.

## Variables de entorno

| Variable | Por omisión | Para qué |
|---|---|---|
| `SERVER_PORT` | `8080` | Puerto HTTP |
| `EUREKA_URL` | `http://localhost:8761/eureka/` | Servidor Eureka |
| `JWT_SECRET` | Marcador de `infra/.env.example` | Clave HS256 en texto plano UTF-8, al menos 32 caracteres, **idéntica a la de ms-auth**. Con menos, el Gateway no arranca |
| `CORS_ORIGENES` | `http://localhost:5173` | Orígenes permitidos, separados por comas |

## Cómo levantarlo

```bash
cd monpoint-ms/infra && docker compose up -d --build       # el sistema completo
```

Un servicio nuevo tarda unos 20 s en ser enrutable después de registrarse. El Gateway refresca su lista de Eureka
cada 10 s y la guarda en caché otros 10 s; mientras tanto responde 503.

En local, fuera de Docker: `cd monpoint-ms/api-gateway && ./mvnw spring-boot:run`. Necesita Eureka en `localhost:8761`.

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
  - preflight desde `localhost:5173` ➔ 200 con cabeceras CORS; desde un origen ajeno ➔ 403;
  - `X-Request-ID: e2e-gateway-001` vuelve en la respuesta y aparece en el log de ms-auth.
- El contenedor de ms-auth no publica puertos (`docker port monpoint-auth` vacío).
- `docker compose stop api-gateway`: apagado limpio (exit 143) y baja inmediata en Eureka (`stop_grace_period: 15s`).

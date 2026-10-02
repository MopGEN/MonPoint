# Contrato del token JWT — `ms-auth` (H-014)

Referencia para el **API Gateway** y para cualquier servicio que valide los tokens que emite `ms-auth`.
Si algo de este contrato cambia, debe cambiar en ambos lados a la vez.

---

## 1. Cómo se obtiene

`POST /auth/login` (ruta pública):

```http
POST /auth/login
Content-Type: application/json

{"correo": "admin@monpoint.com", "password": "Admin123!"}
```

Respuesta `200 OK`:

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOi...",
  "tipo": "Bearer",
  "correo": "admin@monpoint.com",
  "nombre": "Administrador",
  "rol": "ADMIN",
  "expiraEn": 86400000
}
```

- `expiraEn` es la **vigencia en milisegundos**, no una fecha.
- El correo se normaliza (sin espacios y en minúsculas): `ADMIN@MonPoint.com` y `admin@monpoint.com` son el mismo usuario.
- Correo inexistente, contraseña incorrecta o cuenta inactiva reciben **el mismo** `401` con `"detail": "Credenciales incorrectas"`, y tardan lo mismo: la respuesta no revela qué correos existen.

## 2. Cómo se envía

En cada petición protegida:

```http
Authorization: Bearer <token>
```

## 3. Firma

| Elemento | Valor |
|---|---|
| Algoritmo | `HS256` (HMAC-SHA256), declarado explícitamente al firmar. Header del token: `{"alg":"HS256"}` |
| Clave | Variable `JWT_SECRET`: **texto plano UTF-8** (no Base64), mínimo 32 caracteres (256 bits) |
| Compartida con | API Gateway. Debe ser **idéntica** en ambos lados |
| Origen | `monpoint-ms/infra/.env`. El valor por omisión de `application.properties` es el marcador de `infra/.env.example`: solo sirve para desarrollo |

Con una clave de menos de 32 bytes, `ms-auth` no arranca (`WeakKeyException`).

## 4. Claims

| Claim | Tipo | Valor |
|---|---|---|
| `sub` | string | Correo del usuario, normalizado |
| `userId` | string | Id del usuario en MongoDB (`ObjectId` en hexadecimal) |
| `rol` | string | `ADMIN` o `VENDEDOR`: un único rol por usuario |
| `iat` | número | Fecha de emisión, en segundos Unix |
| `exp` | número | Expiración, en segundos Unix: `iat` + `JWT_EXPIRATION_MS` (24 h por omisión) |

Ejemplo de payload:

```json
{"sub":"admin@monpoint.com","userId":"6abf387781233486cd272c8e","rol":"ADMIN","iat":1790920089,"exp":1791006489}
```

Un token se rechaza con `401` si la firma no coincide, si expiró, si viene sin firma (`"alg":"none"`),
si le falta `sub`, `userId` o `rol`, o si `rol` no es `ADMIN` ni `VENDEDOR`.

## 5. Responsabilidades del API Gateway

1. **Ruteo sin `StripPrefix`:** `/auth/**` se envía a `ms-auth` conservando la ruta completa. `ms-auth` expone `/auth/login`, no `/login`.
2. `POST /auth/login` es pública; el resto de `/auth/**` requiere token.
3. Valida la firma y `exp` localmente, con la misma clave, sin llamar a `ms-auth`.
4. Propaga hacia los servicios internos:

   | Header | Origen |
   |---|---|
   | `X-User-Id` | Claim `userId` |
   | `X-User-Role` | Claim `rol` |
   | `X-Request-ID` | El que mandó el cliente, o uno generado por el Gateway |

5. **Antes de propagar, elimina cualquier `X-User-Id` o `X-User-Role` que haya enviado el cliente.** Si no, un cliente podría hacerse pasar por otro usuario ante los servicios internos.

`ms-auth` no confía en los headers `X-User-*`: valida el JWT por su cuenta en cada petición. Sí reutiliza
`X-Request-ID` si solo contiene letras, dígitos, `.`, `_` o `-` (hasta 64 caracteres); si no, genera uno.
Lo devuelve en la respuesta, lo imprime en cada línea de sus logs y lo incluye como `requestId` en cada error.

## 6. Compromiso *stateless*

Los tokens no se revocan. Si un usuario es desactivado o cambia de rol, los tokens que ya tenía
**siguen siendo válidos hasta su `exp`** (máximo 24 h con la configuración actual).

- Los logins nuevos sí reflejan el cambio de inmediato: una cuenta inactiva recibe `401` y un rol nuevo viaja en el siguiente token.
- `GET /auth/me` consulta la base de datos, así que muestra el estado real (`"activo": false`) aunque el token siga vigente.

Si hiciera falta cortar el acceso antes: reducir `JWT_EXPIRATION_MS` o agregar una lista de revocación (fuera del alcance actual).

## 7. Errores de autenticación y autorización

Formato `application/problem+json` (RFC 9457).

**401**: token ausente o inválido. Incluye el header `WWW-Authenticate: Bearer`.

```json
{"detail":"Se requiere un token válido. Inicia sesión para obtener uno.","instance":"/auth/me","status":401,"title":"No autenticado","requestId":"e2e-30"}
```

**403**: token válido, pero el rol no alcanza para la ruta.

```json
{"detail":"Tu rol no tiene permiso para realizar esta operación.","instance":"/auth/register","status":403,"title":"Acceso denegado","requestId":"e2e-12"}
```

## 8. Rutas de `ms-auth`

| Método y ruta | Acceso |
|---|---|
| `POST /auth/login` | Pública |
| `GET /auth/me` | Cualquier usuario autenticado |
| `POST /auth/register` | `ADMIN` |
| `GET /auth/usuarios` | `ADMIN` |
| `PUT /auth/usuarios/{id}` | Autenticado. Un `VENDEDOR` solo sobre su propio id; nadie cambia su propio rol |
| `PATCH /auth/usuarios/{id}/desactivar` | `ADMIN`, nunca sobre su propia cuenta |

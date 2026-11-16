# Auth — seis endpoints

[Índice](README.md) · [Seguridad y revocación](seguridad.md) · [README del servicio](../../ms-auth/README.md)

Prefijo `/auth`. Los permisos de ruta los aplica Spring Security con la
identidad del JWT; las restricciones sobre el perfil propio las decide
UsuarioServiceImpl. Auth no utiliza X-User-Id/Role como credenciales.

| Método y ruta | Acceso y entrada | Éxito | Errores específicos |
|---|---|---|---|
| `POST /auth/login` | Público; LoginRequest | 200 AuthResponse | 400 cuerpo/campos; 401 credenciales incorrectas o cuenta inactiva |
| `POST /auth/register` | ADMIN; RegistroRequest | 201 UsuarioResponse | 400 campos/política/rol; 403; 409 correo duplicado, incluso de cuenta inactiva |
| `GET /auth/me` | Autenticado; sin cuerpo | 200 UsuarioResponse leído por id | 404 si ya no existe la cuenta |
| `GET /auth/usuarios` | ADMIN; sin parámetros ni cuerpo | 200 array de UsuarioResponse, solo activos | 403 |
| `PUT /auth/usuarios/{id}` | ADMIN sobre cualquier perfil o VENDEDOR sobre su propio id; ActualizarUsuarioRequest | 200 UsuarioResponse | 400 política o cambio de rol propio; 403 perfil ajeno para VENDEDOR; 404; 409 correo duplicado |
| `PATCH /auth/usuarios/{id}/desactivar` | ADMIN, excepto su propia cuenta; sin cuerpo | 204 sin cuerpo | 400 intento sobre sí mismo; 403; 404 |

Salvo login, todas pueden recibir 401 por token ausente/inválido. El listado
no está paginado, no incluye inactivos y no promete orden. No hay endpoint de
reactivación, borrado físico, refresh ni logout en este servicio.

## Cuerpos de entrada

| DTO y campo | Regla implementada |
|---|---|
| LoginRequest.correo | String obligatorio, no blanco, @Email; se normaliza con trim y minúsculas antes de validar |
| LoginRequest.password | String obligatorio, no blanco; se compara sin recortar |
| RegistroRequest.nombre | String obligatorio, no blanco; se guarda con trim |
| RegistroRequest.correo | String obligatorio, @Email, normalizado; índice único en toda la colección |
| RegistroRequest.password | String obligatorio, no blanco; política de PasswordValidator |
| RegistroRequest.rol | Obligatorio: `ADMIN` o `VENDEDOR` |
| ActualizarUsuarioRequest.nombre y correo | Siempre obligatorios, mismas reglas de nombre/correo |
| ActualizarUsuarioRequest.password | Omitido, null o `""` conserva la clave. Otro valor exige la política completa |
| ActualizarUsuarioRequest.rol | Omitido o null conserva el rol. Nadie puede cambiar su propio rol; ADMIN sí el de otra cuenta |

La política exige al menos 8 caracteres Java, como máximo 72 bytes UTF-8,
una mayúscula, una minúscula, un dígito y un carácter de `@$!%*?&`.
Los acentos pueden ocupar más de un byte. Los espacios no se eliminan de una
contraseña. La validación no se sustituye por una expresión regular del cliente.

Correo inexistente, clave incorrecta y cuenta inactiva devuelven el mismo 401
con detail `Credenciales incorrectas`. Para correo inexistente también se
calcula una comparación BCrypt ficticia; esto no es una garantía de tiempos
idénticos para todas las peticiones y condiciones de ejecución.

## Respuestas

AuthResponse, solo en login (ejemplo de forma, token abreviado no utilizable):

```json
{
  "token": "<jwt-emitido>",
  "tipo": "Bearer",
  "correo": "victor.ortiz@autotachi.mx",
  "nombre": "Víctor Ortiz",
  "rol": "ADMIN",
  "expiraEn": 86400000
}
```

expiraEn es duración en milisegundos, no fecha. El ejemplo usa el valor por
omisión de 24 horas; consultar [vigencia y revocación](seguridad.md).

UsuarioResponse, usado en alta, edición, perfil y listado:

```json
{
  "id": "640000000000000000000001",
  "nombre": "Víctor Ortiz",
  "correo": "victor.ortiz@autotachi.mx",
  "rol": "ADMIN",
  "activo": true
}
```

No incluye contraseña ni hash. GET /auth/me refleja el estado actual en la
base; los permisos de la petición siguen procediendo del token. Un rol cambiado
o una baja no invalidan la autoridad de tokens ya emitidos.

## Errores adicionales

Un 400 de Bean Validation añade `campos`, mapa campo → mensaje. Un 400 de la
política de contraseña añade `errores`, lista de todas las reglas incumplidas.
Un 409 por correo no garantiza ese mapa. Los cuerpos, títulos y correlación
comunes están en el [índice](README.md).

## Semilla y continuidad

Las cuatro cuentas iniciales y sus variables están en la
[guía de semilla](../../scripts/SEMILLA_AUTOTACHI.md). El inicializador no
restablece claves, roles ni bajas; tampoco desactiva el genérico histórico.
No se publican contraseñas de demostración. La baja de una cuenta y una posible
rotación de JWT_SECRET son operaciones distintas, descritas en seguridad.

## Fuentes

- [AuthController](../../ms-auth/src/main/java/com/monpoint/auth/controller/AuthController.java) y [UsuarioController](../../ms-auth/src/main/java/com/monpoint/auth/controller/UsuarioController.java).
- [DTO de entrada](../../ms-auth/src/main/java/com/monpoint/auth/dto/request/) y [respuestas](../../ms-auth/src/main/java/com/monpoint/auth/dto/response/).
- [PasswordValidator](../../ms-auth/src/main/java/com/monpoint/auth/validator/PasswordValidator.java), [AuthServiceImpl](../../ms-auth/src/main/java/com/monpoint/auth/service/impl/AuthServiceImpl.java) y [UsuarioServiceImpl](../../ms-auth/src/main/java/com/monpoint/auth/service/impl/UsuarioServiceImpl.java).
- [SecurityConfig](../../ms-auth/src/main/java/com/monpoint/auth/config/SecurityConfig.java) y [errores](../../ms-auth/src/main/java/com/monpoint/auth/controller/GlobalExceptionHandler.java).

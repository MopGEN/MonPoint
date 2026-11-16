# Autenticación, autorización y límites

Estado del código en `c898365`. Los roles admitidos son ADMIN y VENDEDOR,
uno por usuario. La matriz del [índice](README.md) describe los permisos
observados en la implementación; no supone una política central inexistente.

## Dos mecanismos conviven

| Componente | Identidad y decisión |
|---|---|
| API Gateway | Valida el JWT, admite los dos roles conocidos y propaga identidad. No decide si un rol puede ejecutar cada operación. |
| ms-auth | Conserva Authorization, valida otra vez con JwtAuthenticationFilter y crea UserPrincipal en SecurityContext. SecurityConfig aplica permisos por ruta; UsuarioServiceImpl aplica las reglas sobre el perfil propio. |
| inventario, clientes, reportes y notificaciones | Reciben X-User-Role convertido a su enum Rol y X-User-Id como texto. Spring Security permite las rutas; las reglas de rol que existen se aplican en los servicios. Algunas lecturas solo exigen la conversión de las cabeceras. |
| ms-ventas | Recibe X-User-Role como String. VentaService compara literalmente con `"ADMIN"` y `"VENDEDOR"` en registro y consulta, después de comprobar identidad no vacía. No usa enum ni SecurityContext para esa decisión. |

La comparación por cadena de ventas exige esas mayúsculas exactas: otro valor
produce 403 en el servicio, mientras que un valor inválido para un enum produce
400 al enlazar la cabecera en los otros cuatro dominios. A través del Gateway,
un JWT con rol desconocido es rechazado antes con 401; esos 400/403 internos
no describen el resultado público de un JWT con claims inválidos.

## Confianza en las cabeceras internas

El filtro JWT del Gateway elimina **ambas** cabeceras de identidad aportadas por
el cliente, incluso al pasar el login público. Para una petición autenticada,
las repone desde `userId` y `rol`; conserva Authorization para auth. Así se
impide suplantar a otro usuario enviando esas cabeceras **por el Gateway**.

Los cinco servicios distintos de auth no validan la firma ni consultan al emisor.
La ausencia de puertos publicados en Compose evita el acceso directo desde el
host, pero no convierte las cabeceras en credenciales criptográficas: otro
proceso que alcance la red interna puede enviarlas. Compose no implementa mTLS
ni autenticación entre servicios. Su aislamiento es una condición del diseño,
no una propiedad de las cabeceras. No publicar 8082–8086 como sustituto del Gateway.
Auth sí vuelve a validar Bearer aunque reciba cabeceras aparentemente válidas.

## Ticket: sin comprobación de rol ni propiedad en el servicio

`GET /reportes/ventas/{id}/ticket` declara X-User-Id y un X-User-Role de tipo Rol:
la falta de cabeceras produce 401 en el controlador y una cabecera de rol ajeno
al enum produce 400. Por Gateway requiere JWT válido y uno de los dos roles.
Por tanto, no es un endpoint público sin autenticación en el despliegue previsto.

Sin embargo, `DocumentoController.ticket` llama a `DocumentoService.ticket(id)`
**sin pasar UsuarioActual**. Ese método no comprueba rol, estado actual del
usuario ni propiedad de la venta. Cualquier ADMIN o VENDEDOR con un id disponible
puede obtener su comprobante. Los otros tres PDF sí llaman a `exigirAdmin`.
La venta tampoco persiste quién la registró: no existe base para un filtro por
autor, ni para demostrar atribución de ventas con solo sembrar cuentas nominales.
El control visible del cliente no amplía la protección del servidor.

## JWT: vigencia y ausencia de revocación individual

Auth emite HS256 con la clave de texto UTF-8 `JWT_SECRET`, compartida con el
Gateway, de al menos 32 bytes. El token emitido lleva `sub` (correo), `userId`,
`rol`, `iat` y `exp`. Los validadores usan JJWT para comprobar la firma y las
restricciones temporales del token, y comprueban que sub, userId y rol estén
presentes y que el rol sea conocido. No consultan la base ni una lista de
revocación para autorizar cada petición.

`jwt.expiration=${JWT_EXPIRATION_MS:86400000}` fija **86 400 000 ms = 24 horas por
omisión** para los nuevos tokens. La duración es configurable: no es un límite
universal de una hora ni una duración medida del entorno del usuario.
`expiraEn` en la respuesta de login expresa milisegundos; `iat` y `exp` en JWT
usan segundos Unix. Cada token vence en su propio exp.

| Cambio | Efecto en accesos posteriores |
|---|---|
| Desactivar cuenta | Impide nuevos login; no invalida tokens emitidos. |
| Cambiar rol | El siguiente login emite el nuevo rol; los tokens anteriores conservan el anterior. |
| Cambiar contraseña o correo | Afecta al siguiente login; no revoca tokens previos. |
| GET /auth/me | Lee el perfil actual por id y puede mostrar activo=false o un rol distinto del token; esa lectura no es una guarda para las demás operaciones. |
| Salir del cliente | Borra la sesión del navegador; no hay endpoint de logout que revoque el token en el servidor. |
| Reducir JWT_EXPIRATION_MS | Acorta tokens **emitidos después** de aplicar la configuración; no cambia el exp firmado de los anteriores. |
| Rotar JWT_SECRET | Invalida todos los tokens firmados con la clave anterior cuando todos los validadores dejan de aceptarla; obliga a iniciar sesión de nuevo. |

Si se desactiva justo después de emitir un token, la autoridad previa puede
persistir prácticamente 24 horas con la configuración por omisión. La ventana
real es el tiempo restante hasta exp. No existe revocación individual, refresh
token, lista negra ni introspección implementados.

### Rotación coordinada

Este es un procedimiento operativo; la actividad 6.2 **no rota claves ni cierra
sesiones**. Primero verificar el acceso de al menos una cuenta nominal ADMIN.
Desactivar el genérico si sigue activo impide nuevos login, pero no corta sus
tokens. Para invalidar también los emitidos:

1. Generar una nueva clave privada para la instalación y guardarla en su `.env`
   no versionado como JWT_SECRET, sin imprimirla ni compartirla en evidencias.
2. Recrear auth y Gateway con el mismo nuevo valor, desde `monpoint-ms/infra`:
   `docker compose up -d --force-recreate ms-auth api-gateway`.
3. Comprobar que no queden instancias locales o réplicas usando la clave anterior.
   Durante una rotación parcial puede haber rechazo de tokens recién emitidos.
4. Verificar que un token anterior recibe 401 por Gateway, que el login nominal
   devuelve un token aceptado y que la cuenta genérica ya desactivada no inicia sesión.
   No publicar tokens ni cuerpos de login como evidencia.

El cambio es global, no una revocación selectiva. No hay keyring ni periodo de
tolerancia implementados. JWT_EXPIRATION_MS se lee en auth pero **no está
pasada por el Compose actual**: añadirla solo al `.env` no basta para ese
contenedor; requiere una entrada environment/override o definirla al ejecutar
el servicio fuera de Compose. Esta documentación no modifica ese cableado.

## Fuentes de la implementación

- [Filtro de Gateway](../../api-gateway/src/main/java/com/monpoint/gateway/filter/JwtAuthenticationGlobalFilter.java) y [validador](../../api-gateway/src/main/java/com/monpoint/gateway/security/JwtValidador.java).
- [Filtro de auth](../../ms-auth/src/main/java/com/monpoint/auth/security/JwtAuthenticationFilter.java), [SecurityConfig](../../ms-auth/src/main/java/com/monpoint/auth/config/SecurityConfig.java) y [JwtService](../../ms-auth/src/main/java/com/monpoint/auth/security/JwtService.java).
- [UsuarioServiceImpl](../../ms-auth/src/main/java/com/monpoint/auth/service/impl/UsuarioServiceImpl.java) y [AuthServiceImpl](../../ms-auth/src/main/java/com/monpoint/auth/service/impl/AuthServiceImpl.java).
- [VentaService](../../ms-ventas/src/main/java/com/monpoint/ventas/service/VentaService.java): comprobación literal y ausencia de autor persistido.
- [DocumentoController](../../ms-reportes/src/main/java/com/monpoint/reportes/controller/DocumentoController.java) y [DocumentoService](../../ms-reportes/src/main/java/com/monpoint/reportes/service/DocumentoService.java): ticket frente a otros PDF.
- [Compose](../../infra/docker-compose.yml): red, puertos y variables realmente pasadas.
- [Contrato JWT](../../ms-auth/docs/CONTRATO_TOKEN.md): emisión, claims y errores.

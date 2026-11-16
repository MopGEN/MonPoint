# API HTTP de MonPoint — 6.2

Referencia de la implementación en `c898365` (PR #31), revisada el 16 de
noviembre de 2026. Documenta lo que expone el código, incluidas sus limitaciones.
Los ejemplos ilustran formatos; sus ids, fechas, folios e importes no acreditan
una ejecución ni sustituyen los expedientes de aceptación.

Origen habitual: `http://localhost:8080`, el API Gateway. Los 27 endpoints de
negocio se distribuyen entre seis servicios. Los healthchecks de Actuator,
Eureka, el cliente nginx y los intercambios de RabbitMQ no forman parte de ese
recuento. Las rutas conservan el prefijo completo: no se aplica StripPrefix.

| Servicio | Endpoints | Referencia | Puerto interno |
|---|---:|---|---:|
| ms-auth | 6 | [Usuarios y autenticación](auth.md) | 8081 |
| ms-inventario | 5 | [Productos](inventario.md) | 8082 |
| ms-ventas | 2 | [Registro y consulta de venta](ventas.md) | 8083 |
| ms-clientes | 5 | [Clientes](clientes.md) | 8084 |
| ms-reportes | 7 | [Configuración, agregación y PDF](reportes.md) | 8085 |
| ms-notificaciones | 2 | [Alertas de stock](notificaciones.md) | 8086 |

## Acceso y autorización

Solicitar el token con `POST /auth/login` y enviarlo en las siguientes peticiones:

```http
Authorization: Bearer <token>
```

Solo el login es público entre los endpoints de negocio. El Gateway valida el
JWT y sustituye las cabeceras `X-User-Id` y `X-User-Role`; el consumidor externo
no debe enviarlas. Auth vuelve a validar el JWT mediante Spring Security. Los
otros cinco servicios confían en las cabeceras internas y deciden permisos en
su lógica de negocio o aceptan el rol convertido a enum, según la ruta.

La matriz describe acceso **a través del Gateway**. No significa que cada ruta
contenga una comprobación de rol propia. El caso del ticket y las diferencias
de implementación están explicados en [Seguridad y límites](seguridad.md).

| Servicio | Método y ruta | Acceso por Gateway | Éxito |
|---|---|---|---|
| auth | `POST /auth/login` | Público | 200 |
| auth | `POST /auth/register` | ADMIN | 201 |
| auth | `GET /auth/me` | ADMIN, VENDEDOR | 200 |
| auth | `GET /auth/usuarios` | ADMIN | 200 |
| auth | `PUT /auth/usuarios/{id}` | ADMIN; VENDEDOR solo su perfil; nadie cambia su propio rol | 200 |
| auth | `PATCH /auth/usuarios/{id}/desactivar` | ADMIN, nunca su propia cuenta | 204 |
| inventario | `POST /inventario/productos` | ADMIN | 201 |
| inventario | `GET /inventario/productos` | ADMIN, VENDEDOR | 200 |
| inventario | `GET /inventario/productos/{id}` | ADMIN, VENDEDOR | 200 |
| inventario | `PUT /inventario/productos/{id}` | ADMIN | 200 |
| inventario | `DELETE /inventario/productos/{id}` | ADMIN | 204 |
| ventas | `POST /ventas` | ADMIN, VENDEDOR | 202 |
| ventas | `GET /ventas/{id}` | ADMIN, VENDEDOR; cualquier venta | 200 |
| clientes | `POST /clientes` | ADMIN, VENDEDOR | 201 |
| clientes | `GET /clientes` | ADMIN, VENDEDOR | 200 |
| clientes | `GET /clientes/{id}` | ADMIN, VENDEDOR | 200 |
| clientes | `PUT /clientes/{id}` | ADMIN, VENDEDOR | 200 |
| clientes | `DELETE /clientes/{id}` | ADMIN | 204 |
| reportes | `GET /reportes/configuracion` | ADMIN, VENDEDOR | 200 |
| reportes | `PUT /reportes/configuracion` | ADMIN | 200 |
| reportes | `GET /reportes/ventas/agregado` | ADMIN | 200 |
| reportes | `GET /reportes/ventas/{id}/ticket` | ADMIN, VENDEDOR; sin comprobación de rol/propiedad en DocumentoService | 200 PDF |
| reportes | `GET /reportes/ventas/historial` | ADMIN | 200 PDF |
| reportes | `GET /reportes/productos/listado` | ADMIN | 200 PDF |
| reportes | `GET /reportes/clientes/listado` | ADMIN | 200 PDF |
| notificaciones | `GET /notificaciones` | ADMIN | 200 |
| notificaciones | `PUT /notificaciones/{id}/leida` | ADMIN | 204 |

Los filtros son parámetros de esas mismas rutas, no endpoints adicionales:
`GET /clientes?nombre=...` y `GET /notificaciones?leida=false`, por ejemplo.
No existe `GET /ventas` ni un listado JSON paginado de ventas. El historial PDF
de reportes no es ese endpoint ni una consulta de ventas PENDIENTE.

## Cuerpos y respuestas

Usar `Content-Type: application/json` para cuerpos JSON. Las respuestas 200/201/202
son JSON salvo las cuatro descargas de reportes; 204 no tiene cuerpo.
Las fichas de servicio detallan campos obligatorios, valores opcionales y errores.
Los importes son decimales de negocio: el servidor calcula la venta con
BigDecimal y los almacena como Decimal128. No se deben recalcular los importes
del detalle a partir de aritmética binaria del navegador.

Las fechas Instant se serializan en ISO-8601 UTC. Los parámetros de fecha de
reportes son días `AAAA-MM-DD` en la zona horaria del negocio, no instantes UTC.
No se promete orden ni paginación para listados que no los declaran.

## Errores y correlación

Los handlers de los servicios y el 401 del filtro JWT del Gateway usan
ProblemDetail, `application/problem+json` (RFC 9457). Ejemplo ilustrativo:

```json
{
  "type": "about:blank",
  "title": "Datos inválidos",
  "status": 400,
  "detail": "Uno o más campos no cumplen las validaciones",
  "instance": "/inventario/productos",
  "requestId": "revision-6.2",
  "campos": {"nombre": "El nombre es obligatorio"}
}
```

| Código | Significado y matices |
|---|---|
| 400 | JSON, campos, parámetros o regla de negocio inválidos; `campos` aparece en validación de DTO, no en todo 400 |
| 401 | JWT ausente/inválido en Gateway; credenciales incorrectas en login; también falta de cabeceras en servicios internos |
| 403 | Rol insuficiente en el servicio o Spring Security; CORS también puede devolver 403 antes de autenticar |
| 404 | Recurso inexistente o no disponible en una proyección; el ticket añade su código específico |
| 409 | Correo de usuario duplicado, ticket de venta cancelada u otro conflicto de índice; no implica unicidad de nombres |
| 500 | Error inesperado; los handlers de dominio no exponen la excepción interna |
| 503 | Entre otros casos, Gateway sin instancia disponible en Eureka; no prueba un fallo de validación del negocio |

Auth devuelve `errores` (lista de reglas) para la política de contraseña. El
404 de ticket añade `codigo: "VENTA_NO_DISPONIBLE"`. Los demás campos extra
no están garantizados en todos los errores.

No todos los errores del Gateway tienen ese formato: sus 404/503 de infraestructura
pueden usar el cuerpo de WebFlux. Para correlación se debe usar la cabecera
`X-Request-ID`, también cuando el cuerpo no contiene un `requestId` equivalente.
Se conserva el id del cliente si cumple `[A-Za-z0-9._-]{1,64}`; en otro caso se
genera uno. El 401 por token inválido incluye `WWW-Authenticate: Bearer`.
No se promete esa cabecera en todas las respuestas 401 originadas en el login
u otros handlers.

## Navegador y PDF

El Gateway permite los orígenes de `CORS_ORIGENES`; el preflight OPTIONS se
atiende antes de pedir token. Se autorizan Authorization, Content-Type y
X-Request-ID. Solo X-Request-ID está expuesta explícitamente a JavaScript:
`Content-Disposition` de los PDF no es legible entre orígenes con la configuración
actual. El cliente descarga con fetch y Bearer, no con un enlace sin cabecera.
Un 200 del login o de un GET no acredita el contenido de otra respuesta ni la
recepción de eventos por un consumidor.

## Arranque, pruebas y fuentes

- [Operación local y pruebas](operacion.md): Compose, entorno, puertos y límites de las verificaciones.
- [Seguridad](seguridad.md): mecanismos, confianza interna, vigencia y rotación.
- [Semilla nominal](../../scripts/SEMILLA_AUTOTACHI.md): identidades y catálogo del prototipo.
- [Eventos](../eventos/producto.md), [ventas](../eventos/venta-cierre.md) y [clientes](../eventos/cliente.md): integración asíncrona.
- [Configuración del Gateway](../../api-gateway/src/main/resources/application.properties).

Cada ficha enlaza los controladores, DTO y servicios usados para redactarla.
Esta documentación no añade endpoints ni corrige sus controles de acceso;
los hallazgos deben conservarse como límites al redactar 6.4.

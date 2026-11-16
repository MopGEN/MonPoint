# Clientes — cinco endpoints

[Índice](README.md) · [Seguridad](seguridad.md) · [README del servicio](../../ms-clientes/README.md)

Prefijo `/clientes`. JWT requerido por Gateway; el controlador enlaza las dos
cabeceras de identidad y el rol a enum. Ambos roles pueden crear y modificar
**cualquier cliente**; no se filtra por propietario. ClienteServiceImpl exige
ADMIN solo para la eliminación.

| Método y ruta | Entrada y acceso | Éxito | Errores específicos |
|---|---|---|---|
| `POST /clientes` | ADMIN, VENDEDOR; ClienteRequest | 201 ClienteResponse | 400 campos |
| `GET /clientes` | Ambos; nombre opcional en query | 200 array de ClienteResponse | Sin paginación ni orden garantizado |
| `GET /clientes/{id}` | Ambos | 200 ClienteResponse | 404 |
| `PUT /clientes/{id}` | Ambos; ClienteRequest | 200 ClienteResponse | 400; 404 |
| `DELETE /clientes/{id}` | ADMIN; sin cuerpo | 204 sin cuerpo | 403; 404 |

Todas requieren autenticación por Gateway; ver [errores comunes](README.md).

## Alta y modificación

```json
{
  "nombre": "Taller de ejemplo",
  "correo": "contacto@example.test",
  "telefono": "3312345678",
  "direccion": "Calle de ejemplo 10"
}
```

| Campo | Regla |
|---|---|
| nombre | String obligatorio, no blanco; se guarda con trim |
| correo | String opcional; @Email cuando tiene valor; no es único |
| telefono | String opcional; no hay patrón de teléfono en ClienteRequest |
| direccion | String opcional |

Los opcionales se guardan con trim; una cadena vacía se convierte a null.
PUT requiere de nuevo nombre. Los campos opcionales omitidos/null se borran
(se guardan null), no conservan el valor anterior. La validación de correo es
la del servidor; una expresión simplificada en el cliente puede rechazar
formas que @Email admite, como una parte local entrecomillada.

## Búsqueda

`GET /clientes?nombre=taller` hace coincidencia parcial sin distinguir mayúsculas.
El filtro se recorta; ausente o blanco devuelve todos. No hay filtro de correo
o teléfono en el endpoint: que una pantalla filtre también esos campos sobre
el listado descargado es una elección del cliente, no otra capacidad de la API.

## ClienteResponse

```json
{
  "id": "640000000000000000000020",
  "nombre": "Taller de ejemplo",
  "correo": "contacto@example.test",
  "telefono": "3312345678",
  "direccion": "Calle de ejemplo 10",
  "fechaRegistro": "2026-11-16T18:00:00Z",
  "fechaActualizacion": "2026-11-16T18:00:00Z"
}
```

No tiene activo. DELETE es **físico**; repetir sobre el id eliminado devuelve
404. No se publica un evento de baja: las réplicas de ventas y las proyecciones
de reportes pueden conservar al cliente eliminado. La API no comprueba vínculos
con ventas antes de borrarlo. Las ventas existentes conservan su snapshot de nombre.
Alta y modificación sí publican eventos de catálogo.

## Fuentes

- [ClienteController](../../ms-clientes/src/main/java/com/monpoint/clientes/controller/ClienteController.java).
- [ClienteRequest](../../ms-clientes/src/main/java/com/monpoint/clientes/dto/request/ClienteRequest.java) y [ClienteResponse](../../ms-clientes/src/main/java/com/monpoint/clientes/dto/response/ClienteResponse.java).
- [ClienteServiceImpl](../../ms-clientes/src/main/java/com/monpoint/clientes/service/impl/ClienteServiceImpl.java) y [Cliente](../../ms-clientes/src/main/java/com/monpoint/clientes/model/Cliente.java).
- [Errores](../../ms-clientes/src/main/java/com/monpoint/clientes/controller/GlobalExceptionHandler.java) y [eventos](../eventos/cliente.md).

# Notificaciones — dos endpoints

[Índice](README.md) · [Seguridad](seguridad.md) · [README del servicio](../../ms-notificaciones/README.md)

Prefijo `/notificaciones`. Solo ADMIN en ambas operaciones. El Gateway valida
JWT y sustituye las cabeceras; el controlador enlaza Rol y el servicio exige
id no vacío y rol ADMIN. No hay alerta por usuario: la lectura es global.

| Método y ruta | Entrada | Éxito | Errores específicos |
|---|---|---|---|
| `GET /notificaciones` | leida opcional en query | 200 array de Notificacion | 400 filtro ilegible; 403 VENDEDOR |
| `PUT /notificaciones/{id}/leida` | Id; sin cuerpo | 204 sin cuerpo | 403 VENDEDOR; 404 id inválido o inexistente |

Por Gateway, sin token/incorrecto produce 401. No se promete orden del array
ni paginación. `leida=false` filtra no leídos; `leida=true`, leídos; sin filtro
se devuelven todos, incluidos los históricos inactivos. El filtro es por leida,
no por stock actual ni por activa.

## Forma de un aviso

```json
{
  "id": "640000000000000000000040",
  "tipo": "STOCK_BAJO",
  "productoId": "640000000000000000000010",
  "productoNombre": "Producto de ejemplo",
  "existenciaResultante": 5,
  "mensaje": "Existencia baja de Producto de ejemplo: 5",
  "activa": true,
  "leida": false,
  "fechaCreacion": "2026-11-16T18:00:01Z",
  "fechaLectura": null
}
```

El PUT establece atómicamente leida=true, activa=false y fechaLectura. Repetir
el PUT devuelve 204 y conserva la fecha de primera lectura, aunque ya exista
otro aviso activo para el producto. Un id con formato no ObjectId devuelve
404 por decisión explícita de NotificacionService.

Leer **no repone stock**. Si llega después otro StockBajo distinto, puede abrir
un aviso nuevo. Una reentrega con el mismo eventoId se descarta sin reabrirlo.
Mientras un aviso esté activo, otros eventos distintos del producto actualizan
su mensaje y existencia, conservando id/fechaCreacion. La existencia refleja
el evento procesado, no un GET del inventario actual. No hay orden causal por
fecha del sobre; una entrega tardía puede mostrar un valor anterior.

No existe POST para crear alertas ni un endpoint para reabrirlas manualmente.
Se originan en StockBajo de inventario. Un listado vacío no demuestra que todos
los productos estén por encima del umbral: puede haber avisos leídos, eventos
perdidos o consumidores todavía sin recibirlos.

## Fuentes

- [NotificacionController](../../ms-notificaciones/src/main/java/com/monpoint/notificaciones/controller/NotificacionController.java) y [NotificacionService](../../ms-notificaciones/src/main/java/com/monpoint/notificaciones/service/NotificacionService.java).
- [Modelo de respuesta](../../ms-notificaciones/src/main/java/com/monpoint/notificaciones/model/Notificacion.java) y [errores](../../ms-notificaciones/src/main/java/com/monpoint/notificaciones/controller/GlobalExceptionHandler.java).
- [Contrato StockBajo](../eventos/stock-bajo.md).

# Inventario — cinco endpoints

[Índice](README.md) · [Seguridad](seguridad.md) · [README del servicio](../../ms-inventario/README.md)

Prefijo `/inventario/productos`. El Gateway exige JWT. El controlador recibe
X-User-Id y X-User-Role como enum Rol; ProductoServiceImpl exige ADMIN para
las tres escrituras. Las consultas no incluyen una comprobación adicional de rol.

| Método y ruta | Entrada y acceso | Éxito | Errores específicos |
|---|---|---|---|
| `POST /inventario/productos` | ADMIN; ProductoRequest | 201 ProductoResponse activo | 400 campos; 403 |
| `GET /inventario/productos` | Ambos roles; sin filtros | 200 array de activos | Sin paginación ni orden garantizado |
| `GET /inventario/productos/{id}` | Ambos roles | 200 ProductoResponse, incluso inactivo | 404 |
| `PUT /inventario/productos/{id}` | ADMIN; ProductoRequest completo | 200 ProductoResponse | 400; 403; 404 |
| `DELETE /inventario/productos/{id}` | ADMIN; sin cuerpo | 204, baja lógica | 403; 404 |

Todas requieren autenticación por Gateway; ver los [errores comunes](README.md).
No existe endpoint para reactivar. Repetir DELETE sobre un producto ya inactivo
devuelve 204 sin otra baja publicada. PUT sobre un inactivo está permitido,
pero no lo reactiva.

## ProductoRequest

```json
{
  "nombre": "Balatas delanteras cerámicas Tsuru",
  "descripcion": null,
  "precio": 489.00,
  "existencia": 14,
  "umbralStock": 6
}
```

| Campo | Regla |
|---|---|
| nombre | String obligatorio, no blanco; trim al guardar |
| descripcion | String opcional; null/blanco se guarda como null; otro valor con trim |
| precio | BigDecimal obligatorio, no negativo; el DTO no impone dos decimales ni unicidad por nombre |
| existencia | Integer obligatorio, no negativo |
| umbralStock | Integer opcional, no negativo. En alta, omitido/null toma **5**; en edición, omitido/null **conserva el actual** |

PUT no es una modificación parcial: siempre requiere nombre, precio y existencia.
Su existencia enviada reemplaza el stock actual; una pantalla o guion que edite
precios debe considerar ese efecto. No hay campo codigo/SKU en request o response.
No se debe convertir una referencia del prototipo en una promesa de contrato.

## ProductoResponse

```json
{
  "id": "640000000000000000000010",
  "nombre": "Balatas delanteras cerámicas Tsuru",
  "descripcion": null,
  "precio": 489.00,
  "existencia": 14,
  "umbralStock": 6,
  "activo": true,
  "fechaRegistro": "2026-11-16T18:00:00Z",
  "fechaActualizacion": "2026-11-16T18:00:00Z"
}
```

El listado excluye inactivos; obtener por id no los excluye. No hay índice único
por nombre: dos productos con el mismo nombre son posibles. Un 409 genérico de
índice no debe presentarse como una regla de unicidad inexistente.

La existencia es una observación de inventario en esa respuesta, no una reserva
del carrito. Al registrar una venta, la reserva se hace de forma asíncrona y
atómica para todas sus líneas. La condición de stock bajo es existencia
resultante menor o igual al umbral. Crear o editar por HTTP no equivale a emitir
StockBajo; el evento se emite en la reserva. No hay API HTTP de reserva.

## Eventos y límites

Alta, modificación y baja publican ProductoCreado, ProductoActualizado y
ProductoDadoDeBaja. El consumidor de VentaCreada reserva existencias y publica
StockReservado/StockRechazado y, si corresponde, StockBajo. Sin outbox, una
publicación perdida no se repara con solo consultar o reutilizar el producto.
Contratos: [producto](../eventos/producto.md), [reserva](../eventos/stock-reservado.md),
[rechazo](../eventos/stock-rechazado.md) y [alerta](../eventos/stock-bajo.md).

## Fuentes

- [ProductoController](../../ms-inventario/src/main/java/com/monpoint/inventario/controller/ProductoController.java).
- [ProductoRequest](../../ms-inventario/src/main/java/com/monpoint/inventario/dto/request/ProductoRequest.java) y [ProductoResponse](../../ms-inventario/src/main/java/com/monpoint/inventario/dto/response/ProductoResponse.java).
- [ProductoServiceImpl](../../ms-inventario/src/main/java/com/monpoint/inventario/service/impl/ProductoServiceImpl.java) y [Producto](../../ms-inventario/src/main/java/com/monpoint/inventario/model/Producto.java).
- [Errores](../../ms-inventario/src/main/java/com/monpoint/inventario/controller/GlobalExceptionHandler.java).

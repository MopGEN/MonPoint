# VentaConfirmada y VentaCancelada — versión 1

Publicador: ms-ventas. Exchange topic durable: `ventas.eventos`.
Routing keys: `venta.confirmada` y `venta.cancelada`.
Consumidores previstos: ms-reportes y ms-notificaciones.

Sobre: `eventoId` (UUID), `tipo`, `version: "1"`, `fecha` (UTC ISO-8601),
`origen: "ms-ventas"`, `datos`.

## Datos completos de la venta

| Campo | Tipo JSON | Origen |
| --- | --- | --- |
| ventaId | string | Identificador de venta |
| folio | integer (Int64) | Folio asignado al registrar |
| clienteId | string o null | Identificador congelado |
| clienteNombre | string o null | Nombre congelado |
| total | number decimal | Total calculado en el servidor |
| fechaCreacion | string UTC ISO-8601 | Fecha original de la venta |
| fechaResolucion | string UTC ISO-8601 | Momento de resolución en ms-ventas |
| lineas | array | Líneas congeladas, en el orden original |
| motivo | string, solo en VentaCancelada | Código de rechazo de inventario |

Cada línea incluye `productoId`, `nombreProducto`, `cantidad` (Int32 positivo)
y `precioUnitario` (decimal). No incluye `subtotal` ni existencias. El tipo de
evento determina el estado; no se repite un campo `estado`.

`motivo` **no existe** en VentaConfirmada, ni siquiera como null. En VentaCancelada
es uno de `EXISTENCIA_INSUFICIENTE`, `PRODUCTO_INACTIVO`, `PRODUCTO_NO_ENCONTRADO`.
La frase para el cajero se guarda en `ventas.motivoCancelacion`; no reemplaza el
código del evento. Los nombres y cantidades proceden de la venta congelada.

## Consistencia y reentregas

El envío se difiere hasta después del commit que resuelve la venta y registra
el evento de stock. Una reversión no anuncia ningún cierre. Una respuesta sobre
una venta ya resuelta se registra sin cambiarla ni volver a publicar.

Sin outbox no hay entrega garantizada entre base y broker: una caída después
del commit puede dejar la proyección desactualizada. Reentregar stock no repara
esa ventana, porque su id ya está registrado. Se requiere reconciliación manual;
los consumidores de cierre también deben aplicar idempotencia por `eventoId`.

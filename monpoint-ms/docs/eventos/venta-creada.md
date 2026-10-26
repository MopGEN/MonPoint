# VentaCreada — contrato de salida de ms-ventas

Actividad 4.1. Versión del sobre: `"1"`. Exchange `ventas.eventos` (topic),
routing key `venta.creada`, cola consumidora de inventario `inventario.venta-creada`.

```json
{
  "eventoId": "c20e9e68-3908-47e3-bb4b-e009988ca68b",
  "tipo": "VentaCreada",
  "version": "1",
  "fecha": "2026-10-25T21:00:00Z",
  "origen": "ms-ventas",
  "datos": {
    "ventaId": "68f1a2b3c4d5e6f701234570",
    "folio": 1042,
    "clienteId": "68f1a2b3c4d5e6f701234568",
    "clienteNombre": "Ana Ruiz",
    "total": 123.45,
    "fecha": "2026-10-25T21:00:00Z",
    "lineas": [
      {
        "productoId": "68f1a2b3c4d5e6f701234567",
        "nombreProducto": "Tinta para sello",
        "cantidad": 3,
        "precioUnitario": 41.15
      }
    ]
  }
}
```

- `eventoId` es un UUID nuevo; también se coloca en `messageId` de AMQP.
- `ventaId` y los identificadores de producto/cliente son cadenas ObjectId.
- `folio` es entero de 64 bits; `cantidad`, entero positivo de 32 bits.
- `clienteId` y `clienteNombre` son ambos nulos para una venta sin cliente.
- Los importes viajan como números decimales, nunca texto ni valores de punto
  flotante calculados por el cliente. El total lo calcula ms-ventas.
- Los nombres y precios son snapshots de la venta. No se incluye `subtotal`:
  cada consumidor lo obtiene mediante cantidad × precioUnitario.
- `datos.fecha` corresponde a `ventas.fechaCreacion`; la fecha del sobre
  corresponde a la construcción del evento. Ambas son instantes ISO-8601.
- Inventario lee únicamente `ventaId` y `lineas[productoId, cantidad]`; ignora
  los campos adicionales. Los nombres enriquecidos quedan disponibles para
  consumidores posteriores sin renombrar `clienteNombre`.

## Publicación y fallos

La venta y el incremento del contador se confirman en una misma transacción.
El servicio invoca el publicador después de insertar la venta; el adaptador
registra `afterCommit` y envía únicamente cuando la transacción confirma.
Una reversión no emite el evento.

El envío fallido se registra con eventoId, ventaId, folio y causa; se mantiene
la respuesta 202 porque la venta ya existe. Los rechazos/devoluciones del broker
se registran mediante los callbacks de RabbitConfig. Un envío no equivale a una
confirmación de stock.

No hay outbox: una caída entre commit y envío puede dejar una venta PENDIENTE
sin evento. La detección/operación de pendientes y la resolución de la saga
corresponden a las actividades siguientes; no hay reenvío automático en 4.1.

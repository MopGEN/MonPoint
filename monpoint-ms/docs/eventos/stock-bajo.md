# StockBajo — contrato de salida de ms-inventario

Actividad 2.5. Contrato implementado, pendiente de revisión. Versión del sobre: `"1"`.

Publica **ms-inventario**. Consume **ms-notificaciones**, actividad 5.4.
Exchange `inventario.eventos`: topic, durable, sin borrado automático.
Routing key `stock.bajo`. ms-ventas no tiene binding para esta clave.

```json
{
  "eventoId": "6ab530ed-0fb9-4046-8e38-6c380153cbee",
  "tipo": "StockBajo",
  "version": "1",
  "fecha": "2026-11-04T19:00:00Z",
  "origen": "ms-inventario",
  "datos": {
    "productoId": "68f1a2b3c4d5e6f701234567",
    "nombre": "Tinta para sello",
    "existenciaResultante": 5
  }
}
```

## Campos y disparador

- `productoId`: cadena ObjectId del producto reservado.
- `nombre`: cadena con el nombre del producto. No se llama nombreProducto ni productoNombre.
- `existenciaResultante`: entero no negativo de 32 bits tras la reserva.
- No incluye ventaId, cantidad reservada, umbralStock, precio ni identificador de notificación.

ReservaStockService (H-024), después de una reserva confirmada y de invocar la
publicación de StockReservado, recorre cada producto reservado y emite StockBajo
si **existenciaResultante <= umbralStock**. No se limita al cruce del umbral:
una reserva posterior que siga bajo el umbral puede producir otro evento nuevo.
La reentrega normal de una misma VentaCreada ya registrada se descarta sin
repetir reserva ni avisos; eso no agrupa eventos distintos por producto.

No se emite al crear un producto ni al editar existencias manualmente. Tampoco
se emite por una reserva rechazada. El umbral por defecto del modelo es 5. Con un umbral configurado explícitamente
en 0, el aviso se produce al quedar sin existencia. Para demostrar el aviso con stock todavía disponible, la futura semilla por API
puede crear existencia 10 y umbral 5; reservar 5 deja 5 y dispara el evento.

## Consumidor de 5.4

La cola es `notificaciones.stock-bajo`, durable, con binding exacto
`stock.bajo` y su `notificaciones.stock-bajo.fallidos`. ms-notificaciones declara
su propia topología y configura tres intentos totales (`max-retries=2` en Boot 4.1.1), sin
reutilizar ventas.stock. Un exchange durable no conserva mensajes por sí solo:
sin una cola enlazada no hay historial que se pueda recuperar retroactivamente.

El consumidor mapea explícitamente **datos.nombre → productoNombre**.
No cambia el campo del evento.
La 5.4 requiere dos mecanismos complementarios: **idempotencia por eventoId**
en `eventos_procesados`, para que la reentrega del mismo evento no produzca un
efecto adicional, y **deduplicación por productoId con activa = true**, para
que eventos distintos del mismo producto actualicen `existenciaResultante` de
la notificación activa existente en lugar de crear otra. Si no existe una
notificación activa para ese producto, se crea una nueva. Dos ventas pueden
producir dos eventos válidos sin acumular dos notificaciones activas.
La implementación usa una transacción para marcador y aviso, actualización
atómica e índice único parcial por producto activo. Leer pone `leida=true` y
`activa=false`; un evento distinto posterior puede abrir otro aviso. La fecha
del sobre no se usa como versión: ante eventos fuera de orden, queda la
existencia del último procesado, que puede ser antigua. Véanse las
[decisiones, índices y API de 5.4](../../ms-notificaciones/README.md).
El evento no tiene ventaId, por lo que ese vínculo no se puede reconstruir solo
con esta carga.

## Sobre y transporte

`eventoId` es un UUID nuevo por evento; el mismo valor viaja como `messageId`
de AMQP. Los consumidores actuales de ventas deduplican usando **eventoId del
JSON**, registrado en `eventos_procesados`; RabbitMQ no deduplica automáticamente
por messageId. Una reentrega del mismo mensaje debe conservar ambos valores.
`tipo`, `version`, `fecha` y `origen` son cadenas; `version` es `"1"`, no el número
1. `fecha` es el instante ISO-8601 UTC de construcción del sobre, no una garantía
de orden de entrega. Content-Type AMQP: `application/json`.

Los ejemplos son ilustrativos, no capturas de mensajes de una ejecución real.

## Publicación y fallos

El adaptador registra `afterCommit` cuando hay sincronización transaccional;
si no la hay, envía inmediatamente. Un error de serialización o envío capturado
por el adaptador se registra en logs. No hay outbox ni reenvío automático desde
este publicador: un commit seguido de una caída antes del envío puede perder
el evento. La llamada a `send` por sí sola no acredita que un consumidor lo haya
recibido. Inventario y clientes no configuran aquí los callbacks de confirmación
y devolución que sí tiene ms-ventas; no se les atribuye esa garantía.

## Fuentes contrastadas

Publicador contrastado en `674ee3599d519980c6595284df03a278018211e1`.
La sección del consumidor se actualiza con la implementación de 5.4; no altera
la carga emitida por ese publicador.

- [Carga exacta](https://github.com/MopGEN/MonPoint/blob/674ee3599d519980c6595284df03a278018211e1/monpoint-ms/ms-inventario/src/main/java/com/monpoint/inventario/event/impl/RabbitInventarioEventPublisher.java)
- [Condición y momento de emisión](https://github.com/MopGEN/MonPoint/blob/674ee3599d519980c6595284df03a278018211e1/monpoint-ms/ms-inventario/src/main/java/com/monpoint/inventario/service/ReservaStockService.java)
- [CRUD sin StockBajo](https://github.com/MopGEN/MonPoint/blob/674ee3599d519980c6595284df03a278018211e1/monpoint-ms/ms-inventario/src/main/java/com/monpoint/inventario/service/impl/ProductoServiceImpl.java)
- [Modelo y umbral](https://github.com/MopGEN/MonPoint/blob/674ee3599d519980c6595284df03a278018211e1/monpoint-ms/ms-inventario/src/main/java/com/monpoint/inventario/model/Producto.java)
- [Bindings actuales de ventas](https://github.com/MopGEN/MonPoint/blob/674ee3599d519980c6595284df03a278018211e1/monpoint-ms/ms-ventas/src/main/java/com/monpoint/ventas/config/RabbitConfig.java)

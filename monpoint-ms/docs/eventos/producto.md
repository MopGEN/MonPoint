# ProductoCreado, ProductoActualizado y ProductoDadoDeBaja — contrato de ms-inventario

Actividad 2.5. Contrato implementado, pendiente de revisión. Versión del sobre: `"1"`.

Publica **ms-inventario**. Consume actualmente **ms-ventas**, mediante
`ProductoEventoListener`, para mantener `productos_replica`.
Exchange `inventario.eventos`: topic, durable, sin borrado automático.
Cola durable `ventas.productos`, binding `producto.*`; fallidos:
`ventas.productos.fallidos`. El guion de `producto.dado-de-baja` no introduce
segmentos adicionales: los tres routing keys coinciden con ese patrón.

| Tipo | Routing key | Cuándo se publica |
| --- | --- | --- |
| ProductoCreado | producto.creado | Tras guardar el alta por ProductoServiceImpl.crear |
| ProductoActualizado | producto.actualizado | Tras guardar una modificación |
| ProductoDadoDeBaja | producto.dado-de-baja | Tras la baja lógica; activo=false |

Los tres comparten exactamente la carga del ejemplo; cambia el tipo, el estado
y los valores del snapshot. Cada evento recibe su propio eventoId.

```json
{
  "eventoId": "4c0c1979-2d46-4a3a-8078-da8f09ebf1e6",
  "tipo": "ProductoCreado",
  "version": "1",
  "fecha": "2026-11-04T19:00:00Z",
  "origen": "ms-inventario",
  "datos": {
    "productoId": "68f1a2b3c4d5e6f701234567",
    "nombre": "Tinta para sello",
    "precio": 41.15,
    "activo": true,
    "fechaActualizacion": "2026-11-04T18:59:59Z"
  }
}
```

## Campos y consumo

- `productoId`: cadena ObjectId del documento de inventario; conserva ese id en la réplica.
- `nombre`: cadena; `precio`: número decimal (BigDecimal), no texto; `activo`: booleano.
- `fechaActualizacion`: cadena ISO-8601 del producto, o null si el objeto no tiene fecha.
- No publica `existencia`, `umbralStock` ni `descripcion`. Ventas no replica stock.
- La baja conserva el producto con activo=false; repetir la baja de un producto
  ya inactivo no publica otro evento. Modificar un producto inactivo está permitido
  por el servicio actual y no implica reactivación.
- La reserva cambia existencia y fecha del producto, pero no publica ProductoActualizado.
- Editar existencia manualmente puede publicar ProductoActualizado, pero su carga
  sigue sin existencia y no dispara StockBajo. Escribir directo en MongoDB omite
  esta publicación: la semilla 3.1 debe entrar por la API a través del Gateway.

El DTO consumidor tolera propiedades desconocidas. El listener exige eventoId,
productoId, nombre, precio y activo no nulos y hace upsert de la réplica; analiza
la fecha si no es nula.

Un evento con fecha estrictamente anterior a la réplica se descarta como
antiguo y se registra como procesado. Fechas iguales se aplican; una fecha nula
no prueba antigüedad y también puede aplicarse. No se garantiza orden total.
Estos listeners no validan `version` ni discriminan `tipo`; la selección actual
parte del binding y la forma del DTO. No se documenta una validación inexistente.

En ms-ventas la aplicación del cambio y el registro de evento procesado
comparten transacción. En Spring Boot 4.1.1, `max-retries=2` configura **3 intentos
totales** (entrega inicial más dos reintentos), intervalo inicial de 1 segundo
y `default-requeue-rejected=false`. `max-attempts` se retiró en Boot 4 y no
configura ese límite. La [prueba del interceptor real](../../ms-ventas/src/test/java/com/monpoint/ventas/config/ReintentosRabbitTest.java)
cuenta las tres entregas y verifica el rechazo sin reencolado. Tras agotar
los intentos, el rechazo se enruta por el exchange por omisión `""` a la cola
`.fallidos` indicada arriba. No hay garantía de entrega exactamente una vez ni
recuperación automática de mensajes publicados que no alcanzaron una cola.

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

Revisión `674ee3599d519980c6595284df03a278018211e1`. Los archivos examinados coinciden con esta revisión de main.

- [Publicador y carga](https://github.com/MopGEN/MonPoint/blob/674ee3599d519980c6595284df03a278018211e1/monpoint-ms/ms-inventario/src/main/java/com/monpoint/inventario/event/impl/RabbitInventarioEventPublisher.java)
- [Disparadores CRUD](https://github.com/MopGEN/MonPoint/blob/674ee3599d519980c6595284df03a278018211e1/monpoint-ms/ms-inventario/src/main/java/com/monpoint/inventario/service/impl/ProductoServiceImpl.java)
- [Consumidor](https://github.com/MopGEN/MonPoint/blob/674ee3599d519980c6595284df03a278018211e1/monpoint-ms/ms-ventas/src/main/java/com/monpoint/ventas/listener/ProductoEventoListener.java)
- [Topología](https://github.com/MopGEN/MonPoint/blob/674ee3599d519980c6595284df03a278018211e1/monpoint-ms/ms-ventas/src/main/java/com/monpoint/ventas/config/RabbitConfig.java)
- [Reintentos](https://github.com/MopGEN/MonPoint/blob/674ee3599d519980c6595284df03a278018211e1/monpoint-ms/ms-ventas/src/main/resources/application.properties)

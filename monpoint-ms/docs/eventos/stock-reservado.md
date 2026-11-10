# StockReservado — contrato de salida de ms-inventario

Actividad 2.5. Contrato implementado, pendiente de revisión. Versión del sobre: `"1"`.

Publica **ms-inventario**. Consume actualmente **ms-ventas**, mediante
`StockEventoListener`, para resolver la venta pendiente.
Exchange `inventario.eventos`: topic, durable, sin borrado automático.
Routing key `stock.reservado`. Cola durable `ventas.stock`, con binding explícito
para esta clave; fallidos: `ventas.stock.fallidos`. Comparte cola con
StockRechazado; no se usa `stock.*`, que también capturaría StockBajo.

```json
{
  "eventoId": "74007b36-ace7-45de-949d-c524327bade5",
  "tipo": "StockReservado",
  "version": "1",
  "fecha": "2026-11-04T19:00:00Z",
  "origen": "ms-inventario",
  "datos": {
    "ventaId": "68f1a2b3c4d5e6f701234570",
    "lineas": [
      {
        "productoId": "68f1a2b3c4d5e6f701234567",
        "cantidad": 3,
        "existenciaResultante": 7
      }
    ]
  }
}
```

## Campos y significado

- `ventaId`: cadena ObjectId que correlaciona con VentaCreada y con la venta existente.
- `lineas`: arreglo; cada elemento contiene productoId (cadena ObjectId), cantidad
  (entero positivo de 32 bits) y existenciaResultante (entero no negativo de 32 bits).
- Cantidades de un producto repetido en VentaCreada se agrupan antes de reservar:
  se publica una línea por producto con la suma y la existencia resultante.
- No se publica nombre, precio ni umbral en estas líneas, aunque LineaReservada
  conserva esos datos internamente para decidir si también emite StockBajo.
- La existencia es un snapshot tras la reserva, no una promesa de stock actual.

## Publicación y resolución

ReservaStockService descuenta todas las líneas dentro de una transacción MongoDB,
con condición activo=true y existencia suficiente. Si cualquiera falla, revierte
el conjunto y no publica StockReservado. El evento se publica después del commit
exitoso junto con el registro de la entrada VentaCreada como procesada.

Ventas reconoce el tipo y exige eventoId y ventaId no vacíos, y que la venta
exista. Si está PENDIENTE la confirma y publica VentaConfirmada después de su
commit. Si ya estaba resuelta, registra la respuesta como procesada y no vuelve
a resolverla. El DTO admite las líneas, pero el listener actual decide por tipo
y ventaId: **no valida las líneas ni almacena existenciaResultante**.

En ms-ventas la aplicación del cambio y el registro de evento procesado
comparten transacción. En Spring Boot 4.1.1, `max-retries=2` configura **3 intentos
totales** (entrega inicial más dos reintentos), intervalo inicial de 1 segundo
y `default-requeue-rejected=false`. `max-attempts` se retiró en Boot 4 y no
configura ese límite. La [prueba del interceptor real](../../ms-ventas/src/test/java/com/monpoint/ventas/config/ReintentosRabbitTest.java)
cuenta las tres entregas y verifica el rechazo sin reencolado. Tras agotar
los intentos, el rechazo se enruta por el exchange por omisión `""` a la cola
`.fallidos` indicada arriba. No hay garantía de entrega exactamente una vez ni
recuperación automática de mensajes publicados que no alcanzaron una cola.

Si inventario cae tras confirmar la reserva y antes de enviar la respuesta,
la venta puede quedar PENDIENTE. Reentregar la misma VentaCreada ya registrada no
repite el descuento ni reconstruye la respuesta perdida. La prueba manual de
reentrega de CONTROL 2 debe conservar eventoId del JSON y messageId.

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

- [Publicador](https://github.com/MopGEN/MonPoint/blob/674ee3599d519980c6595284df03a278018211e1/monpoint-ms/ms-inventario/src/main/java/com/monpoint/inventario/event/impl/RabbitInventarioEventPublisher.java)
- [Reserva e idempotencia de entrada](https://github.com/MopGEN/MonPoint/blob/674ee3599d519980c6595284df03a278018211e1/monpoint-ms/ms-inventario/src/main/java/com/monpoint/inventario/service/ReservaStockService.java)
- [DTO de entrada](https://github.com/MopGEN/MonPoint/blob/674ee3599d519980c6595284df03a278018211e1/monpoint-ms/ms-ventas/src/main/java/com/monpoint/ventas/event/entrada/StockReservadoEvento.java)
- [Consumidor de saga](https://github.com/MopGEN/MonPoint/blob/674ee3599d519980c6595284df03a278018211e1/monpoint-ms/ms-ventas/src/main/java/com/monpoint/ventas/listener/StockEventoListener.java)
- [Topología](https://github.com/MopGEN/MonPoint/blob/674ee3599d519980c6595284df03a278018211e1/monpoint-ms/ms-ventas/src/main/java/com/monpoint/ventas/config/RabbitConfig.java)

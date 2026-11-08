# StockRechazado — contrato de salida de ms-inventario

Actividad 2.5. Contrato implementado, pendiente de revisión. Versión del sobre: `"1"`.

Publica **ms-inventario**. Consume actualmente **ms-ventas**, mediante
`StockEventoListener`, para cancelar la venta pendiente.
Exchange `inventario.eventos`: topic, durable, sin borrado automático.
Routing key `stock.rechazado`. Cola durable `ventas.stock`, binding explícito;
fallidos: `ventas.stock.fallidos`. Comparte cola con StockReservado.

```json
{
  "eventoId": "e97598be-bc5c-47dc-bbf9-4e3c7ab3d0a8",
  "tipo": "StockRechazado",
  "version": "1",
  "fecha": "2026-11-04T19:00:00Z",
  "origen": "ms-inventario",
  "datos": {
    "ventaId": "68f1a2b3c4d5e6f701234570",
    "productoId": "68f1a2b3c4d5e6f701234567",
    "motivo": "EXISTENCIA_INSUFICIENTE"
  }
}
```

## Campos y motivos

`datos` contiene **solo tres campos publicados**: ventaId y productoId (cadenas
ObjectId), y motivo (código, no texto para el cajero).

| Motivo | Diagnóstico del publicador |
| --- | --- |
| PRODUCTO_NO_ENCONTRADO | El producto consultado al diagnosticar no existe |
| PRODUCTO_INACTIVO | El producto existe pero está inactivo |
| EXISTENCIA_INSUFICIENTE | Falló el descuento condicional y el producto existe activo |

Se informa el primer producto cuya reserva falla en el recorrido agrupado, no
una lista de todos los problemas. La transacción que intentó los descuentos se
revierte completa. Después se registra la entrada VentaCreada como procesada en
otra transacción y se publica el rechazo. No se descuenta ninguna línea por esa
venta. Una caída entre registro y publicación puede perder la respuesta.

## Consumo y motivo visible

Ventas exige eventoId, tipo y ventaId válidos y una venta existente. Para una
PENDIENTE exige también productoId no vacío que pertenezca a las líneas de la
venta y uno de los tres códigos conocidos. Construye el nombre y la cantidad
solicitada sumando las líneas congeladas del producto; cancela conservando
productoIdRechazado para la consulta HTTP y publica VentaCancelada.

Ejemplo de motivo actual: «existencia insuficiente de Tinta para sello,
solicitado 3». productoIdRechazado es un campo de la venta/DTO HTTP: en este evento
se llama **productoId**. Una venta ya resuelta ignora una respuesta posterior,
registrándola como procesada; no se cambia su desenlace.

En ms-ventas la aplicación del cambio y el registro de evento procesado
comparten transacción. La configuración actual habilita **3 intentos totales**,
intervalo inicial de 1 segundo y `default-requeue-rejected=false`. Tras agotar
los intentos, el rechazo se enruta por el exchange por omisión `""` a la cola
`.fallidos` indicada arriba. No hay garantía de entrega exactamente una vez ni
recuperación automática de mensajes publicados que no alcanzaron una cola.

## Hueco abierto: disponible y solicitado

El publicador **no envía** disponible ni solicitado. El DTO de ventas ya declara
ambos como Integer opcionales y hoy quedan nulos; eso no los convierte en parte
de la carga publicada. Además, StockEventoListener.motivoParaCajero **todavía no
usa disponible**: ampliar solo el publicador no cambiará el texto de caja.

Decisión propuesta para revisión: solicitar la ampliación aditiva a Victor, sin
consolidar la ausencia de disponible como solución definitiva. El borrador
existente pide, para EXISTENCIA_INSUFICIENTE, disponible como existencia observada
al rechazar y solicitado como cantidad total agrupada. Para producto inexistente
propone disponible=null. La semántica para producto inactivo debe acordarse.

Esto es una propuesta pendiente, no un contrato implementado ni un mensaje
ya enviado. Requiere cambios coordinados de publicador, consumo del motivo y
pruebas; no inventar disponible desde la réplica ni consultar inventario por HTTP
entre servicios. Hasta entonces se acepta el evento actual y se conserva el
texto sin disponible. Cerrar el hueco exige verificar emisión real, tratamiento
de eventos anteriores sin esos campos y presentación en la consulta de venta.

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

- [Campos publicados y firma actual](https://github.com/MopGEN/MonPoint/blob/674ee3599d519980c6595284df03a278018211e1/monpoint-ms/ms-inventario/src/main/java/com/monpoint/inventario/event/impl/RabbitInventarioEventPublisher.java)
- [Reversión y motivos](https://github.com/MopGEN/MonPoint/blob/674ee3599d519980c6595284df03a278018211e1/monpoint-ms/ms-inventario/src/main/java/com/monpoint/inventario/service/ReservaStockService.java)
- [DTO con campos aún no emitidos](https://github.com/MopGEN/MonPoint/blob/674ee3599d519980c6595284df03a278018211e1/monpoint-ms/ms-ventas/src/main/java/com/monpoint/ventas/event/entrada/StockRechazadoEvento.java)
- [Validación y texto real](https://github.com/MopGEN/MonPoint/blob/674ee3599d519980c6595284df03a278018211e1/monpoint-ms/ms-ventas/src/main/java/com/monpoint/ventas/listener/StockEventoListener.java)
- [Borrador para Victor](https://github.com/MopGEN/MonPoint/blob/674ee3599d519980c6595284df03a278018211e1/monpoint-ms/ms-ventas/docs/mensaje-victor-stock.md)
- [Topología](https://github.com/MopGEN/MonPoint/blob/674ee3599d519980c6595284df03a278018211e1/monpoint-ms/ms-ventas/src/main/java/com/monpoint/ventas/config/RabbitConfig.java)

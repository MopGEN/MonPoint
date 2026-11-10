# ClienteCreado y ClienteActualizado — contrato de ms-clientes

Actividad 2.5. Contrato implementado, pendiente de revisión. Versión del sobre: `"1"`.

Publica **ms-clientes**. Consume actualmente **ms-ventas**, mediante
`ClienteEventoListener`, para mantener `clientes_replica`.
Exchange `clientes.eventos`: topic, durable, sin borrado automático.
Cola durable `ventas.clientes`, binding `cliente.*`; fallidos:
`ventas.clientes.fallidos`.

| Tipo | Routing key | Disparador |
| --- | --- | --- |
| ClienteCreado | cliente.creado | Alta guardada por ClienteServiceImpl.crear |
| ClienteActualizado | cliente.actualizado | Modificación guardada por ClienteServiceImpl.modificar |

Ambos comparten la carga del ejemplo. Cada publicación tiene un eventoId nuevo.

```json
{
  "eventoId": "060aacc0-0d88-4a42-b9a0-5f7abefa7082",
  "tipo": "ClienteCreado",
  "version": "1",
  "fecha": "2026-11-04T19:00:00Z",
  "origen": "ms-clientes",
  "datos": {
    "clienteId": "68f1a2b3c4d5e6f701234568",
    "nombre": "Ana Ruiz",
    "correo": "ana@example.test",
    "telefono": null,
    "direccion": null,
    "fechaActualizacion": "2026-11-04T18:59:59Z"
  }
}
```

## Campos y consumo

- `clienteId`: cadena ObjectId del cliente, usada como id en la réplica.
- `nombre`: cadena; el modelo la recorta con trim.
- `correo`, `telefono`, `direccion`: cadenas o null. El modelo convierte textos
  vacíos tras trim a null; el correo es opcional y no es único.
- `fechaActualizacion`: cadena ISO-8601 del cliente o null si no tiene fecha.
- No se envían `fechaRegistro` ni `activo`.

El DTO tolera campos desconocidos. Ventas exige eventoId, clienteId y nombre no
nulos; conserva solo id, nombre y fecha en clientes_replica. Correo, teléfono y
dirección llegan pero no se almacenan en esa réplica.

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

## Limitación de baja

ClienteServiceImpl.eliminar hace borrado físico y **no publica evento de baja**.
No existe ClienteEliminado en este contrato. Las réplicas actuales no se enteran
de esa eliminación; el catálogo no promete invalidación que el código no emite.

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

- [Publicador y carga](https://github.com/MopGEN/MonPoint/blob/674ee3599d519980c6595284df03a278018211e1/monpoint-ms/ms-clientes/src/main/java/com/monpoint/clientes/event/impl/RabbitClienteEventPublisher.java)
- [Tipos y routing keys](https://github.com/MopGEN/MonPoint/blob/674ee3599d519980c6595284df03a278018211e1/monpoint-ms/ms-clientes/src/main/java/com/monpoint/clientes/event/ClienteEventos.java)
- [Disparadores y eliminación](https://github.com/MopGEN/MonPoint/blob/674ee3599d519980c6595284df03a278018211e1/monpoint-ms/ms-clientes/src/main/java/com/monpoint/clientes/service/impl/ClienteServiceImpl.java)
- [Normalización y nulos](https://github.com/MopGEN/MonPoint/blob/674ee3599d519980c6595284df03a278018211e1/monpoint-ms/ms-clientes/src/main/java/com/monpoint/clientes/model/Cliente.java)
- [Consumidor](https://github.com/MopGEN/MonPoint/blob/674ee3599d519980c6595284df03a278018211e1/monpoint-ms/ms-ventas/src/main/java/com/monpoint/ventas/listener/ClienteEventoListener.java)
- [Topología](https://github.com/MopGEN/MonPoint/blob/674ee3599d519980c6595284df03a278018211e1/monpoint-ms/ms-ventas/src/main/java/com/monpoint/ventas/config/RabbitConfig.java)

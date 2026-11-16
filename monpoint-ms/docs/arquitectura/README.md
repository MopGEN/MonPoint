# Diagramas de arquitectura y datos — 6.3 / H-092

Estado de la implementación: `6bc8480` (PR #32), 16 de noviembre de 2026.
Los diagramas describen código y configuración de esa base; no son evidencia
de una nueva ejecución ni una certificación de los datos de una instalación.

| Criterio de H-092 | Diagrama vigente | Fuente editable |
|---|---|---|
| Componentes: servicios, infraestructura, broker y cliente | [Componentes y tráfico](renders/01-componentes-actual.svg) | [Mermaid](01-componentes-actual.mmd) |
| Secuencia de la saga, incluido el rechazo | [Saga de venta](renders/02-saga-venta.svg) | [Mermaid](02-saga-venta.mmd) |
| Modelo documental por servicio | [Seis vistas por base](03-modelo-documental.md) | Seis archivos `03-modelo-*.mmd` enlazados en esa ficha |

Los SVG se pueden abrir y ampliar sin conexión en un navegador o insertar en
la presentación. Las fuentes Mermaid permiten revisar y modificar el contenido
sin reconstruir un dibujo a partir de una captura.

## Componentes y continuidad con el diagrama existente

Se conserva **sin modificar** [01-componentes-y-trafico.jpg](01-componentes-y-trafico.jpg),
que ya mostraba servicios, infraestructura, cliente, broker y bases separadas.
Su parte de Notificaciones atribuía también el consumo de VentaConfirmada y
VentaCancelada. El consumidor actual solo recibe **StockBajo**: la nueva vista
editable corrige esa leyenda y no elimina el original histórico.

Líneas continuas entre cliente/Gateway/servicios: HTTP. Líneas hacia bases:
persistencia propia. Entre dominios, las flechas por RabbitMQ representan
publicación y consumo de eventos; no llamadas HTTP. Las líneas de Eureka
representan registro y descubrimiento. No se modela una base distinta por
contenedor Mongo: hay una instancia en replica set con seis bases y usuarios
restringidos por servicio.

El enlace agregado del grupo al broker no significa que cada integrante
publique y consuma todo. La distribución precisa es:

| Servicio | Publica | Consume |
|---|---|---|
| auth | Fuera del bus | Fuera del bus |
| clientes | ClienteCreado, ClienteActualizado | Ninguno |
| inventario | ProductoCreado, ProductoActualizado, ProductoDadoDeBaja; StockReservado, StockRechazado, StockBajo | VentaCreada |
| ventas | VentaCreada, VentaConfirmada, VentaCancelada | ProductoCreado/Actualizado/DadoDeBaja, ClienteCreado/Actualizado, StockReservado/Rechazado |
| reportes | Ninguno | ProductoCreado/Actualizado/DadoDeBaja, ClienteCreado/Actualizado, VentaConfirmada/Cancelada |
| notificaciones | Ninguno | StockBajo |

El Gateway sustituye las cabeceras de identidad del cliente; auth revalida el
JWT. Los otros cinco servicios dependen de esas cabeceras y del aislamiento de
la red interna. El diagrama no añade mTLS, una guarda para el ticket, revocación
JWT ni outbox. Sus límites de seguridad están en [6.2](../api/seguridad.md).

## Cómo leer la secuencia de venta

- POST /ventas valida identidad y réplicas antes de insertar la venta y asignar
  folio. Un 400 previo al registro no es el rechazo de stock de una saga aceptada.
- El registro confirma su transacción antes de publicar VentaCreada; el 202
  lleva id, folio y PENDIENTE. La entrega puede adelantarse a la recepción del
  HTTP: el primer GET puede encontrar ya CONFIRMADA o CANCELADA.
- Inventario descuenta todas las líneas o ninguna. En éxito, descuento y
  eventoId comparten transacción. En rechazo, se revierten descuentos **y**
  marcador de esa transacción; luego se registra el marcador en otra transacción,
  y solo después se publica StockRechazado.
- Ventas aplica estado terminal y eventoId en una transacción; publica el cierre
  tras el commit. Reportes proyecta ambos cierres. Canceladas no suman en la
  gráfica y su ticket devuelve 409; Notificaciones no recibe esos cierres.
- StockBajo se publica después de una reserva exitosa cuando una existencia
  resultante alcanza o baja el umbral. No se publica por el simple alta del
  catálogo ni por leer una alerta.
- El sondeo HTTP corre independientemente de los consumidores. Las regiones
  `par` muestran flujos concurrentes, no una transacción distribuida ni una
  barrera del sistema que haga esperar a Caja hasta la proyección de reportes.

La reentrega dibujada conserva el mismo eventoId **ya procesado**: inventario
la descarta sin repetir el descuento. Los demás consumidores tienen sus propios
marcadores. Eso no equivale a entrega exactamente una vez. Una excepción técnica
puede terminar tras tres entregas AMQP en la cola de fallidos; no crea por sí
sola una venta CANCELADA. Los reintentos de transacción de un consumidor y los
reintentos AMQP son mecanismos diferentes.

**Límite sin outbox:** un proceso puede confirmar su base y caer antes de publicar.
La saga puede quedar PENDIENTE o reportes atrasado. Una reentrega cuyo marcador
ya existe no vuelve a publicar la respuesta perdida. Tampoco hay idempotencia
HTTP: repetir un POST /ventas incierto puede crear otra venta.

## Fuentes de la secuencia y la topología

- [Compose](../../infra/docker-compose.yml), [rutas del Gateway](../../api-gateway/src/main/resources/application.properties) y [filtro JWT](../../api-gateway/src/main/java/com/monpoint/gateway/filter/JwtAuthenticationGlobalFilter.java).
- [VentaService](../../ms-ventas/src/main/java/com/monpoint/ventas/service/VentaService.java), [StockEventoListener](../../ms-ventas/src/main/java/com/monpoint/ventas/listener/StockEventoListener.java) y [publicador de ventas](../../ms-ventas/src/main/java/com/monpoint/ventas/event/impl/RabbitVentaEventPublisher.java).
- [ReservaStockService](../../ms-inventario/src/main/java/com/monpoint/inventario/service/ReservaStockService.java) y [publicador de inventario](../../ms-inventario/src/main/java/com/monpoint/inventario/event/impl/RabbitInventarioEventPublisher.java).
- [Proyección de cierres](../../ms-reportes/src/main/java/com/monpoint/reportes/listener/VentaCierreListener.java) y [alertas](../../ms-notificaciones/src/main/java/com/monpoint/notificaciones/service/NotificacionService.java).
- Contratos de [VentaCreada](../eventos/venta-creada.md), [StockReservado](../eventos/stock-reservado.md), [StockRechazado](../eventos/stock-rechazado.md), [cierre](../eventos/venta-cierre.md) y [StockBajo](../eventos/stock-bajo.md).

## Renderizado y verificación

Se generaron los SVG con Mermaid 11.17.2 (instalada mediante Mermaid CLI
11.12.0), Chromium y
[mermaid-config.json](mermaid-config.json). Por ejemplo, con Node y el navegador
que requiera Puppeteer configurados en el equipo:

```bash
npx --yes --package @mermaid-js/mermaid-cli@11.12.0 mmdc -i 02-saga-venta.mmd -o renders/02-saga-venta.svg -c mermaid-config.json -b white
```

Repetir para los otros `.mmd`. En este entorno el navegador bloquea file://;
el motor Mermaid se sirvió por HTTP local para renderizar las mismas fuentes,
sin cambiar las políticas del navegador. El renderizador usa tipografía Arial/sans-serif;
el trazado y espaciado pueden variar con versión y fuentes. Las fuentes, no una
igualdad de píxeles entre máquinas, son el contenido revisable.
La verificación de 6.3 comprende parseo/render de ocho fuentes, inspección visual,
contraste de las colecciones/campos con el mapping e inventario de referencias.
No se ejecutan ventas ni se inspecciona MongoDB de Windows para generar estos diagramas.

## H-095 y trazabilidad del alcance

El Documento 03 v3.2 mantiene H-095 (p. 18): guion por módulo, rechazos de
credenciales/existencia/rol y precondición, pasos, resultado esperado y registro
de ejecución. Su sección 9.4 también lo cita junto con H-094. La advertencia de
p. 2 remite al Documento 01 y Asana para decidir qué se construye de las 58 historias.

El Documento 01 v3.4, sección 7.2 (p. 8), enumera las actividades de S6 por 16
puntos sin asignar una actividad propia a H-095. La sección 7.4 (p. 9) recorta
cinco grupos funcionales y no cancela expresamente ese guion. Con esos documentos
no se acredita ni su cancelación ni una asignación independiente de tres puntos
en el tablero actual. Es una brecha de trazabilidad, no un entregable ya cerrado.

Recomendación para 6.4: conservar sus criterios como lista de aceptación y
registrar cómo se cubren o qué falta; no sumar puntos ni declarar su ejecución
por deducción. Esta entrega 6.3 no crea un guion H-095, no actualiza Asana ni
modifica los PDF de planeación.

Fuentes: [Documento 01](../planeacion/MonPoint_MS_01_Plan_de_Trabajo_del_Equipo.pdf)
y [Documento 03](../planeacion/MonPoint_MS_03_Product_Backlog_y_Convenciones_de_Trabajo.pdf).

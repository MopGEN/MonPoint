# Reentrega de VentaCreada desde la consola de RabbitMQ

5 de noviembre de 2026, entorno Linux de la nube. Referencia de código: `main`
en `8f3d44c` (PR #17). Evidencia del criterio de reentrega de CONTROL 2 y avance
parcial de la 6.1; este informe no evalúa los demás criterios ni modifica Asana.

## Resultado

| Dato | Valor observado |
|---|---|
| Producto | `Semilla 3.1 - Tinta` |
| productoId | `6aebe0094ced4caee15ce519` |
| Venta / folio | `6aeccd409548df5c96ce23bf` / **28** |
| Cantidad y total | 1 unidad / **41.15** |
| Existencia antes de vender | **10** |
| Existencia tras confirmar | **9** |
| Existencia tras republicar el mismo evento | **9** |
| Estado de la venta antes y después de republicar | **CONFIRMADA**, respuestas de consulta idénticas |
| eventoId y message_id conservados | `03d3f3b0-84fa-46d0-8f70-e297e8d898bd` |
| StockReservado original observado | 1, con el mismo ventaId |
| StockReservado adicional observado | 0, en dos consultas de la cola temporal |
| Limpieza | Ambas colas temporales eliminadas mediante la consola, HTTP 204 |
| Estado final del conjunto | Nueve contenedores saludables |

Línea exacta del consumidor, también conservada en `inventario-descarte.txt`:

```text
2026-11-05T18:40:18.773Z  INFO [] 1 --- [ms-inventario] [ntContainer#0-1] c.m.i.service.ReservaStockService        : evento=VENTA_CREADA_DESCARTADO eventoId=03d3f3b0-84fa-46d0-8f70-e297e8d898bd motivo=ya_procesado
```

## Cómo se hizo

Se operó **la consola web** en `http://localhost:15672` con Chromium y Playwright.
La venta se hizo desde Caja en `http://localhost:3000`. Las operaciones de crear
colas, enlazarlas, obtener mensajes, publicar y borrar colas se hicieron mediante
sus formularios. No se fabricó VentaCreada ni se invocó directamente un listener.
Las consultas de inventario y venta se hicieron por HTTP a través del Gateway.

1. Se creó `captura.venta-creada` como **Transient**, enlazada a `ventas.eventos`
   con `venta.creada`. Se creó además `captura.stock-reservado`, también
   transitoria, enlazada a `inventario.eventos` con `stock.reservado`. Ambas estaban
   enlazadas antes de vender y carecían de consumidores.
2. Se consultó la existencia y se cobró una unidad desde Caja. Se conservaron
   el 202, la consulta CONFIRMADA y la existencia posterior: 10 → 9.
3. En Get messages de la primera cola se eligió **Nack message requeue true**.
   El payload recibido se guardó como texto original, sin reconstruirlo ni
   reformatearlo, en `venta-creada.payload.json`.
4. En la cola de StockReservado se obtuvo el único mensaje inicial con
   **Automatic ack**, retirándolo para poder observar nuevas publicaciones.
   Su carga y propiedades se conservaron en `estado.json`.
5. En Exchanges → ventas.eventos → Publish message se pegó el payload original,
   routing key `venta.creada`, `message_id` igual al original y `content_type`
   `application/json`. La consola recibió `{"routed":true}`.
6. Se comparó el payload de la petición enviada por la consola con el texto
   capturado: igualdad exacta. SHA-256 de ambos:
   `06bbd0b4658d66c3d17723a3cf7cfaa98f5f4df8dd08c35396931c31f30a6dfe`.
7. Después del descarte del consumidor, dos Get messages de la cola de
   StockReservado devolvieron una lista vacía; hubo una espera de 10 segundos
   entre ambas operaciones. Las capturas muestran **Queue is empty**. Las
   consultas por API confirmaron stock 9 y la venta sin cambios.
8. Se borraron ambas colas desde sus formularios. Los dos DELETE devolvieron
   204 y la lista final no contiene ninguna de ellas. No quedaron bindings de
   captura acumulando eventos futuros. Los datos de la venta real se conservan.

## Evidencia conservada

| Archivo | Qué muestra |
|---|---|
| `01-cola-captura.png`, `02-cola-stock.png` | Preparación de las colas temporales |
| `03-venta-confirmada.png` | Caja, folio 28 y total del servidor |
| `04-venta-creada-original.png` | Mensaje recibido, propiedades y payload original |
| `05-stock-reservado-original.png` | Respuesta de reserva de la primera entrega |
| `06-republicar-mismo-evento.png`, `07-publicacion-aceptada.png` | Formulario de republicación y su resultado |
| `08-sin-segundo-stock-reservado.png`, `09-stock-vacio-segunda-consulta.png` | Dos consultas posteriores sin mensajes |
| `10-existencia-conservada.png` | Catálogo actualizado con existencia 9 |
| `11-colas-temporales-eliminadas.png` | Lista final de colas, sin las dos de captura |
| `estado.json` | Producto y venta antes/después, mensajes originales, petición de republicación y limpieza |
| `venta-creada.payload.json` | Texto exacto del evento procesado y republicado |
| `inventario-descarte.txt` | Línea de log del consumidor para ese eventoId |
| `contenedores.json` | Imágenes y salud de los nueve contenedores al terminar |
| `colas-finales.txt` | Verificación adicional de limpieza con rabbitmqctl: ocho colas originales, ninguna de captura, sin mensajes pendientes |
| `guiones/` | Pasos de navegador ejecutados y recuperación de la captura final |
| `SHA256.json` | Integridad de todos los archivos del recorrido |

Las capturas y payload se conservan sin editar. Los guiones son registros de
esta sesión, con rutas absolutas y estado de navegador compartido; **no son un
ejecutable único para repetir a ciegas**. Algunos son intentos de preparación
interrumpidos, descritos a continuación. No contienen credenciales del entorno.

## Incidencias y límites

- Fue una operación de la consola mediante navegador automatizado en Linux,
  no una sesión humana en Windows. El equipo puede valorar esa distinción al
  evaluar literalmente el criterio de reentrega manual.
- El refresco automático de RabbitMQ cerraba paneles mientras se llenaban los
  formularios. Se desactivó y se retomó la preparación; todavía no había venta.
  Un selector ambiguo de Publish message se corrigió antes de publicar.
- `comprobar.mjs` completó el descarte, las dos lecturas vacías y las consultas
  de API, pero falló después al buscar la pestaña de Caja. Los pasos anteriores
  seleccionaban pestañas por índice y al reconectar habían reutilizado esa
  pestaña para RabbitMQ. Las capturas de cola vacía y el log ya estaban guardados;
  **las dos respuestas JSON vacías no llegaron a archivarse**. Se completó la
  captura en un contexto nuevo y se repitieron solo las consultas de producto y
  venta. No hubo una segunda venta ni otra republicación para corregirlo.
- Ausencia de otro StockReservado: observación acotada a las dos consultas y
  respaldada por el log de descarte, no una vigilancia indefinida. El estado
  CONFIRMADA por sí solo no se usó como prueba de ausencia de otra publicación.
- Se republicó un mensaje con el mismo eventoId; no se forzó una caída de
  conexión ni se acreditó el indicador AMQP `redelivered`. Se verifica la
  idempotencia lógica del consumidor ante el duplicado solicitado.
- Se reutilizaron los contenedores existentes. No es una prueba de despliegue
  limpio de la PR #17. El código de inventario y ventas no tiene diferencias
  entre la revisión `034bdfe` del recorrido de nginx y `8f3d44c`.
- No se prueban reportes, notificaciones, la fase 2 de la semilla ni el resto de
  criterios de la 6.1. Tampoco se afirma que la reunión de CONTROL 2 haya ocurrido.

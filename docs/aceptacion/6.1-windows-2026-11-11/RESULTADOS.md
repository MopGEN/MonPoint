# Recorrido de aceptación 6.1 — ejecución única sobre volúmenes nuevos

11 de noviembre de 2026, 01:05–02:35 hora local (`America/Mexico_City`, UTC−06).
Instantáneas de cierre recolectadas a las 03:08:04–03:08:12 de ese mismo día.
Windows 11 con Docker Desktop, PowerShell 5.1 y Chrome, operado por una persona.
Referencia de código declarada por el operador: `main` en `1244393` — *3.1 Agrega la fase de ventas
confirmadas a la semilla (#22)*.

Este informe cubre los **siete escenarios de la actividad 6.1** y la comprobación
de reentrega de CONTROL 2. No evalúa los demás criterios de los controles ni
modifica Asana.

El operador declara **una sola ejecución**, con volúmenes destruidos y
reconstruidos antes de sembrar. Las capturas conservadas muestran el arranque
con `docker compose up -d`, los once contenedores saludables y los folios 1–3;
no incluyen la salida de `down -v` ni de la compilación. El arranque sin estado
previo se declara, pero no queda demostrado de forma independiente por esos
archivos. Ninguna observación se toma de una ejecución anterior.

Diferencia con la [reentrega del 5 de noviembre](../6.1-reentrega-2026-11-05/RESULTADOS.md):
aquella se operó con un navegador automatizado sobre Linux, y su propio informe
señalaba esa distancia respecto al criterio de reentrega manual. Esta se hizo a
mano, desde Windows, en la consola web de RabbitMQ.

## Estado inicial y final

| Dato | Al terminar la semilla | Al terminar el recorrido |
|---|---|---|
| `Semilla 3.1 - Tinta` | 5 | 5 |
| `Semilla 3.1 - Papel` | 8 | 7 |
| `Semilla 3.1 - Sello` | 9 | 8 |
| Folios emitidos | 1, 2, 3 (CONFIRMADAS) | 1–6; folio 5 CANCELADA |
| Agregación del día | **476.75** | **672.25** |
| Notificaciones sin leer | 1 (Tinta) | 1 (Tinta), activa y sin leer |
| Contenedores saludables | 11 | 11, todos running/healthy |

476.75 + 75.50 (folio 4) + 120.00 (folio 6) = 672.25. El folio 5, cancelado, no
aporta: la agregación filtra por `estado: CONFIRMADA`.

La [recolección de cierre](entorno/20261111T090804848Z/recoleccion.json), en
PowerShell `5.1.26100.9711`, confirma esos valores por API y Docker. Conserva las
seis ventas: cinco CONFIRMADAS (folios 1, 2, 3, 4 y 6), cuya suma exacta es
672.25, y la CANCELADA del folio 5 por 4073.85, excluida de la agregación.
Las reservas confirmadas suman 5 unidades de Tinta, 3 de Papel y 2 de Sello;
partiendo de 10 por producto resultan 5/7/8. La alerta sigue siendo la original,
con el mismo identificador, fecha y existencia 5.

El broker tiene dieciséis colas, todas vacías: ocho de trabajo con un consumidor
cada una y ocho de fallidos sin consumidores. No quedan colas `captura.6-1.*`.
Los bindings conservan los enlaces exactos de reserva/rechazo, alerta y cierre
de ventas hacia sus consumidores. Los cuatro registros de inventario se
archivaron como texto además de las capturas.

## Resultado por escenario

| # | Escenario | Resultado observado |
|---|---|---|
| 1 | Venta exitosa desde el cliente web | Folio **4**, `1 × Semilla 3.1 - Papel`, **75.50**. Creación con **202**, dos consultas con **200** y confirmación visible. Los cuerpos de esas respuestas no se archivaron; no se deduce PENDIENTE del código 200 |
| 2 | Descuento correcto de existencias | Papel **8 → 7**, comprobado por API y en el catálogo del cliente tras refrescarlo |
| 3 | Venta reflejada en la agregación | **476.75 → 552.25**, incremento exacto de 75.50, en el cubo `2026-11-11`; los días vecinos en 0 |
| 4 | Venta rechazada sin descuento | Folio **5**, Tinta × 99, CANCELADA con motivo `existencia insuficiente de Semilla 3.1 - Tinta, solicitado 99`; existencias **5 / 7 / 9** intactas y agregación en 552.25 |
| 5 | Alerta de existencia bajo umbral | Una notificación `STOCK_BAJO` de Tinta, `existenciaResultante: 5`, `activa: true`, `leida: false`, creada a las `07:16:19.454Z` |
| 6 | Reentrega sin doble descuento | `eventoId` `1eb998dc-…` republicado al exchange; el consumidor responde `VENTA_CREADA_DESCARTADO … motivo=ya_procesado`. Sello sigue en **8**, la venta sigue CONFIRMADA con su `fechaResolucion` original y la agregación en **672.25** |
| 7 | VENDEDOR en operación de ADMIN | Token real de `vendedor.semilla@monpoint.test`. `GET /inventario/productos` → **200**; `GET /notificaciones?leida=false` → **403** |

Comprobación complementaria registrada: una petición **sin cabecera
`Authorization` devuelve 401 en el Gateway**, no 403. Los dos rechazos provienen
de capas distintas — autenticación en el borde, autorización en el servicio — y
el recorrido muestra ambos.

## Detalle de la reentrega (escenario 6 y CONTROL 2)

| Dato | Valor |
|---|---|
| Venta / folio | `6af41fae2d1858377c263c90` / **6** |
| Producto y total | `Semilla 3.1 - Sello`, 1 unidad, **120.00** |
| `eventoId` = `message_id` | `1eb998dc-c565-44d9-9cfb-8e993b91eab6` |
| Tamaño del payload archivado | **422 bytes**; la copia se pegó del original según el operador. No hay una captura binaria independiente de ambas entregas |
| Existencia antes / después de republicar | **8 / 8** |
| Estado de la venta antes / después | CONFIRMADA, `fechaResolucion` `07:56:30.354Z` en ambos casos |
| `captura.6-1.stock-reservado` antes / después | **0 / 0** |
| `captura.6-1.venta-creada` antes / después | **0 / 1** |

Las cuatro líneas del consumidor, primera entrega y reentrega, transcritas de las capturas:

```text
2026-11-11T07:56:30.306Z  INFO [] 1 --- [ms-inventario] [ntContainer#0-1] c.m.i.event.VentaCreadaListener  : evento=VENTA_CREADA_RECIBIDO eventoId=1eb998dc-c565-44d9-9cfb-8e993b91eab6
2026-11-11T07:56:30.336Z  INFO [] 1 --- [ms-inventario] [ntContainer#0-1] c.m.i.service.ReservaStockService : evento=STOCK_RESERVADO eventoId=1eb998dc-c565-44d9-9cfb-8e993b91eab6 ventaId=6af41fae2d1858377c263c90 lineas=1

2026-11-11T08:17:11.801Z  INFO [] 1 --- [ms-inventario] [ntContainer#0-1] c.m.i.event.VentaCreadaListener  : evento=VENTA_CREADA_RECIBIDO eventoId=1eb998dc-c565-44d9-9cfb-8e993b91eab6
2026-11-11T08:17:11.803Z  INFO [] 1 --- [ms-inventario] [ntContainer#0-1] c.m.i.service.ReservaStockService : evento=VENTA_CREADA_DESCARTADO eventoId=1eb998dc-c565-44d9-9cfb-8e993b91eab6 motivo=ya_procesado
```

La prueba no se apoya solo en que *no* aparezca un segundo `StockReservado`. Una
ausencia es compatible con un mensaje que nunca llegó, un enlace mal hecho o un
consumidor caído. La línea `VENTA_CREADA_DESCARTADO` convierte la prueba en
afirmativa: el mensaje llegó, se deserializó y el servicio decidió descartarlo.

Las marcas de tiempo de esos registros están separadas por 30 ms en la primera
entrega y 2 ms en la reentrega. Son intervalos entre logs, no una medición de
duración de la saga o de la publicación: el código registra `STOCK_RESERVADO`
antes de publicar. El descarte por `existsByEventoId` se confirma por el log y
el código, sin necesitar una afirmación de rendimiento.

La cola de captura de reservas estaba enlazada y vacía antes de republicar.
Después hay una captura con `Queue is empty`; el operador confirmó una segunda
lectura vacía tras esperar unos 10 segundos, sin adjuntar otra captura de esa
lectura. La ausencia observada queda acotada a esas comprobaciones.

La identidad de deduplicación es el `eventoId` **del cuerpo**, no la propiedad
AMQP: `VentaCreadaListener` deserializa el mensaje y pasa `evento.eventoId()` a
`ReservaStockService`, que consulta `eventos_procesados` y, dentro de la
transacción, inserta contra un índice único. El `message_id` se reprodujo por
fidelidad, pero el consumidor no lo lee.

## Cómo se hizo

1. El operador declara `docker compose down -v` y `docker compose up -d --build`.
   La evidencia de arranque archivada comienza con la resolución del puerto
   reservado de Eureka y muestra después `docker compose up -d` y once
   contenedores saludables. No se conserva el tiempo de compilación.
2. Semilla fase 1 por API a través del Gateway: vendedor, dos clientes y tres
   productos, todos `CREADO`. El ADMIN sale `REUTILIZADO` porque lo crea
   `DataInitializer` al arrancar ms-auth con la colección vacía.
3. Semilla fase 2: tres ventas secuenciales confirmadas, folios 1–3, con
   `rechazosReplica` en cero tanto para producto como para cliente.
4. Línea base comprobada por API: existencias, notificación y agregación.
5. Escenarios 1, 4 y 6 desde la caja en `http://localhost:3000`, con la pestaña
   de red abierta. Cada efecto se contrastó por API, no solo en pantalla.
6. Reentrega: se crearon dos colas transitorias de captura. La de VentaCreada
   capturó el original; la de StockReservado tiene su enlace y lectura vacía
   acreditados antes de republicar. No se demuestra que ese segundo enlace
   existiera antes de la venta original. Ambas se eliminaron al terminar.
7. Escenario 7 con un token obtenido por `Get-Credential`, sin exponer la clave
   en el historial de la consola.

## Evidencia conservada

| Carpeta | Contenido |
|---|---|
| `00-arranque/` | Rango de puertos excluidos, exclusión administrada de 8761, los once contenedores saludables, puertos publicados |
| `01-semilla/` | Salidas JSON de las dos fases, línea base de notificación y agregación, existencias iniciales 5/8/9 |
| `02-escenarios/` | Carrito, folio 4 con su traza de red, catálogo refrescado, agregación 552.25, folio 5 cancelado, folio 6 confirmado y existencias en cada punto |
| `03-reentrega/` | Creación y enlace de las colas, mensaje original con sus propiedades, log de la primera entrega, colas en cero, bindings del exchange, publicación aceptada, log del descarte, agregación final y limpieza |
| `04-autorizacion/` | Vendedor con 200 y 403; petición sin token con 401 |
| `incidencias/` | Tabla de PowerShell, purga de la cola equivocada, intento no enrutado y consola sin variables; la incidencia de puertos se conserva en `00-arranque/` |
| `venta-creada.payload.json` | Texto exacto del evento procesado y republicado, 422 bytes |
| `entorno/20261111T090229047Z/` | Primer intento, INCOMPLETA por el fallo del recolector; se conserva sin presentarlo como éxito |
| `entorno/20261111T090804848Z/` | Recolección COMPLETADA: contenedores, colas, bindings, revisión, montajes/volúmenes, cuatro líneas de log y las instantáneas de productos, notificaciones, agregación y seis ventas |
| `SHA256.json` | Integridad de los 59 archivos del recorrido, incluidos ambos intentos; excluye el propio manifiesto |

Las capturas se conservan sin editar, incluidas las que muestran errores.

## Incidencias y límites

**Incidencias resueltas durante el recorrido**

- *Eureka no arrancaba.* `bind: WSAEACCES` al publicar 8761. No era un conflicto
  de puerto — eso lo reporta Docker como *port is already allocated* — sino el
  rango 8691–8790 reservado dinámicamente por `winnat`. Se resolvió con
  `net stop winnat`, una exclusión administrada del 8761 y `net start winnat`.
  **El bloque reservado es dinámico**: en otro arranque puede capturar 8080 o
  5672. Queda como prerrequisito documentado de Windows, no como anécdota.
- *Primer intento de republicación no enrutado.* La consola respondió
  `Message published, but not routed`; el segundo intento respondió
  `Message published` y fue recibido por inventario. Los espacios en la routing
  key son una explicación posible, pero las capturas no conservan su valor
  exacto como para establecer la causa. Ambas respuestas se conservan.
- *Tabla de productos vacía en PowerShell.* `Invoke-RestMethod` en PowerShell 5.1
  **no enumera** el arreglo JSON hacia la canalización: lo entrega como un único
  objeto, y `Select-Object nombre` recibía el `Object[]`. Asignando a una
  variable y canalizando la variable, se enumera. Ningún dato del backend estuvo
  afectado; se verificó con la respuesta cruda.
- *Purga de la cola equivocada.* Se purgó `captura.6-1.venta-creada` en lugar de
  `captura.6-1.stock-reservado`. Sin consecuencia: su payload ya estaba
  conservado y ambas quedaron en cero antes de republicar, que era el estado
  buscado.
- *Autenticación desde otra consola sin variables.* El primer intento del
  vendedor falló por una URI inválida al faltar `$g`; no llegó al servidor.
  Se repitió con la URL definida y produjo el control positivo 200 y el 403.
  Después, una consulta fuera de `& { ... }` no conservó `$hV`: mostró 401.
  No quedó archivada la cabecera efectiva de esa consulta. El código del Gateway
  sí genera un `ProblemDetail` para el 401; que PowerShell no lo imprimiera no
  demuestra que la respuesta careciera de cuerpo.

**Límites de lo que este informe acredita**

- Los escenarios 1, 4 y 6 se ejecutaron con **sesión de ADMIN** en el cliente
  web, no de VENDEDOR. El recorrido de caja no se hizo con el rol de caja.
- **El cuerpo del 403 no quedó registrado**, solo el código de estado. No consta
  en la evidencia la frase del `ProblemDetail` que nombra la regla aplicada.
- El escenario 7 se verificó contra **un solo servicio**, ms-notificaciones. Los
  demás endpoints exclusivos de ADMIN no se ejercitaron en esta ejecución.
- **El `StockReservado` original no se archivó**: su cola se purgó sin extraer el
  payload. El log acredita la ruta de reserva, la venta y el número de líneas;
  no acredita el contenido exacto del mensaje publicado.
- La consulta de log previa a republicar salió vacía **porque la ventana
  `--since 10m` no alcanzaba las 07:56Z**, no porque faltaran las líneas
  originales. No debe leerse como ausencia de la primera entrega.
- El estado intermedio PENDIENTE no se capturó en pantalla ni en un cuerpo JSON.
  Dos GET con 200 no permiten deducir sus estados ni su orden respecto a la
  resolución. Tampoco se acredita una duración visual del estado En proceso.
- La notificación del escenario 5 la produjo la **primera venta de la semilla**,
  no una acción manual del recorrido. La semilla forma parte de esta ejecución,
  pero el umbral no se cruzó dos veces: no se observó el comportamiento del
  índice parcial ante una segunda alerta del mismo producto.
- Las colas de captura eran transitorias y se eliminaron al terminar. El listado
  final vuelve a mostrar las dieciséis colas del sistema.
- Los metadatos de MongoDB registran el volumen de datos creado a las
  `06:58:01Z` (00:58:01 local) y el anónimo de configuración a las `06:58:02Z`.
  Son compatibles con el arranque nuevo declarado, pero no reconstruyen la
  salida histórica de `down -v` ni documentan la creación del volumen RabbitMQ.
  `revision.txt` registra `54a75f5` al recolectar; el cambio posterior del HEAD
  corresponde a documentación y pruebas. No certifica por sí solo las fuentes
  usadas para construir las imágenes del recorrido declarado en `1244393`.
- Solo hay una captura de la lectura vacía posterior a republicar. La segunda
  fue confirmada por el operador. El enlace previo y consumidores en cero hacen
  útil esa observación, pero no se conservó un StockReservado original que
  probara antes el funcionamiento de la cola de captura.

## Integridad y revisión de cierre

El archivo `venta-creada.payload.json` tiene 422 bytes y SHA-256
`afdf2ed67a9adf577f503df0cea874442fd26d19f76f31985fd38a698091bb8e`.
El tarball recibido contiene 36 PNG y tres archivos adicionales dentro de esta
carpeta, más el README de aceptación externo; no incluía instantáneas `entorno/`
ni manifiesto. El commit `ff4cbb0` añadió los dos intentos de recolección y el
manifiesto de 59 archivos desde el Windows original, sin repetir la semilla.
Los archivos de cada intento están fechados y se conservan por separado.

Revisión del paquete recibido: 39 archivos dentro del directorio del recorrido
y el README externo. SHA-256 del tarball original:
`0e77beb0f9753bcb40dc6110eb3e794fa66f9dae2232f0719f5237a7a0d13f05`.
Se compararon byte a byte los 36 PNG y el payload tras corregir el acta y el
recolector; permanecen idénticos. La revisión inicial fue sintáctica y estática;
el primer intento real en Windows falló al enumerar los montajes: el bucle
recibía el arreglo JSON completo como un montaje y no encontraba `Name`.
La corrección asigna el resultado de `ConvertFrom-Json` antes de recorrerlo.
El intento fallido se conserva como INCOMPLETA; no generó un manifiesto de éxito.
Las [pruebas del recolector](../pruebas/recolectar.test.ps1) ejercitan seis casos
con respuestas simuladas y arreglos no enumerados: éxito, montajes vacíos,
fallos nativos/HTTP, ausencia de descarte y folio discordante. Comprueban además
la integridad del manifiesto y que no se guarden credenciales. Pasaron en
PowerShell 7.4.6 sobre Linux. Después, la recolección completa sí se ejecutó en
PowerShell 5.1 contra el conjunto Windows, como registra el intento COMPLETADA;
no se afirma que las seis pruebas simuladas se ejecutaran también en Windows.
Las instantáneas se obtienen secuencialmente, sin una transacción global.

En la revisión de `ff4cbb0`, 57 archivos coincidieron exactamente con sus hashes.
`RESULTADOS.md` y `recolectar.ps1` estaban versionados con LF, mientras que el
manifiesto describía sus bytes CRLF de Windows. Convertir únicamente sus saltos
de línea a CRLF reprodujo exactamente los dos tamaños y hashes declarados.
La regla `-text` ya estaba añadida, pero esos archivos previamente seguidos no
habían quedado incorporados con los bytes de Windows.

Se restauró CRLF en el recolector y se actualizó esta acta con las instantáneas
revisadas, conservando CRLF. El manifiesto final se recalculó sobre los archivos
efectivamente versionados. Las 36 capturas, el payload y las veinte instantáneas
de ambos intentos permanecen byte a byte como se recibieron. La verificación
final comprende el conjunto exacto de rutas, tamaño y SHA-256 de los 59 archivos,
además de las sumas monetarias, existencias, estados, colas y salud de cierre.

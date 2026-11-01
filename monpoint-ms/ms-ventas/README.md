# ms-ventas — réplicas y saga de venta (3.6, 4.1 y 4.4)

## Registro de ventas

`POST /ventas` a través del Gateway. Requiere token con rol ADMIN o VENDEDOR.
El servicio recibe la identidad mediante `X-User-Id` y `X-User-Role`, que el
Gateway sobrescribe; Compose no publica el puerto de ventas.

```json
{"clienteId":null,"lineas":[{"productoId":"68f1a2b3c4d5e6f701234567","cantidad":3}]}
```

Responde **202 Accepted**, por ejemplo:

```json
{"id":"68f1a2b3c4d5e6f701234570","folio":1042,"estado":"PENDIENTE"}
```

Los campos adicionales como total, precio, nombre, folio o estado se ignoran.
El total se calcula con BigDecimal a partir de las réplicas; el total y los
precios unitarios se guardan como Decimal128. Las líneas embebidas congelan
nombre y precio; la venta congela `clienteNombre`. No se almacena subtotal.

- Sin líneas, cantidades no positivas/fraccionarias, producto no disponible o
  inactivo: **400**. Tampoco se permite desbordar Int32 sumando líneas del mismo
  producto, porque ese es el contrato que recibe inventario.
- Cliente omitido o nulo: venta sin cliente. Si se envía un cliente no disponible
  en la réplica: **400**, con este `detail` que la caja de 5.5 debe mostrar tal cual:
  `el cliente no está disponible; si acaba de darlo de alta, inténtelo de nuevo en unos segundos`
- `clienteId` (cuando se envía) y `productoId` deben tener 24 caracteres
  hexadecimales. Un formato inválido devuelve **400** con un `detail` específico
  del campo, antes de abrir la transacción o consultar las réplicas.
- La réplica es eventualmente consistente: no se puede distinguir una alta que
  aún no llegó de un identificador válido pero inventado. No se consulta a ms-clientes.
- Sin identidad: **401**; rol distinto de ADMIN/VENDEDOR: **403**.

Orden: validar réplicas → congelar/calcular → incrementar atómicamente
`contadores.venta_folio` mediante findAndModify/$inc → insertar venta → invocar
publicador. Todo ocurre dentro de TransactionTemplate. Los conflictos transitorios
de MongoDB reintentan la transacción completa (hasta diez intentos con espera);
no se reintenta un commit cuyo resultado sea desconocido.

El publicador envía VentaCreada después del commit; una reversión no publica.
Un fallo al enviar se registra con ventaId y folio sin convertir la respuesta
en 500. Sin outbox existe una ventana de pérdida entre commit y envío: la venta
puede permanecer PENDIENTE y requiere atención manual. No hay idempotencia HTTP
por reenvío del POST: ante una respuesta perdida, repetir puede crear otra venta.
Contrato de inicio: [VentaCreada](../docs/eventos/venta-creada.md).

Los campos persistidos siguen el Documento 04 (`clienteNombre`, `fechaCreacion`).
Los índices heredados del guion 02 usan `fecha`; al implementar la consulta por
fecha deberá alinearse ese índice con `fechaCreacion`. Este POST usa únicamente
el índice único de folio, que también se exige al arrancar.

## Cierre de la saga (4.4)

`StockEventoListener` consume `ventas.stock`: `StockReservado` confirma y
`StockRechazado` cancela. Resolución e inserción de `eventoId` comparten una
transacción MongoDB; `VentaConfirmada`/`VentaCancelada` se envían después del commit.

- Un evento ya registrado se descarta antes de buscar la venta.
- Una venta inexistente produce una excepción; Spring AMQP hace tres intentos
  y envía el mensaje a `ventas.stock.fallidos` al agotarlos.
- Una venta ya resuelta conserva estado, motivo y fecha. Se registra el nuevo
  `eventoId`, sin volver a publicar, incluso si la respuesta tardía es contraria.
- Dos respuestas concurrentes que leen PENDIENTE chocan al escribir la misma
  venta. Una transacción se revierte y el reintento ve la venta ya resuelta.
- `fechaResolucion` corresponde al momento de resolver en ms-ventas, con
  precisión de milisegundos para coincidir con BSON. No depende de la fecha del sobre.
- El nombre y la cantidad solicitada salen de las líneas congeladas. Los productos
  repetidos suman sus cantidades. No se consulta `productos_replica` ni se almacena stock.
- Por ahora el motivo comprensible omite «disponible»: inventario aún no lo envía.
  Ejemplo: `existencia insuficiente de Tinta para sello, solicitado 3`.

El evento de cierre lleva la venta completa para construir `ventas_proy`, sin
consultas HTTP entre servicios. La cancelación incluye el **código** de inventario
en `motivo`; la frase para el cajero queda en `ventas.motivoCancelacion`.
Contrato: [Eventos de cierre](../docs/eventos/venta-cierre.md).

**Limitación sin outbox:** si el proceso cae entre commit y envío, o falla RabbitMQ,
la venta queda resuelta y el evento registrado, pero reportes/notificaciones pueden
no recibir el cierre. Una reentrega de stock se descarta y no lo vuelve a publicar.
Hay que revisar los errores de publicación y reconciliar/reemitir manualmente;
esta actividad no implementa recuperación automática. Un fallo de envío posterior
al commit se registra sin intentar revertir una transacción que ya terminó.

Se mantienen los tres escuchadores independientes: productos/clientes actualizan
réplicas; stock aplica transiciones terminales y publica un resultado. Comparten
infraestructura transaccional y de transporte, pero una clase base ocultaría esas
decisiones diferentes. Se reevaluará si aparecen más consumidores con la misma lógica.

## Réplicas

Los consumidores de `ventas.productos` y `ventas.clientes` actualizan las
réplicas y registran `eventoId` en una misma transacción de MongoDB.

- Un identificador ya procesado no modifica la réplica.
- Un evento estrictamente anterior no modifica la réplica, pero se registra.
- Si cualquiera de las fechas es nula, o ambas son iguales, se aplica el evento.
- Los tres eventos de producto usan el mismo recorrido, incluida la baja con
  `activo=false`. Si `activo` está ausente o es nulo, el mensaje falla.
- Las excepciones salen del consumidor. La configuración de AMQP realiza hasta
  tres intentos en total y después envía el mensaje a la cola de fallidos.

Las réplicas no almacenan stock ni correo, teléfono o dirección del cliente.
ms-clientes todavía no publica bajas; los clientes eliminados pueden permanecer
en la réplica hasta que se acuerde ese contrato entre servicios.

## Consulta de una venta (5.2)

`GET /ventas/{id}` devuelve el detalle: estado, folio, cliente, líneas congeladas
con su subtotal, total, motivo y fechas. Es la única vía por la que el cliente
web conoce el desenlace de la saga, porque el registro responde 202 con la venta
en PENDIENTE y la resolución llega después por mensajería.

Tres decisiones que conviene no revertir sin leer esto:

- **El subtotal y el total se calculan en el servidor.** El Documento 04 prohíbe
  persistir el subtotal para no tener dos fuentes de verdad, pero eso no obliga a
  que lo calcule el navegador. JavaScript no tiene decimales exactos: `41.15 * 3`
  da `123.44999999999999`. El cliente muestra lo que llega y nunca recalcula.
- **`motivo` llega redactado, no como código.** `StockEventoListener` traduce
  `EXISTENCIA_INSUFICIENTE` y los otros dos códigos a una frase de caja antes de
  persistirla. El cliente no traduce nada.
- **`productoIdRechazado` acompaña al motivo en CANCELADA.** La frase nombra el
  producto en texto; el identificador permite a la caja señalar la línea exacta
  del carrito (actividad 5.5, paso 9) sin analizar la cadena. Las ventas
  canceladas antes de esa actividad lo traen en `null`.
- **La respuesta lleva `Cache-Control: no-store`.** La caja consulta este recurso
  repetidamente mientras la venta está PENDIENTE; sin la cabecera, una respuesta
  cacheada dejaría el sondeo sin ver el cambio a CONFIRMADA.

Los campos que no aplican viajan en `null` en lugar de omitirse, para que la forma
del objeto sea siempre la misma: `motivo` salvo en CANCELADA, `fechaResolucion`
mientras está PENDIENTE y los dos de cliente en una venta sin cliente.

### Carencia conocida: la venta no guarda quién la registró

Cualquier ADMIN o VENDEDOR puede consultar cualquier venta. No es una decisión de
permisos, es una consecuencia del modelo: `Venta` no tiene campo de autor. El
registro recibe `X-User-Id` del Gateway y no lo persiste, de modo que hoy no
existe rastro de qué vendedor cobró cada venta.

Esto no afecta a la 5.2 —nada que consultar se podría filtrar por un dato que no
está—, pero sí limita a ms-reportes: cualquier corte por vendedor es imposible
mientras el campo no exista, y agregarlo después no reconstruye el histórico.
Decidirlo antes de la 4.6.

## Pruebas

Desde este directorio, con Java 21 y Maven:

```powershell
mvn test
```

Las pruebas unitarias no necesitan Docker. Para incluir las pruebas de
transacción, iniciar MongoDB con el Compose de `../infra` y definir
`MONGO_VENTAS_TEST_URI` con la URI autenticada de una base de desarrollo
aprovisionada mediante los guiones de `infra/mongo-init`. Al conectar desde
Windows, la URI usa `localhost:27017`, `replicaSet=rs0`,
`authSource=ventas_db` y `directConnection=true`.

```powershell
# Definir MONGO_VENTAS_TEST_URI en el entorno sin versionar la credencial.
mvn test
```

Sin esa variable, `ReplicasTransaccionTest`, `VentaRegistroIntegracionTest` y
`StockResolucionIntegracionTest` se
omiten explícitamente. Con ella se comprueban:

- Rollback de creación/actualización de ambas réplicas ante conflicto real del
  índice único de eventos, idempotencia y ausencia de campos adicionales.
- Ocho registros simultáneos con folios distintos y consecutivos.
- Venta visible desde una consulta sin sesión antes de enviar a RabbitMQ.
- Rollback de venta y contador después de registrar el callback afterCommit.
- Snapshots históricos, Decimal128, ObjectId e Int64 en BSON; ausencia de subtotal.
- Broker caído después del commit: la venta permanece PENDIENTE.
- Cierre confirmado/cancelado visible fuera de la transacción antes de publicar.
- Conflicto real de índice único al registrar stock: se revierte la resolución
  y no se ejecuta el envío pendiente de afterCommit, en ambos desenlaces.
- Respuestas contrarias concurrentes: un solo cierre publicado; la reentrega
  registra la respuesta restante sin modificar la venta.
- Broker caído después del cierre: la venta sigue resuelta y la reentrega se descarta.

Cada prueba elimina únicamente sus documentos; no borra colecciones ni reinicia
el contador compartido. Los folios consumidos por las pruebas quedan avanzados.
El transporte Rabbit se simula en las pruebas de transacción; el adaptador real
sí ejecuta sus callbacks. Las pruebas unitarias cubren DTO/HTTP, roles, réplicas,
importes manipulados, reintentos y el contrato del publicador.

La configuración compartida y el aviso sobre `MONGO_VENTAS_PASSWORD` están en
[`../infra/README.md`](../infra/README.md).

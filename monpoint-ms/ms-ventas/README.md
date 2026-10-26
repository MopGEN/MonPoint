# ms-ventas — réplica local y registro asíncrono (3.6 y 4.1)

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
La resolución de StockReservado/StockRechazado pertenece a 4.4 y no se implementa
en este paso. Contrato: [VentaCreada](../docs/eventos/venta-creada.md).

Los campos persistidos siguen el Documento 04 (`clienteNombre`, `fechaCreacion`).
Los índices heredados del guion 02 usan `fecha`; al implementar la consulta por
fecha deberá alinearse ese índice con `fechaCreacion`. Este POST usa únicamente
el índice único de folio, que también se exige al arrancar.

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

Sin esa variable, `ReplicasTransaccionTest` y `VentaRegistroIntegracionTest` se
omiten explícitamente. Con ella se comprueban:

- Rollback de creación/actualización de ambas réplicas ante conflicto real del
  índice único de eventos, idempotencia y ausencia de campos adicionales.
- Ocho registros simultáneos con folios distintos y consecutivos.
- Venta visible desde una consulta sin sesión antes de enviar a RabbitMQ.
- Rollback de venta y contador después de registrar el callback afterCommit.
- Snapshots históricos, Decimal128, ObjectId e Int64 en BSON; ausencia de subtotal.
- Broker caído después del commit: la venta permanece PENDIENTE.

Cada prueba elimina únicamente sus documentos; no borra colecciones ni reinicia
el contador compartido. Los folios consumidos por las pruebas quedan avanzados.
El transporte Rabbit se simula en las pruebas de transacción; el adaptador real
sí ejecuta sus callbacks. Las pruebas unitarias cubren DTO/HTTP, roles, réplicas,
importes manipulados, reintentos y el contrato del publicador.

La configuración compartida y el aviso sobre `MONGO_VENTAS_PASSWORD` están en
[`../infra/README.md`](../infra/README.md).

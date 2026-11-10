# ms-notificaciones — 5.4

Consume `StockBajo`, guarda avisos en su propia base y permite que ADMIN los
consulte y marque como leídos. No publica eventos ni llama a otros servicios
de dominio por HTTP. No añade una pantalla al cliente.

## Contrato y decisiones

El [contrato StockBajo](../docs/eventos/stock-bajo.md) conserva su forma:
`datos.nombre` se guarda como `productoNombre`. `productoId` se almacena como
ObjectId; los identificadores de la API son cadenas. `tipo` es `STOCK_BAJO`.
`fechaCreacion` es el instante local de creación del aviso, no el del producto;
las fechas se guardan en UTC con precisión de milisegundos en MongoDB.

**Leer cierra el aviso.** El PUT cambia atómicamente `leida=true`, `activa=false`
y `fechaLectura`. Repetirlo devuelve 204 sin cambiar la fecha. Si después llega
otro evento distinto del mismo producto, crea otro aviso no leído. Se conserva
el historial de avisos inactivos. No significa que se haya repuesto existencia:
no existe un evento de reposición en este contrato.

**Dos garantías distintas:**

| Garantía | Identidad e implementación |
| --- | --- |
| Idempotencia de entrega | Índice único `eventos_procesados.eventoId`; marcador y aviso en la misma transacción MongoDB |
| Un aviso activo por producto | Índice único parcial `productoId`, con `partialFilterExpression: {activa: true}` |

También se crea el índice compuesto **no único** `(productoId, activa)` que
pide la actividad, y un índice `leida` para el filtro. Hacer único el compuesto
impediría conservar dos avisos inactivos del mismo producto.

El consumidor consulta el marcador como optimización. Después lo inserta y
hace un `findAndModify` con upsert y filtro `{productoId, activa: true}` dentro
de la misma transacción. No realiza una lectura seguida de `save`. Los índices
son la garantía ante concurrencia. Un evento distinto actualiza existencia,
nombre y mensaje de la activa; conserva su id y fecha de creación.

Los conflictos de escritura, clave duplicada y resultado de commit incierto se
reintentan en una **nueva transacción**, hasta 20 intentos con pausas de 5–25 ms.
Una repetición después de un commit exitoso encuentra el marcador y no vuelve
a aplicar cambios. Si se agotan esos intentos, el error llega al contenedor AMQP.
Los índices se materializan durante la creación del contexto, antes de iniciar
los escuchadores, también con volúmenes existentes. Un índice incompatible o
duplicados preexistentes hacen fallar el arranque; no se elimina información.

**Orden de eventos:** se acepta el último evento procesado. Se valida `fecha`
del sobre, pero no se utiliza como versión causal: representa la construcción
del mensaje y no garantiza orden. Un evento antiguo entregado tarde puede
mostrar una existencia anterior o, si es un evento distinto que aún no se había
procesado, abrir un aviso después de leer otro. La alerta no es una consulta de
existencia actual. Resolver ese límite exigiría ampliar el contrato del productor.

## RabbitMQ

| Elemento | Valor |
| --- | --- |
| Exchange topic durable | `inventario.eventos` |
| Binding exacto | `stock.bajo` |
| Cola durable propia | `notificaciones.stock-bajo` |
| Cola durable de fallidos | `notificaciones.stock-bajo.fallidos` |
| Reintentos AMQP | `max-retries=2`: dos reintentos más la entrega inicial, intervalo 1 s |
| Rechazo final | `default-requeue-rejected=false`; DLX por defecto y routing key de fallidos |

Spring Boot **4.1.1** usa `max-retries`; en sus metadatos, `max-attempts` está
retirado desde 4.0.0. La suite cuenta tres invocaciones del interceptor
configurado por Boot y verifica el rechazo sin reencolado. Esto corrige la
suposición previa del catálogo basada en versiones anteriores; no cambia
la configuración de otros servicios en esta rama.

Se rechazan sobres incompletos, tipo/versión/origen ajenos, carga inválida y
`message_id` discordante con `eventoId`. Si falta `message_id`, la identidad
sigue siendo el `eventoId` del JSON. Para reentregar, conservar ambos valores.
No se reconstruyen avisos anteriores a la declaración de la cola: el exchange
no guarda historial. Tampoco se recupera un evento perdido entre commit y
publicación en inventario; su publicador no tiene outbox.

## API, siempre mediante el Gateway

| Método y ruta | Resultado |
| --- | --- |
| `GET /notificaciones` | 200, array con todos los avisos, sin paginación |
| `GET /notificaciones?leida=false` | 200, solo no leídos |
| `GET /notificaciones?leida=true` | 200, solo leídos |
| `PUT /notificaciones/{id}/leida` | 204 sin cuerpo; 404 si no existe o el id no es válido |

No se promete un orden del listado. Un filtro ilegible responde 400.
Ambas rutas exigen ADMIN: el Gateway valida el JWT y sustituye las cabeceras
`X-User-Id` y `X-User-Role`; el servicio comprueba el rol. Sin identidad responde
401 y con VENDEDOR, 403. Los errores usan ProblemDetail y `requestId`.
El contenedor **no publica el puerto 8086**; las cabeceras de identidad solo se
confían dentro de la red privada de Compose. No exponer directamente ese puerto.

## Arranque y configuración

Desde `monpoint-ms/infra`:

```powershell
docker compose up -d --build ms-notificaciones
docker compose ps ms-notificaciones
```

Requiere MongoDB replica set, RabbitMQ y Eureka. Compose suministra `MONGO_URI`,
`RABBITMQ_HOST`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD`, `EUREKA_URL` y
`SERVER_PORT`. La base y su usuario ya se aprovisionan en
`infra/mongo-init/01-crear-bases-y-usuarios.js`. `/actuator/health` sirve al
healthcheck y no publica detalles internos.

## Pruebas

Requieren JDK 21. Desde `monpoint-ms/ms-notificaciones`, en Windows:

```powershell
$env:MONGO_NOTIFICACIONES_TEST_URI = 'mongodb://notificaciones_user:notificaciones_pass@localhost:27017/notificaciones_db?replicaSet=rs0&authSource=notificaciones_db&directConnection=true'
.\mvnw.cmd test
if ($LASTEXITCODE -ne 0) { throw 'Fallaron las pruebas de notificaciones' }
```

Las credenciales del ejemplo son las de desarrollo del guion de infraestructura.
Para otra instalación, usar su URI sin publicarla. En Linux se usa la misma
variable y `./mvnw test`. Sin la variable, las pruebas con MongoDB se omiten:
**esa ejecución no acredita concurrencia ni atomicidad**.

La suite cubre la carga real, campos obligatorios, índices efectivos, doce
eventos concurrentes del mismo producto, doce reentregas concurrentes, lectura
simultánea con actualización, rollback tras un fallo inducido, historial,
fechas estables, eventos fuera de orden y respuestas/filtros de la API.
Las pruebas con MongoDB usan ids nuevos y eliminan solo sus propios documentos;
no vacían colecciones. Desactivan Eureka y los escuchadores AMQP para probar la
persistencia y la API sin consumir mensajes del entorno.

## Recorrido de los cinco criterios

Con el servicio sano y la cola declarada, crear por API un producto de prueba
con existencia 10 y umbral 5. Vender 5 unidades desde la caja y esperar la
confirmación. Consultar `GET /notificaciones?leida=false` con token ADMIN:
debe aparecer el producto, existencia 5 y fecha. Otra venta de una unidad debe
actualizar el mismo aviso a 4. Marcarlo leído debe dar 204; un id inexistente,
404. Verificar GET y PUT con VENDEDOR (403) y sin token (401).

Para comprobar la reentrega, enlazar antes una cola temporal de captura a
`inventario.eventos` / `stock.bajo`, copiar el mensaje original completo y
republicarlo conservando payload y `message_id`. La notificación y su fecha de
lectura no deben cambiar, ni debe crearse otra. El log muestra
`STOCK_BAJO_DESCARTADO`. Borrar la cola temporal al terminar. Un nuevo evento
después de la lectura sí debe crear un nuevo aviso activo.

La [evidencia de aceptación](../../docs/aceptacion/5.4-2026-11-10/RESULTADOS.md)
delimita qué se verificó sobre el conjunto real.

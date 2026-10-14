# Modelo de datos — MonPoint MS

Transcripción del **Documento 04 — Modelo de Datos, v1.1** (`docs/planeacion/MonPoint_MS_04_Modelo_de_Datos.pdf`) en un
formato que se puede leer, buscar y pasar como contexto a una IA (H-070). **La fuente de verdad sigue siendo el PDF**:
si algo difiere, manda el PDF. El esquema está **cerrado**: agregar o cambiar un campo, una colección o un índice lo
decide el Product Owner, porque afecta a todo consumidor de esa colección, directo o por evento.

> **Para IAs:** usa exactamente estos nombres de colección y de campo (camelCase) y estos tipos. No agregues campos,
> colecciones ni índices que no estén aquí. Revisa también el [alcance recortado](#alcance-recortado-doc-01-74):
> parte de este modelo no se construye en este periodo.

---

## Reglas de diseño (Doc 04 §2)

| Regla | Qué significa al programar |
|---|---|
| Agregado natural | La venta y sus líneas son **un solo documento** (`lineas` embebido). No hay colección de líneas |
| Sin referencias vivas entre servicios | Una relación con otro servicio es un **ObjectId de solo lectura**. Nunca `$ref` ni consultas cruzadas |
| Copia histórica | La línea de venta **congela** nombre y precio del producto, y la venta congela el nombre del cliente |
| Réplica y proyección | ms-ventas mantiene réplicas y ms-reportes proyecciones, **alimentadas solo por eventos** |
| Idempotencia por consumidor | Todo servicio que consume eventos tiene `eventos_procesados` con índice único en `eventoId` (RNF-16) |
| Decimal128 obligatorio | Todo importe es `Decimal128`. **Nunca** texto ni coma flotante (RNF-08) |
| Índices explícitos | Cada índice de este documento se declara en código (`@Indexed`, `@CompoundIndex`) |
| Identificadores | `ObjectId` en todo documento. La venta tiene además un **folio** numérico secuencial |
| Fechas | `Date` de BSON en **UTC**; en HTTP y en eventos se serializa como **ISO-8601** |
| Sin facturación | No existe colección de facturas |

## Tipos BSON ➔ Java (Spring Data MongoDB 5.1)

Verificado en este repositorio: así guarda Spring Data los campos de `ms-auth` en `auth_db`.

| Tipo en este documento | Tipo Java | Cómo declararlo |
|---|---|---|
| `ObjectId` (el `_id`) | `String` | `@Id private String id;` Spring lo guarda como ObjectId y lo expone como texto hexadecimal de 24 caracteres |
| `ObjectId` (referencia a otro servicio: `clienteId`, `productoId`) | `String` | `@Field(targetType = FieldType.OBJECT_ID)`, para que se guarde como ObjectId y no como texto |
| `_id` fijo de texto (`"venta_folio"`, `"config"`) | `String` | `@Id private String id;` con ese valor; como no es un ObjectId válido, se guarda como texto |
| `String` | `String` | — |
| `String` enumerado (`rol`, `estado`, `tipo`) | `enum` de Java | Se guarda el nombre de la constante (`"CONFIRMADA"`) |
| `Decimal128` | `BigDecimal` | **`@Field(targetType = FieldType.DECIMAL128)`**. Sin esto, Spring guarda `BigDecimal` como **texto** |
| `Int32` | `int` / `Integer` | `Integer` solo si el campo admite `null` |
| `Int64` | `long` / `Long` | — |
| `Boolean` | `boolean` / `Boolean` | — |
| `Date` | `Instant` | Siempre UTC. `@CreatedDate`/`@LastModifiedDate` necesitan `@EnableMongoAuditing` |
| `Array<Documento>` | `List<...>` de una clase o record embebido | Ej. `ventas.lineas` |
| `Documento` | Clase embebida, o `Map<String, Object>` para contenido libre (datos de eventos) | — |
| `X \| null` | El tipo envoltorio (`String`, `Integer`, `Instant`) | `null` cuando no aplica |

Imports: `org.springframework.data.mongodb.core.mapping.{Document, Field, FieldType}`, `org.springframework.data.annotation.{Id, CreatedDate, LastModifiedDate}` y `org.springframework.data.mongodb.core.index.{Indexed, CompoundIndex}`.

**Nombres:** el campo Java se llama exactamente como en estas tablas, en camelCase y sin `@Field(name = ...)`, y la
colección se declara con `@Document(collection = "<nombre exacto>")`.

---

## ms-auth — `auth_db` (Doc 04 §4)

### `usuarios`
| Campo | Tipo | Notas |
|---|---|---|
| `_id` | ObjectId | Generado por MongoDB |
| `correo` | String | Único. RF-1.7 |
| `contrasenaHash` | String | BCrypt, factor 12. Nunca se devuelve en una respuesta |
| `nombre` | String | — |
| `rol` | String | Enumerado: `ADMIN`, `VENDEDOR`. RF-1.5 |
| `fechaRegistro` | Date | — |
| `fechaActualizacion` | Date | — |

Índice: único sobre `correo`. Según el Doc 04, ningún otro servicio referencia a un usuario, así que **la baja es
eliminación física**.

> ⚠️ **Divergencia pendiente de decisión del PO.** Lo implementado en `ms-auth` guarda `password_hash`, `creado_en` y
> `actualizado_en` (snake_case), agrega `activo` y hace **baja lógica** (`PATCH /auth/usuarios/{id}/desactivar`).
> No afecta a otros servicios, porque nadie lee `auth_db`, pero no coincide con el documento cerrado.
> **Los demás servicios deben seguir el Doc 04 (camelCase), no a ms-auth en este punto.**

## ms-inventario — `inventario_db` (Doc 04 §5)

### `productos`
| Campo | Tipo | Notas |
|---|---|---|
| `_id` | ObjectId | Se propaga a `productos_replica` y `productos_proy` con el mismo valor |
| `nombre` | String | — |
| `descripcion` | String | Opcional |
| `precio` | Decimal128 | RNF-08 |
| `existencia` | Int32 | RF-2.4, RF-2.5 |
| `umbralStock` | Int32 | Configurable por producto; valor por omisión al crear. RF-2.6 |
| `activo` | Boolean | Baja lógica. RF-2.2 |
| `fechaRegistro` | Date | — |
| `fechaActualizacion` | Date | Viaja en cada evento de producto, para que los consumidores descarten eventos desordenados |

Índice: sobre `activo`. Además tiene `outbox` y `eventos_procesados` ([transversales](#colecciones-transversales-doc-04-10)).

## ms-ventas — `ventas_db` (Doc 04 §6)

### `ventas`
| Campo | Tipo | Notas |
|---|---|---|
| `_id` | ObjectId | Se propaga a `ventas_proy` con el mismo valor |
| `folio` | Int64 | Secuencial, por incremento atómico sobre `contadores`. RF-3.7 |
| `clienteId` | ObjectId \| null | Referencia de solo lectura a ms-clientes; trazabilidad, no integridad referencial |
| `clienteNombre` | String \| null | Copia del nombre al momento de la venta. RF-3.4 |
| `estado` | String | Enumerado: `PENDIENTE`, `CONFIRMADA`, `CANCELADA`. RF-3.6 |
| `motivoCancelacion` | String \| null | Solo si `estado` es `CANCELADA`. H-034 |
| `lineas` | Array\<Documento\> | Ver abajo |
| `total` | Decimal128 | Calculado en el servidor a partir de las líneas. RF-3.3 |
| `fechaCreacion` | Date | — |
| `fechaResolucion` | Date \| null | Cuando pasó a `CONFIRMADA` o `CANCELADA` |

### Línea (embebida en `ventas.lineas`)
| Campo | Tipo | Notas |
|---|---|---|
| `productoId` | ObjectId | Referencia de solo lectura, para trazabilidad |
| `nombreProducto` | String | Copia. RF-3.4 |
| `precioUnitario` | Decimal128 | Copia. RNF-08 |
| `cantidad` | Int32 | — |

El subtotal de línea y el total se calculan con `cantidad × precioUnitario`. **No se guarda un subtotal**, para no
tener dos fuentes de verdad.

### `productos_replica`
| Campo | Tipo | Notas |
|---|---|---|
| `_id` | ObjectId | El mismo `_id` del producto en ms-inventario; se fija en el *upsert*, no se autogenera |
| `nombre` | String | — |
| `precio` | Decimal128 | — |
| `activo` | Boolean | — |
| `fechaActualizacion` | Date | Un evento con fecha anterior a la almacenada se descarta |

**No guarda existencia**: la autoridad sobre el stock es solo ms-inventario.

### `clientes_replica`
| Campo | Tipo | Notas |
|---|---|---|
| `_id` | ObjectId | El mismo `_id` del cliente en ms-clientes |
| `nombre` | String | — |
| `fechaActualizacion` | Date | — |

### `contadores`
| Campo | Tipo | Notas |
|---|---|---|
| `_id` | String | Clave fija: `"venta_folio"` |
| `valor` | Int64 | Se incrementa de forma atómica (`findAndModify` con `$inc`); el valor resultante es el folio |

Índices: único sobre `folio`; compuesto `(fechaCreacion, estado)`; sobre `estado`. Además tiene `outbox` y `eventos_procesados`.

## ms-clientes — `clientes_db` (Doc 04 §7)

### `clientes`
| Campo | Tipo | Notas |
|---|---|---|
| `_id` | ObjectId | Se propaga a `clientes_replica` y `contrapartes_proy` |
| `nombre` | String | — |
| `correo` | String | Formato validado. RF-4.3. **No** exige unicidad |
| `telefono` | String | — |
| `direccion` | String | — |
| `fechaRegistro` | Date | Automática. RF-4.5 |
| `fechaActualizacion` | Date | — |

Índice: sobre `nombre`, para la búsqueda por nombre parcial (H-040).

### `proveedores` — ⛔ fuera del alcance recortado
| Campo | Tipo | Notas |
|---|---|---|
| `_id` | ObjectId | — |
| `nombre` | String | — |
| `ruc` | String | Único. RF-4.4 |
| `razonSocial` | String | — |
| `telefono` | String | — |
| `direccion` | String | — |
| `fechaRegistro` | Date | Automática |
| `fechaActualizacion` | Date | — |

## ms-reportes — `reportes_db` (Doc 04 §8)

Son proyecciones alimentadas por eventos, salvo `configuracion`, que es propia.

### `configuracion`
| Campo | Tipo | Notas |
|---|---|---|
| `_id` | String | Clave fija: `"config"`, para garantizar un único documento. RF-5.1 |
| `nombreEmpresa` | String | — |
| `rfc` | String | — |
| `direccion` | String | — |
| `telefono` | String | — |
| `fechaActualizacion` | Date | — |

### `ventas_proy`
| Campo | Tipo | Notas |
|---|---|---|
| `_id` | ObjectId | El mismo `_id` de la venta en ms-ventas |
| `folio` | Int64 | — |
| `clienteNombre` | String \| null | — |
| `estado` | String | `CONFIRMADA` o `CANCELADA`; una venta pendiente no tiene proyección |
| `lineas` | Array\<Documento\> | `productoId`, `nombreProducto`, `precioUnitario`, `cantidad` |
| `total` | Decimal128 | — |
| `fecha` | Date | Fecha de confirmación o cancelación |

Índice: compuesto `(fecha, estado)`.

### `productos_proy`
| Campo | Tipo | Notas |
|---|---|---|
| `_id` | ObjectId | El mismo `_id` del producto en ms-inventario |
| `nombre` | String | — |
| `precio` | Decimal128 | — |
| `activo` | Boolean | — |
| `fechaActualizacion` | Date | Descarta eventos anteriores al estado almacenado |

### `contrapartes_proy`
| Campo | Tipo | Notas |
|---|---|---|
| `_id` | ObjectId | El mismo `_id` de origen en ms-clientes |
| `tipo` | String | Enumerado: `CLIENTE`, `PROVEEDOR` |
| `nombre` | String | — |
| `correo` | String \| null | Solo `CLIENTE` |
| `ruc` | String \| null | Solo `PROVEEDOR` |
| `razonSocial` | String \| null | Solo `PROVEEDOR` |
| `telefono` | String | — |
| `direccion` | String | — |
| `fechaActualizacion` | Date | — |

Índice: sobre `tipo`. Además tiene `eventos_procesados`.

## ms-notificaciones — `notificaciones_db` (Doc 04 §9)

### `eventos` (bitácora de auditoría, RF-6.1)
| Campo | Tipo | Notas |
|---|---|---|
| `_id` | ObjectId | — |
| `eventoId` | String | Identificador del evento original |
| `tipo` | String | `StockBajo`, `VentaConfirmada` o `VentaCancelada` |
| `origen` | String | Servicio productor |
| `fecha` | Date | — |
| `datosResumen` | Documento | Copia del contenido del evento |

### `notificaciones`
| Campo | Tipo | Notas |
|---|---|---|
| `_id` | ObjectId | — |
| `tipo` | String | Por ahora, único valor: `STOCK_BAJO` |
| `productoId` | ObjectId \| null | — |
| `productoNombre` | String \| null | — |
| `existenciaResultante` | Int32 \| null | — |
| `mensaje` | String | Texto que ve el usuario |
| `activa` | Boolean | Deduplicación (abajo) |
| `leida` | Boolean | RF-6.3, RF-6.4 |
| `fechaCreacion` | Date | — |
| `fechaLectura` | Date \| null | — |

**Deduplicación (H-061):** antes de crear una alerta se busca una notificación con el mismo `productoId` y `activa = true`.
Si existe, se actualiza su `existenciaResultante` en lugar de crear otra. Índices: compuesto `(productoId, activa)` y
sobre `leida`. Además tiene `eventos_procesados`.

## Colecciones transversales (Doc 04 §10)

### `eventos_procesados` — en ms-inventario, ms-ventas, ms-reportes y ms-notificaciones
| Campo | Tipo | Notas |
|---|---|---|
| `_id` | ObjectId | — |
| `eventoId` | String | **Único**. Es la clave de idempotencia |
| `tipo` | String | — |
| `fechaProcesado` | Date | — |

Antes de aplicar un evento, el consumidor verifica si su `eventoId` ya está aquí. Si está, lo descarta sin efecto (RNF-16).

### `outbox` — en ms-ventas y ms-inventario · ⛔ fuera del alcance recortado
| Campo | Tipo | Notas |
|---|---|---|
| `_id` | ObjectId | — |
| `eventoId` | String | UUID; el mismo que lleva el evento publicado. Único |
| `tipo` | String | Ej. `VentaCreada`, `StockReservado` |
| `datos` | Documento | Contenido del evento, según el catálogo (H-008) |
| `estado` | String | `PENDIENTE`, `ENVIADO` |
| `fechaCreacion` | Date | — |
| `fechaEnvio` | Date \| null | — |

## Índices consolidados (Doc 04 §11)

| Servicio | Colección | Índice | Propósito |
|---|---|---|---|
| ms-auth | `usuarios` | único: `correo` | Correos irrepetibles. RF-1.7 |
| ms-inventario | `productos` | `activo` | Excluir inactivos del listado. RF-2.2 |
| ms-inventario | `eventos_procesados` | único: `eventoId` | Idempotencia. RNF-16 |
| ms-ventas | `ventas` | único: `folio` | Folio irrepetible. RF-3.7 |
| ms-ventas | `ventas` | `(fechaCreacion, estado)` | Listado filtrado. RF-3.9 |
| ms-ventas | `ventas` | `estado` | Ventas pendientes. RF-3.10 |
| ms-ventas | `eventos_procesados` | único: `eventoId` | Idempotencia |
| ms-clientes | `clientes` | `nombre` | Búsqueda parcial. H-040 |
| ms-reportes | `ventas_proy` | `(fecha, estado)` | Historial y agregación. RF-5.4, RF-5.7 |
| ms-reportes | `contrapartes_proy` | `tipo` | Separar clientes de proveedores |
| ms-reportes | `eventos_procesados` | único: `eventoId` | Idempotencia |
| ms-notificaciones | `notificaciones` | `(productoId, activa)` | Deduplicación. H-061 |
| ms-notificaciones | `notificaciones` | `leida` | Filtro de lectura. RF-6.3 |
| ms-notificaciones | `eventos_procesados` | único: `eventoId` | Idempotencia |

El Doc 04 también lista índices de `outbox` (en estado y único en `eventoId`) y de `proveedores` (único `ruc`; `nombre`
y `razonSocial`), que quedan fuera junto con esas colecciones.

## Alcance recortado (Doc 01 §7.4)

Al arrancar el desarrollo, el alcance bajó de 234 a 144 puntos. **Estas partes del modelo no se construyen en este
periodo**, salvo que el Product Owner las reactive:

| Fuera del alcance | Efecto en el modelo |
|---|---|
| Patrón outbox transaccional | No se implementa `outbox` ni su publicador periódico: los eventos se publican directo a RabbitMQ |
| Gestión de proveedores en ms-clientes | No se implementa `proveedores` |
| Paginación, filtros y ordenamiento en los listados | No se implementan aunque un criterio del backlog los mencione; ante la duda, se escala al PO |
| Comprobantes en PDF | Los reportes PDF no se construyen |
| Pantallas de administración del cliente web | Esas operaciones se hacen por API |

# ms-reportes

Configuración de la empresa, proyecciones, datos para gráficas y documentos PDF de MonPoint (H-050 a H-056; épica E-05).
Puerto interno **8085**, base `reportes_db`, usuario `reportes_user`.
Se llega a él **solo a través del Gateway** (`http://localhost:8080/reportes/...`); no publica puertos.

## Qué hace

- Guarda la configuración de la empresa: un único documento, que encabeza todos los PDF.
- Mantiene **sus propias copias** (proyecciones) de las ventas cerradas, del catálogo y de los clientes a partir de los
  eventos de RabbitMQ. **No llama por HTTP a ningún otro servicio**: todo reporte sale de esas copias.
- Entrega la serie de ventas agrupada por día, mes o año para las gráficas.
- Genera el comprobante de venta, el historial y los listados de productos y clientes en PDF (iText 9.1.0).

Patrón: capas `controller ➔ service ➔ repository`. La identidad viene del Gateway en `X-User-Id` / `X-User-Role`; este
servicio **no valida el JWT** y verifica el rol él mismo. Los errores salen en RFC 9457 `ProblemDetail` con `requestId`.

## Endpoints

Todos exigen token en el Gateway. Sin identidad: **401**; un rol que no sea `ADMIN` ni `VENDEDOR`: **400**.

| Método y ruta | Rol | Éxito | Errores |
|---|---|---|---|
| `GET /reportes/configuracion` | ADMIN, VENDEDOR | 200 + configuración | 404 si todavía no se registra |
| `PUT /reportes/configuracion` | **ADMIN** | 200; crea o actualiza el único documento | 400 (lista los campos en `campos`), 403 |
| `GET /reportes/ventas/agregado?periodo=DIA\|MES\|ANIO&desde=AAAA-MM-DD&hasta=AAAA-MM-DD` | **ADMIN** | 200 + serie | 400 (periodo, fecha, rango o más de 1000 puntos), 403 |
| `GET /reportes/ventas/{id}/ticket` | ADMIN, VENDEDOR | 200 PDF | 404 con `codigo: VENTA_NO_DISPONIBLE`, 409 si está cancelada |
| `GET /reportes/ventas/historial?desde=AAAA-MM-DD&hasta=AAAA-MM-DD` | **ADMIN** | 200 PDF | 400 (fecha, rango o más de 366 días), 403 |
| `GET /reportes/productos/listado` | **ADMIN** | 200 PDF, solo productos activos | 403 |
| `GET /reportes/clientes/listado` | **ADMIN** | 200 PDF | 403 |

Cuerpo de la configuración:
`{"nombreEmpresa":"Papelería Monpoint","rfc":"MON850101AB1","direccion":"Calle 1","telefono":"3312345678"}`.
`nombreEmpresa` y `rfc` son obligatorios; el RFC se guarda en mayúsculas. Una dirección o un teléfono en blanco se
guardan como `null`.

### Fechas y zona horaria

`desde` y `hasta` son **días completos e inclusivos** en la zona del negocio (`monpoint.reportes.zona-horaria`,
`America/Mexico_City`). Internamente se consulta `[desde 00:00, hasta+1 00:00)`: una venta de las 23:30 del día 14 cuenta
en el 14 aunque en UTC ya sea el 15. La fecha de una venta es la de su **confirmación** (`fechaResolucion`).

### Serie para gráficas

```json
{"periodo":"DIA","desde":"2026-11-07","hasta":"2026-11-08","zonaHoraria":"America/Mexico_City","total":71.00,
 "puntos":[{"inicio":"2026-11-07","total":0},{"inicio":"2026-11-08","total":71.00}]}
```

Un punto por periodo, en orden y **sin huecos**: los periodos sin ventas valen 0. Solo suman las ventas CONFIRMADAS.
La suma la hace Mongo sobre `Decimal128`, así que coincide al centavo con la suma de los totales. Con `MES` o `ANIO`,
`inicio` es el día 1 del periodo aunque el rango empiece después; el primer y el último punto pueden cubrir solo una
parte de su periodo.

### PDF

- `Content-Type: application/pdf`, `Content-Disposition: inline` (el navegador lo abre en su visor) y
  `Cache-Control: no-store`. Nombres: `ticket-folio-42.pdf`, `historial-ventas-2026-11-01-a-2026-11-30.pdf`,
  `listado-productos-2026-11-08.pdf`.
- Encabezado con los datos de la empresa; si aún no se configuran, dice **«Empresa sin configurar»** y el PDF se
  genera igual. Todos llevan la fecha de generación en la zona del negocio.
- Los importes **no se redondean**: mínimo dos decimales y, si el valor trae más, todos los significativos. El ticket
  imprime el total calculado por ms-ventas, no una suma rehecha aquí.
- El ticket muestra el folio, la fecha, el cliente («Público en general» si la venta no tiene) y las líneas con el
  nombre y el precio **congelados** al vender.
- El historial lista solo las ventas CONFIRMADAS, con el número de ventas y el total acumulado; un periodo vacío
  genera un documento que lo indica.
- Los listados van en orden alfabético en español («Ábaco» antes que «Barra») y con el total de registros.
- Fuente Helvetica estándar (WinAnsi): cubre acentos, ñ y ¿¡. Un carácter fuera de ese juego, como un emoji, no se
  dibuja, pero el documento se genera.

**Ticket no disponible (RF-5.8).** Si la venta no está en la proyección (sigue pendiente, su evento aún no llega o el id
no existe) responde 404 con `"codigo": "VENTA_NO_DISPONIBLE"`: el cliente puede reintentar en unos segundos. Desde aquí
no se distingue una venta pendiente de un id inventado.

## Eventos que consume

Cada cola tiene su `.fallidos`. Spring AMQP reintenta 2 veces con 1 s de espera (`retry.max-retries=2`; en Boot 4,
`max-attempts` ya no tiene efecto) y después envía el mensaje a la cola de fallidos.

| Cola | Exchange | Routing keys | Proyección | Contrato |
|---|---|---|---|---|
| `reportes.ventas` | `ventas.eventos` | `venta.confirmada`, `venta.cancelada` | `ventas_proy` | [venta-cierre](../docs/eventos/venta-cierre.md) |
| `reportes.productos` | `inventario.eventos` | `producto.*` | `productos_proy` | [producto](../docs/eventos/producto.md) |
| `reportes.clientes` | `clientes.eventos` | `cliente.*` | `contrapartes_proy` (`tipo = CLIENTE`) | [cliente](../docs/eventos/cliente.md) |

- **Idempotencia:** cada listener aplica el evento y registra su `eventoId` en `eventos_procesados` (índice único) en
  **una sola transacción**. Una reentrega se descarta. Si dos entregas chocan, el índice rechaza la segunda y la
  transacción deshace también la proyección.
- **Ventas:** se enlazan dos routing keys exactas y no `venta.*`, que también traería `venta.creada`: una venta pendiente
  no tiene proyección. Las **canceladas también se guardan**, para que su ticket responda 409 y no 404.
- **Productos y clientes:** un evento con `fechaActualizacion` anterior a la guardada se descarta, pero se registra como
  procesado. Una fecha ausente no se toma como antigua.
- Un evento con datos obligatorios faltantes o con un `tipo` desconocido falla y termina en `.fallidos` para revisarlo;
  nunca se guarda a medias.

## Modelo de datos (`reportes_db`, Doc 04 §8)

`configuracion` (`_id` fijo `"config"`), `ventas_proy` (índice `(fecha, estado)`), `productos_proy`, `contrapartes_proy`
(índice `tipo`) y `eventos_procesados` (único `eventoId`). Importes en `Decimal128`; el `_id` de cada proyección es el
mismo `ObjectId` que en su servicio de origen. Los índices se crean al arrancar (`auto-index-creation=true`). Detalle:
[modelo de datos](../docs/modelo-datos/README.md).

## Variables de entorno

| Variable | Por omisión (desde el IDE) |
|---|---|
| `SERVER_PORT` | `8085` |
| `MONGO_URI` | `mongodb://reportes_user:reportes_pass@localhost:27017/reportes_db?replicaSet=rs0&authSource=reportes_db&directConnection=true` |
| `EUREKA_URL` | `http://localhost:8761/eureka/` |
| `RABBITMQ_HOST` / `RABBITMQ_USER` / `RABBITMQ_PASSWORD` | `localhost` / `monpoint` / marcador de `infra/.env.example` |

La zona horaria va en `application.properties` (`monpoint.reportes.zona-horaria`), no en una variable.

## Cómo correrlo

```bash
cd monpoint-ms/infra && docker compose up -d mongodb rabbitmq eureka-server   # solo la infraestructura
cd ../ms-reportes && ./mvnw spring-boot:run                                   # con los valores por omisión
```

Para usar tu `.env` desde la terminal: `(set -a; source monpoint-ms/infra/.env; set +a; cd monpoint-ms/ms-reportes && ./mvnw spring-boot:run)`.

Sistema completo: `cd monpoint-ms/infra && docker compose up -d --build`. Para probar por el Gateway:
`POST /auth/login` ➔ token ➔ `Authorization: Bearer <token>` en `http://localhost:8080/reportes/...`.

Las colas se crean la primera vez que arranca el servicio. Para revisarlas: `http://localhost:15672` ➔ *Queues*.

## Pruebas

```bash
cd monpoint-ms/ms-reportes && ./mvnw test
```

| Clase | Qué comprueba | ¿Necesita MongoDB? |
|---|---|---|
| `ConfiguracionControllerTest`, `ReporteVentasControllerTest`, `DocumentoControllerTest` | Cada endpoint: códigos 200/400/401/403/404/409, cabeceras del PDF, `codigo` del 404 | No |
| `ConfiguracionServiceTest`, `AgregacionVentasServiceTest`, `DocumentoServiceTest` | Reglas de rol, rangos, conversión de zona, relleno con ceros, orden alfabético | No |
| `VentaCierreListenerTest`, `ProductoEventoListenerTest`, `ClienteEventoListenerTest` | Evento aplicado, duplicado, antiguo, datos faltantes, tipo desconocido | No |
| `PdfGeneradorTest` | El texto extraído de cada PDF: encabezado, folio, total exacto, documento vacío, varias páginas | No |
| `ProyeccionesIntegracionTest`, `AgregacionVentasIntegracionTest`, `DocumentosIntegracionTest`, `ConfiguracionRepositoryTest`, `MsReportesApplicationTests` | `Decimal128` real, índices, choque en el índice único, `$dateTrunc` con zona, consultas de los PDF | **Sí** (`docker compose up -d mongodb`) |

Las pruebas de integración usan ids nuevos (y ventas del año 2000) y al final borran **solo** sus documentos.
Los listeners de RabbitMQ no arrancan en las pruebas. `src/test/resources/spring.properties` fija
`spring.test.context.cache.pause=never`: sin eso, Spring Framework 7 reinicia los contextos cacheados y arranca los
listeners aunque estén desactivados.

## Limitaciones conocidas

| Limitación | Por qué | Efecto |
|---|---|---|
| Un cliente borrado sigue en el listado | ms-clientes hace baja física y no publica un evento de baja | Hasta que exista ese evento, el listado de clientes puede mostrar clientes eliminados |
| Lo publicado antes de que existan las colas se pierde | Las colas las declara este servicio al arrancar; sin cola, RabbitMQ descarta el mensaje | En una base nueva, levantar ms-reportes antes de registrar ventas, productos o clientes |
| No hay reporte por vendedor | El evento de venta no trae quién la registró | Cualquier VENDEDOR puede pedir el ticket de cualquier venta |
| Proveedores sin proyección | La gestión de proveedores quedó fuera del alcance (Doc 01 §7.4) y nadie publica sus eventos | `contrapartes_proy` solo tiene `tipo = CLIENTE` |
| El listado de productos no muestra existencias | Los eventos de producto no la traen; la autoridad del stock es ms-inventario | Solo nombre y precio |
| Contraseñas de desarrollo en archivos versionados | `reportes_pass` está en `infra/mongo-init` y en los valores por omisión, igual que en los demás servicios | Válido solo para desarrollo; en otro entorno se pasa `MONGO_URI` por variable |
| 503 unos segundos tras reiniciar el contenedor | Con `eureka.client.healthcheck.enabled=true`, la instancia pasa UP → DOWN → UP al registrarse, y el Gateway guarda en caché el registro unos 30 s | Pasa en todos los servicios de dominio; se resuelve solo |

**Alcance:** el Doc 01 §7.4 había recortado los comprobantes en PDF. Se construyeron porque H-051 a H-053 siguen en el
backlog del mapa de construcción y el Doc 02 los exige (RF-5.3 a RF-5.6); conviene confirmarlo con el Product Owner.
Licencia: iText 9 es AGPL, compatible con un repositorio público; si el código dejara de ser abierto, haría falta una
licencia comercial u otra biblioteca.

## Estructura

```
com.monpoint.reportes
├── audit/        RequestIdFilter (X-Request-ID ➔ logs y errores)
├── config/       MongoConfig (transacciones), RabbitConfig (colas y fallidos), SecurityConfig, TiempoConfig (Clock)
├── controller/   ConfiguracionController, ReporteVentasController, DocumentoController, GlobalExceptionHandler
├── dto/          request/ConfiguracionRequest · response/ConfiguracionResponse, AgregadoVentasResponse, DocumentoPdf
├── event/        ReportesEventos (nombres de la topología) · entrada/VentaCierreEvento, ProductoEvento, ClienteEvento
├── exception/    AccesoDenegado, RecursoNoEncontrado, RangoInvalido, VentaNoDisponible, VentaCancelada
├── listener/     VentaCierreListener, ProductoEventoListener, ClienteEventoListener
├── model/        Configuracion, VentaProyeccion + LineaVenta, ProductoProyeccion, ContraparteProyeccion,
│                 EventoProcesado, TotalPeriodo, enums (EstadoVenta, Periodo, Rol, TipoContraparte)
├── pdf/          PdfGenerador (solo formato; no consulta datos ni decide permisos)
├── repository/   un repositorio por colección; la agregación y el historial usan @Aggregation / @Query
├── security/     UsuarioActual
└── service/      ConfiguracionService, AgregacionVentasService, DocumentoService
```

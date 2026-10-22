# ms-inventario

Productos y control de existencias de MonPoint (H-020 a H-024; épica E-02). Puerto interno **8082**, base
`inventario_db`, usuario `inventario_user`. Se llega a él **solo a través del Gateway**
(`http://localhost:8080/inventario/...`); no publica puertos.

## Qué hace

1. **API de productos:** alta, consulta, modificación y baja lógica (H-020, H-021).
2. **Publica** `ProductoCreado`, `ProductoActualizado` y `ProductoDadoDeBaja` (H-022).
3. **Consume `VentaCreada`** y reserva el stock de **todas** las líneas en una sola transacción: todas o ninguna
   (H-023). Responde `StockReservado` o `StockRechazado`, y `StockBajo` si una existencia queda igual o bajo su
   umbral (H-024).

Patrón: capas `controller ➔ service ➔ repository` (como ms-auth). La identidad viene del Gateway en `X-User-Id` /
`X-User-Role`; este servicio **no valida el JWT** y verifica el rol él mismo (RNF-07). Errores en RFC 9457
`ProblemDetail` con `requestId`. Ningún servicio le llama por HTTP ni él a otros.

## Endpoints

| Método y ruta | Rol | Éxito | Errores |
|---|---|---|---|
| `POST /inventario/productos` | **ADMIN** | 201 + producto con su `id` | 400 (precio/existencia negativos, nombre vacío; lista **todos** los campos en `campos`), 401, 403 |
| `GET /inventario/productos` | ADMIN, VENDEDOR | 200 + solo productos **activos**, con `existencia` | 401 |
| `GET /inventario/productos/{id}` | ADMIN, VENDEDOR | 200 (también si está inactivo, con su `activo`) | 404, 401 |
| `PUT /inventario/productos/{id}` | **ADMIN** | 200 | 400, 403, 404, 401 |
| `DELETE /inventario/productos/{id}` | **ADMIN** | 204 (baja lógica: `activo=false`, el documento permanece) | 403, 404, 401 |

Cuerpo de alta y modificación: `{"nombre":"Aceite 1L","descripcion":"opcional","precio":45.50,"existencia":10,"umbralStock":5}`.
`descripcion` y `umbralStock` son opcionales (sin umbral se usa el valor por omisión). El precio viaja como decimal
exacto y se guarda como `Decimal128`.

## Reserva de existencias (H-023, H-024)

```
VentaCreada ─► cola inventario.venta-creada ─► VentaCreadaListener ─► ReservaStockService
   1. ¿eventoId en eventos_procesados?  sí ➔ se descarta (idempotente)
   2. UNA transacción de MongoDB:
        · registra el eventoId
        · por cada producto: $inc de existencia -cantidad SOLO SI activo y existencia >= cantidad (atómico)
        · si alguno falla ➔ se revierte todo (ninguna línea queda descontada)
   3. después de confirmar: StockReservado (+ StockBajo por cada producto en o bajo su umbral)
      o, si se revirtió: StockRechazado con producto y motivo
```

La seguridad ante concurrencia viene del descuento condicional atómico por documento; si dos transacciones chocan
sobre el mismo producto, MongoDB aborta una con un error transitorio y el reintento del listener la repite.

## ⚠️ Lo que la documentación no define (confirmar con el Product Owner)

| Tema | Lo que se hizo |
|---|---|
| Catálogo de eventos (H-008, `docs/eventos/` vacío) | **Propuesta provisional** concentrada en `event/InventarioEventos.java`, `event/impl/RabbitInventarioEventPublisher.java`, `event/VentaCreadaEvento.java` y `config/RabbitConfig.java`: routing keys (`producto.creado`, `stock.reservado`, `venta.creada`…), nombres de cola, `version="1"` y los nombres de los campos de `datos`. De H-022/H-023/H-024 salen los campos mínimos; `ventaId` se agregó porque ms-ventas lo necesita para correlacionar |
| Formato de `VentaCreada` | Se asume `datos: {ventaId, lineas:[{productoId, cantidad}]}`. **Alejandro (ms-ventas) debe confirmarlo** |
| Motivos de `StockRechazado` | `EXISTENCIA_INSUFICIENTE`, `PRODUCTO_INACTIVO`, `PRODUCTO_NO_ENCONTRADO` |
| Umbral por omisión | 5 (`Producto.UMBRAL_POR_OMISION`) |
| Reintentos del consumidor | 3 intentos, 1 s; después a `inventario.venta-creada.fallidos` (`application.properties`) |
| Verbo de la baja | `DELETE`; no se puede reactivar un producto (no hay historia que lo pida) |
| Modificar un producto de baja | Se permite (no se inventó una regla) |
| Paginación / filtros | **No** (Doc 01 §7.4 los recortó) |
| Outbox (H-025, RNF-18) | **No** (Doc 01 §7.4). Se publica con `RabbitTemplate` después de confirmar la transacción. Limitación: si el servicio cae justo entre el commit y la publicación, la respuesta a la venta se pierde (queda en el log) y la reentrega se descarta por idempotente |

## Variables de entorno

| Variable | Por omisión (desde el IDE) |
|---|---|
| `SERVER_PORT` | `8082` |
| `MONGO_URI` | `mongodb://inventario_user:inventario_pass@localhost:27017/inventario_db?replicaSet=rs0&authSource=inventario_db&directConnection=true` |
| `EUREKA_URL` | `http://localhost:8761/eureka/` |
| `RABBITMQ_HOST` / `RABBITMQ_USER` / `RABBITMQ_PASSWORD` | `localhost` / `monpoint` / marcador de `infra/.env.example` |

## Cómo correrlo

```bash
cd monpoint-ms/infra && docker compose up -d mongodb rabbitmq   # solo la infraestructura
cd ../ms-inventario && ./mvnw spring-boot:run
```

Sistema completo: `cd monpoint-ms/infra && docker compose up -d --build`. Para probar la reserva sin ms-ventas,
publica a mano en la consola de RabbitMQ (http://localhost:15672) al exchange `ventas.eventos` con routing key
`venta.creada`:

```json
{"eventoId":"11111111-1111-1111-1111-111111111111","tipo":"VentaCreada","version":"1","fecha":"2026-10-13T12:00:00Z","origen":"ms-ventas","datos":{"ventaId":"v-1","lineas":[{"productoId":"<id de un producto>","cantidad":2}]}}
```

## Pruebas

```bash
cd monpoint-ms/ms-inventario && ./mvnw test
```

| Clase | Qué comprueba | ¿Necesita MongoDB? |
|---|---|---|
| `ProductoControllerTest` | Cada endpoint: 201/200/204/400/401/403/404 | No |
| `ProductoServiceTest` | Reglas de rol, baja lógica, eventos publicados | No |
| `RabbitInventarioEventPublisherTest` | Sobre y datos de cada evento; broker caído no rompe | No |
| `VentaCreadaListenerTest` | Conversión del mensaje; fallo se propaga al reintento | No |
| `ReservaStockServiceTest` | Lógica de reserva, rechazo, umbral, idempotencia (con mocks) | No |
| `ReservaStockIntegracionTest` | **Todas o ninguna** y **reentrega sin doble descuento**, con transacciones reales | **Sí** |
| `ProductoRepositoryTest`, `MsInventarioApplicationTests` | `Decimal128`, listado sin inactivos, índices, contexto | **Sí** (`docker compose up -d mongodb`) |

## Estructura

```
com.monpoint.inventario
├── audit/        RequestIdFilter (X-Request-ID ➔ logs y errores)
├── config/       MongoConfig, SecurityConfig (sin estado, no bloquea), RabbitConfig (exchanges, cola y cola de fallidos)
├── controller/   ProductoController, GlobalExceptionHandler (RFC 9457)
├── dto/          request/ProductoRequest · response/ProductoResponse
├── event/        InventarioEventPublisher (puerto), InventarioEventos, SobreEvento, LineaReservada,
│                 VentaCreadaEvento, VentaCreadaListener, impl/RabbitInventarioEventPublisher
├── exception/    AccesoDenegadoException, RecursoNoEncontradoException
├── model/        Producto (@Document "productos"), EventoProcesado ("eventos_procesados"), Rol
├── repository/   ProductoRepository, EventoProcesadoRepository
├── security/     UsuarioActual
└── service/      ProductoService + impl/ProductoServiceImpl, ReservaStockService, ResultadoReserva
```

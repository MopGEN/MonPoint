# ms-clientes

Clientes de MonPoint (H-040, H-042; épica E-04). Puerto interno **8084**, base `clientes_db`, usuario `clientes_user`.
Se llega a él **solo a través del Gateway** (`http://localhost:8080/clientes/...`); no publica puertos.

## Qué hace

Alta, consulta (con búsqueda por nombre parcial), modificación y baja de clientes, y publica `ClienteCreado` y
`ClienteActualizado` a RabbitMQ. No consume eventos y no llama a ningún otro servicio.

Patrón: capas `controller ➔ service ➔ repository` (como ms-auth). La identidad viene del Gateway en `X-User-Id` /
`X-User-Role`; este servicio **no valida el JWT** y verifica el rol él mismo (RNF-07). Los errores salen en RFC 9457
`ProblemDetail` con `requestId`.

## Endpoints

| Método y ruta | Rol | Éxito | Errores |
|---|---|---|---|
| `POST /clientes` | ADMIN, VENDEDOR | 201 + cliente (con `fechaRegistro` automática) | 400 (lista **todos** los campos inválidos en `campos`), 401 |
| `GET /clientes?nombre=ana` | ADMIN, VENDEDOR | 200 + lista (sin `nombre`: todos; con `nombre`: coincidencia parcial sin distinguir mayúsculas) | 401 |
| `GET /clientes/{id}` | ADMIN, VENDEDOR | 200 | 404, 401 |
| `PUT /clientes/{id}` | ADMIN, VENDEDOR | 200 | 400, 404, 401 |
| `DELETE /clientes/{id}` | **ADMIN** | 204 | 403 (VENDEDOR), 404, 401 |

Cuerpo de alta y modificación: `{"nombre":"Ana Pérez","correo":"ana@correo.com","telefono":"3312345678","direccion":"Calle 1"}`.
`nombre` es obligatorio; `correo` es opcional pero, si viene, debe tener formato válido y **no** se exige único.

### ⚠️ Decisiones que la documentación no fija (confirmar con el Product Owner)

| Tema | Lo que se hizo | Por qué |
|---|---|---|
| Rol para **modificar** | ADMIN y VENDEDOR | H-040 solo fija alta/consulta (ambos) y baja (ADMIN) |
| Baja **física** | `DELETE` borra el documento | El Doc 04 no tiene campo `activo` en `clientes` y su esquema está cerrado. No hay evento de baja, así que las réplicas de otros servicios no se enteran |
| Correo opcional | Se valida solo si viene | H-042 pide rechazar "correo inválido" y "nombre vacío"; no dice que el correo sea obligatorio |
| Paginación | **No** se implementa | H-040 la menciona, pero Doc 01 §7.4 la recortó |
| Outbox | **No** se implementa | Doc 01 §7.4. Se publica con `RabbitTemplate` después de confirmar la transacción; si el broker cae en ese instante, el evento se pierde (queda en el log) |

## Eventos (⚠️ provisional hasta el catálogo H-008)

Exchange `clientes.eventos` (topic). Sobre común: `eventoId`, `tipo`, `version`, `fecha`, `origen`, `datos`.
Las routing keys (`cliente.creado`, `cliente.actualizado`), el formato de `version` (`"1"`) y los campos de `datos`
(`clienteId`, `nombre`, `correo`, `telefono`, `direccion`, `fechaActualizacion`; salen del Doc 04, `clientes_replica` y
`contrapartes_proy`) son una **propuesta**: `docs/eventos/` sigue vacío. Todo está concentrado en
`event/ClienteEventos.java` y `event/impl/RabbitClienteEventPublisher.java`.

## Variables de entorno

| Variable | Por omisión (desde el IDE) |
|---|---|
| `SERVER_PORT` | `8084` |
| `MONGO_URI` | `mongodb://clientes_user:clientes_pass@localhost:27017/clientes_db?replicaSet=rs0&authSource=clientes_db&directConnection=true` |
| `EUREKA_URL` | `http://localhost:8761/eureka/` |
| `RABBITMQ_HOST` / `RABBITMQ_USER` / `RABBITMQ_PASSWORD` | `localhost` / `monpoint` / marcador de `infra/.env.example` |

## Cómo correrlo

```bash
cd monpoint-ms/infra && docker compose up -d mongodb rabbitmq   # solo la infraestructura
cd ../ms-clientes && ./mvnw spring-boot:run                     # desde el IDE o la terminal
```

Sistema completo: `cd monpoint-ms/infra && docker compose up -d --build`. Para probar por el Gateway:
`POST /auth/login` ➔ token ➔ `Authorization: Bearer <token>` en `http://localhost:8080/clientes`.

## Pruebas

```bash
cd monpoint-ms/ms-clientes && ./mvnw test
```

| Clase | Qué comprueba | ¿Necesita MongoDB? |
|---|---|---|
| `ClienteControllerTest` | Cada endpoint: 201/200/204/400/401/403/404, `campos` con todos los errores | No |
| `ClienteServiceTest` | Reglas de rol, eventos publicados, búsqueda | No |
| `RabbitClienteEventPublisherTest` | Sobre del evento, exchange, routing key; broker caído no rompe | No |
| `ClienteRepositoryTest`, `MsClientesApplicationTests` | Persistencia real, fechas automáticas, índice de `nombre`, contexto | **Sí** (`docker compose up -d mongodb`) |

## Estructura

```
com.monpoint.clientes
├── audit/        RequestIdFilter (X-Request-ID ➔ logs y errores)
├── config/       MongoConfig, SecurityConfig (sin estado, no bloquea), RabbitConfig
├── controller/   ClienteController, GlobalExceptionHandler (RFC 9457)
├── dto/          request/ClienteRequest · response/ClienteResponse
├── event/        ClienteEventPublisher (puerto), ClienteEventos, SobreEvento, impl/RabbitClienteEventPublisher
├── exception/    AccesoDenegadoException, RecursoNoEncontradoException
├── model/        Cliente (@Document "clientes"), Rol
├── repository/   ClienteRepository
├── security/     UsuarioActual
└── service/      ClienteService + impl/ClienteServiceImpl
```

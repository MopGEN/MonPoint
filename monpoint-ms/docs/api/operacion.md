# Operación local y comprobaciones

[Índice API](README.md) · [Seguridad](seguridad.md) · [Infraestructura](../../infra/README.md)

Los comandos siguientes son instrucciones reproducibles, no una afirmación de
que esta actividad documental haya reconstruido el sistema en Windows.
Cada README de servicio conserva detalles de implementación y sus pruebas.

## Servicios y dependencias

El Compose declara **11 servicios**: MongoDB, RabbitMQ, Eureka, Gateway,
cliente-web y los seis microservicios. Java 21 es necesario para compilar en
el host; los proyectos traen Maven Wrapper (`./mvnw` o `.\mvnw.cmd`). Docker
Compose v2 construye y ejecuta el conjunto. Node 22.9 o posterior permite los
guiones de semilla sin instalar dependencias Node.

| Componente | Puerto en la red Compose | Publicación en el host |
|---|---:|---|
| cliente-web (nginx) | 80 | 3000; origen de la aplicación web |
| api-gateway | 8080 | 8080; entrada de APIs |
| eureka-server | 8761 | 127.0.0.1:8761; consola/registro |
| mongodb | 27017 | 127.0.0.1:27017; depuración/desarrollo |
| rabbitmq | 5672, 15672 | 127.0.0.1:5672 y 127.0.0.1:15672; AMQP y consola |
| ms-auth | 8081 | No publicado |
| ms-inventario | 8082 | No publicado |
| ms-ventas | 8083 | No publicado |
| ms-clientes | 8084 | No publicado |
| ms-reportes | 8085 | No publicado |
| ms-notificaciones | 8086 | No publicado |

Los seis servicios usan su propia base/usuario de MongoDB; las transacciones
requieren el replica set rs0. Ventas exige MONGO_URI y verifica sus índices al
arrancar; los crea `infra/mongo-init/02-indices-ventas.js` al aprovisionar.
Los guiones mongo-init se ejecutan solo al inicializar el volumen. Si faltan
índices en una base existente, planificar su aprovisionamiento sin borrar datos:
el mensaje de error de ventas propone down -v, pero eso es un **reinicio
destructivo**, no una reparación segura de una instalación con datos.

Todos se registran en Eureka. Auth no participa en el bus de eventos. Clientes
publica eventos de catálogo; inventario, ventas, reportes y notificaciones
necesitan RabbitMQ para sus recorridos de mensajería. Tener una respuesta HTTP
saludable no acredita consumo ni proyección de eventos.

## Configuración

[`infra/.env.example`](../../infra/.env.example) es una plantilla: conservar el
`.env` privado existente. En instalación nueva copiarla una sola vez y reemplazar
los marcadores. No publicar el archivo, tokens, claves o URIs autenticadas.

| Variable/configuración | Dónde se usa y observación |
|---|---|
| JWT_SECRET | Auth y Gateway, mismo valor; mínimo 32 bytes UTF-8. El marcador público permite firmar tokens a quien lo conozca; sustituirlo por una clave privada de la instalación |
| JWT_EXPIRATION_MS | Auth, positivo, 86400000 por omisión; no está pasada por el Compose actual. Ver [rotación/vigencia](seguridad.md) |
| SEMILLA_NOMINAL_HABILITADA y AUTOTACHI_*_PASSWORD | Inicializador de auth; cuatro claves separadas; necesarias para crear cuentas ausentes. Ver [semilla](../../scripts/SEMILLA_AUTOTACHI.md) |
| MONGO_URI | Conexión propia de cada servicio. Ventas no tiene URI por omisión; los otros cinco conservan valores de desarrollo versionados |
| MONGO_VENTAS_PASSWORD | Compose/guion de aprovisionamiento de ventas; cambiarla no rota por sí solo una credencial Mongo ya creada |
| RABBITMQ_USER / RABBITMQ_PASSWORD | Broker y servicios del bus; configurar iguales |
| EUREKA_URL / SERVER_PORT | Destino del registro y puerto de cada proceso, definidos por Compose |
| CORS_ORIGENES | Gateway; incluir el origen exacto del cliente, p. ej. http://localhost:3000 |
| VITE_GATEWAY_URL | URL del Gateway incorporada al build del cliente; cambiar exige reconstruir cliente-web |
| monpoint.reportes.zona-horaria | Propiedad de reportes: America/Mexico_City; no hay variable específica declarada en application.properties |

Un `.env` leído por Compose **no** se carga automáticamente al ejecutar Java
en el IDE o Maven. Configurar allí las variables del proceso. Para conectar
al Mongo publicado desde el host, añadir directConnection=true, porque el
miembro rs0 se anuncia con nombre interno. Dentro de Compose las URIs usan
el nombre mongodb. No introducir credenciales reales en ejemplos versionados.

## Arranque en Windows

Desde PowerShell, con `.env` ya configurado y Docker disponible:

```powershell
Set-Location C:\Users\osber\MonPoint\monpoint-ms
if (!(Test-Path -LiteralPath infra/.env)) { throw 'Crear y configurar infra/.env antes de arrancar.' }
docker compose --project-directory infra config --quiet
if ($LASTEXITCODE -ne 0) { throw 'Compose no es valido; revisar la configuracion sin imprimir secretos.' }
docker compose --project-directory infra up -d --build
if ($LASTEXITCODE -ne 0) { throw 'El conjunto no pudo construirse o arrancar.' }
docker compose --project-directory infra ps
```

En Linux/macOS se ejecutan los mismos comandos Docker desde `monpoint-ms`, con
el directorio correspondiente. No hace falta down -v para actualizar código.
Esperar a que los servicios estén saludables y registrados en Eureka antes de
probar por Gateway: hay cachés de descubrimiento y la primera petición puede
recibir 503 durante el registro. No prometer un tiempo exacto de recuperación.

El cliente abre en `http://localhost:3000`; las APIs, en `http://localhost:8080`.
La consola de Eureka muestra las instancias registradas. El healthcheck propio
de cada aplicación es GET /actuator/health. El del Gateway no agrega la salud
de los seis dominios ni expone sus healthchecks por prefijo.

Levantar consumidores/colas antes de importar catálogos o vender. Un exchange
durable no guarda publicaciones sin cola enlazada. Importar con
`node --env-file=infra/.env scripts/semilla-autotachi.mjs` según su guía; no
escribir directamente en inventario para evitar sus eventos de catálogo.

## Comprobación del contrato

Con credenciales del entorno, sin imprimir la respuesta de login:

1. Autenticar una cuenta nominal ADMIN y el VENDEDOR; conservar los tokens solo
   en memoria. Verificar el perfil por GET /auth/me.
2. Hacer una consulta permitida y otra denegada por rol: por ejemplo, listado
   de productos con ambos, notificaciones solo con ADMIN. Sin token debe
   rechazarse una ruta protegida antes de llegar al servicio.
3. Registrar una venta controlada y conservar id/folio. Un 202 es PENDIENTE;
   consultar ese id hasta CONFIRMADA/CANCELADA. No reenviar un POST incierto.
4. Consultar el ticket por su id: una proyección aún no recibida puede devolver
   VENTA_NO_DISPONIBLE aunque ms-ventas ya informe confirmación.
5. Si se capturan evidencias, guardar cuerpos/status concretos y request-id;
   excluir tokens y contraseñas. Dos GET 200 sin cuerpo no prueban la transición.

Las peticiones de escritura cambian datos reales: usar un entorno de prueba
o elegir un caso cuyo efecto se haya acordado. No ejecutar toda la suite
integral contra la base de demostración para obtener un conteo de pruebas.

## Pruebas por componente

Desde cada módulo Java: `.\mvnw.cmd test` en Windows, `./mvnw test` en Linux.
Los requisitos varían; no se deduce cobertura integral de una ejecución que
omite casos o no tiene MongoDB disponible.

| Módulo | Dependencias y alcance de sus pruebas |
|---|---|
| api-gateway | Sus pruebas usan destinos simulados, sin Eureka ni auth reales; validan routing, JWT, CORS y sustitución de cabeceras |
| eureka-server | Pruebas de contexto y registro con servidor local; ver su README |
| ms-auth | La suite completa requiere MongoDB y configuración de semilla nominal; los controladores/servicios unitarios usan dobles |
| ms-inventario | Las pruebas de repositorio/reserva integral requieren MongoDB replica set; unitarias de HTTP, reglas y publicador no prueban broker real |
| ms-clientes | Repositorio/contexto requieren MongoDB; pruebas unitarias cubren API, reglas y adaptador de publicación |
| ms-ventas | MONGO_VENTAS_TEST_URI activa las tres clases de transacciones; sin ella se omiten explícitamente |
| ms-reportes | Integración de proyecciones/configuración/documentos/agregación requiere MongoDB; listeners AMQP deshabilitados en esas pruebas |
| ms-notificaciones | MONGO_NOTIFICACIONES_TEST_URI activa la prueba integral; sin ella se omite y no acredita concurrencia/atomicidad |
| cliente-web | `npm.cmd ci`, `npm.cmd test`, `npm.cmd run build`; recorridos de navegador separados, con simulación o servicios reales según el guion |

La URI de pruebas debe apuntar a una base aislada aprovisionada para el módulo,
con sus índices. No se versiona el valor. Auth necesita las claves nominales
en el proceso y la bandera para crear las cuatro cuentas en esa base nueva.
La suite de ventas puede avanzar folios; no supone una operación solo de lectura.

Los [README de servicio](README.md) y los [expedientes de aceptación](../../../docs/aceptacion/)
distinguen las ejecuciones anteriores. La comprobación de 6.2 valida documentación,
rutas, referencias y ejemplos; no vuelve a acreditar CONTROL 1/2/3 ni la 6.1.

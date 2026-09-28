# MonPoint MS

Migración de MonPointV2 (monolito JavaFX) a una arquitectura de microservicios.

**Equipo:** Alejandro (Product Owner y Tech Lead) · Victor · Hugo
**Periodo de desarrollo:** 28 de septiembre – 6 de noviembre de 2026

---

## Cómo levantar el sistema

```bash
cd infra
cp .env.example .env     # y cambiar los valores
docker compose up -d
```

Verificar que arrancó:

| Qué | Dónde |
|---|---|
| Panel de Eureka | http://localhost:8761 |
| Consola de RabbitMQ | http://localhost:15672 |
| API a través del Gateway | http://localhost:8080 |

Para empezar de cero, borrando los datos:

```bash
docker compose down -v
docker compose up -d
```

---

## Estructura del repositorio

```
monpoint-ms/
├── infra/                  Orquestación: docker-compose, inicialización de Mongo
├── eureka-server/          Registro de servicios
├── api-gateway/            Punto único de entrada
├── ms-auth/                Usuarios, roles, emisión de JWT
├── ms-inventario/          Productos y control de existencias
├── ms-ventas/              Ventas y saga de confirmación
├── ms-clientes/            Clientes
├── ms-reportes/            Configuración y proyecciones
├── ms-notificaciones/      Alertas de existencia baja
├── cliente-web/            Aplicación Vue 3 (desde la semana 5)
├── docs/
│   ├── eventos/            Catálogo de eventos — contrato entre servicios
│   ├── modelo-datos/       Esquema de colecciones
│   └── api/                Documentación de endpoints
-└── legacy/                 Código de MonPointV2, archivado
+
+El código de MonPointV2 está archivado en /MonPointV2, en la raíz del
+repositorio. No se modifica: es la referencia funcional de la migración.
```

---

## Las dos reglas que no se rompen

**1. Ningún servicio llama a otro por HTTP.**
Los servicios se comunican publicando eventos en RabbitMQ y reaccionando a
ellos. Si construyendo algo parece necesario llamar a otro servicio, no se
hace: se escala al Product Owner. Significa que falta un evento o una réplica
local, y esa es una decisión de arquitectura, no de implementación.

**2. Todo mensaje puede llegar dos veces.**
RabbitMQ garantiza que un mensaje se entrega *al menos* una vez, no
*exactamente* una vez. Todo consumidor de eventos debe ser idempotente:
procesar el mismo evento dos veces no puede tener efecto adicional. Se
resuelve registrando el `eventoId` ya procesado antes de aplicar el cambio.

---

## Puertos

| Componente | Puerto | ¿Expuesto al exterior? |
|---|---|---|
| API Gateway | 8080 | Sí — única puerta de entrada |
| Eureka | 8761 | Sí — solo para inspección |
| MongoDB | 27017 | Sí — solo para inspección |
| RabbitMQ (AMQP) | 5672 | Sí — solo para inspección |
| RabbitMQ (consola) | 15672 | Sí — solo para inspección |
| ms-auth | 8081 | No |
| ms-inventario | 8082 | No |
| ms-ventas | 8083 | No |
| ms-clientes | 8084 | No |
| ms-reportes | 8085 | No |
| ms-notificaciones | 8086 | No |
| cliente-web | 5173 | Sí |

Los microservicios no publican puertos: solo se alcanzan desde dentro de la
red de Docker, a través del Gateway. No es una convención que haya que
respetar por disciplina — está impuesto por la configuración.

---

## Bases de datos

Una instancia de MongoDB con una base lógica y un usuario por servicio. El
usuario de un servicio no tiene permiso sobre las bases de los demás.

| Servicio | Base | Usuario |
|---|---|---|
| ms-auth | `auth_db` | `auth_user` |
| ms-inventario | `inventario_db` | `inventario_user` |
| ms-ventas | `ventas_db` | `ventas_user` |
| ms-clientes | `clientes_db` | `clientes_user` |
| ms-reportes | `reportes_db` | `reportes_user` |
| ms-notificaciones | `notificaciones_db` | `notificaciones_user` |

---

## Qué hacer si algo no arranca

| Síntoma | Causa probable | Qué hacer |
|---|---|---|
| `Transaction numbers are only allowed on a replica set` | El replica set no se inicializó | `docker compose logs mongodb`; si hace falta, `down -v` y volver a levantar |
| Un servicio no aparece en Eureka | Aún no pasó el intervalo de registro | Esperar 30 segundos antes de investigar |
| El cliente web recibe 401 en toda petición | El filtro de JWT corre antes que el de CORS | Revisar el orden de los filtros en el Gateway |
| Un servicio arranca y muere | Arrancó antes que MongoDB | Verificar que su `depends_on` usa `condition: service_healthy` |
| Los totales salen mal por centavos | Los importes se guardaron como texto | Declarar `Decimal128` explícitamente en la entidad |
| Un correo duplicado no da error | Los índices no se crearon | Declararlos: Spring Boot 3 no los crea automáticamente |

---

## Documentación del proyecto

| Documento | Contenido | Ruta | 
|---|---|---|
| 01 — Plan de Trabajo | Metodología, cronograma, asignaciones, puntos de control | docs/planeacion/01-plan-de-trabajo.pdf
| 02 — Requisitos y Alcance | Qué se construye y qué explícitamente no | docs/planeacion/02-requisitos-y-alcance.pdf
| 03 — Product Backlog | Historias con criterios de aceptación | docs/planeacion/03-product-backlog.pdf
| 04 — Modelo de Datos | Colecciones campo por campo | docs/planeacion/04-modelo-de-datos.pdf
| Prototipo del cliente | Diseño de Interfaz | docs/prototipo/index.html
| Diagrama de arquitectura | Representacion grafica de la aplicacion | docs/arquitectura/01-componentes-y-trafico.jpg

El tablero de tareas está en Asana. El prototipo del cliente web define el
comportamiento y los textos de la interfaz.

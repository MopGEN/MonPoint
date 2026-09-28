# MonPoint

Sistema de gestión para punto de venta (POS) enfocado en la administración
comercial de micro y pequeñas empresas: control de inventario, registro de
ventas, clientes y reportes.

Este repositorio contiene **dos sistemas**: la versión monolítica en
funcionamiento y la reingeniería a microservicios que la sustituye.

**Equipo:** Alejandro · Victor · Hugo


---

## Los dos sistemas

### `MonPointV2/` — el sistema actual, archivado

Aplicación de escritorio monolítica, funcional y completa. Está congelada: no
recibe cambios. Sirve como **referencia funcional** de la migración — cuando hay
duda sobre cómo debe comportarse una operación, la respuesta está aquí.

| | |
|---|---|
| Arquitectura | Monolito con capas MVC y DAO |
| Interfaz | JavaFX 21 con vistas FXML |
| Persistencia | Hibernate ORM 6.4 sobre MariaDB, esquema relacional |
| Comprobantes | iText 9 |
| Construcción | Java 21 (LTS), Apache Maven |

En `MonPointV2/docs/` está el análisis de su arquitectura, y en
`MonPointV2/database/` el esquema relacional.

### `monpoint-ms/` — el sistema nuevo, en desarrollo

Reingeniería a una arquitectura distribuida. No es una reescritura línea por
línea: el dominio se reparte en seis servicios independientes, cada uno con su
propia base de datos, que se comunican por eventos.

| | |
|---|---|
| Servicios | ms-auth, ms-inventario, ms-ventas, ms-clientes, ms-reportes, ms-notificaciones |
| Infraestructura | API Gateway, Netflix Eureka, RabbitMQ, MongoDB |
| Construcción | Spring Boot 4.1.1 sobre Java 21 |
| Persistencia | MongoDB — una base lógica y un usuario por servicio |
| Comunicación | Asíncrona por eventos a través de RabbitMQ |
| Seguridad | JWT validado localmente en el Gateway, sin estado |
| Cliente | Aplicación web en Vue 3, sustituye por completo a JavaFX |
| Despliegue | Una imagen por servicio, orquestadas con Docker Compose |

**Ningún servicio de dominio invoca a otro por HTTP.** Los servicios publican
eventos y reaccionan a ellos; el navegador es el único que habla por HTTP, y
solo a través del Gateway. Es la decisión que define la arquitectura, y la razón
por la que una venta se acepta en estado pendiente y se confirma después.

Cómo levantarlo, qué puertos usa y qué hacer cuando algo no arranca está en
[`monpoint-ms/README.md`](monpoint-ms/README.md).

---

## Qué resuelve la migración

El monolito funciona, pero concentra todo en un proceso y una base de datos: un
cambio en reportes obliga a reconstruir y desplegar el sistema entero, y un fallo
en cualquier módulo lo detiene completo. Repartir el dominio en servicios
independientes permite desplegar, escalar y fallar por separado, a cambio de
asumir consistencia eventual entre ellos.

Es también el objetivo académico de la asignatura: construir el sistema con
JWT, Eureka, MongoDB, comunicación asíncrona y contenedores.

---

## Documentación

| Documento | Ruta |
|---|---|
| Plan de trabajo, cronograma y asignaciones | [`monpoint-ms/docs/planeacion/`](monpoint-ms/docs/planeacion/) |
| Requisitos y alcance | [`monpoint-ms/docs/planeacion/`](monpoint-ms/docs/planeacion/) |
| Product backlog con criterios de aceptación | [`monpoint-ms/docs/planeacion/`](monpoint-ms/docs/planeacion/) |
| Modelo de datos | [`monpoint-ms/docs/planeacion/`](monpoint-ms/docs/planeacion/) |
| Diagrama de arquitectura | [`monpoint-ms/docs/arquitectura/`](monpoint-ms/docs/arquitectura/) |
| Prototipo del cliente web | [`monpoint-ms/docs/prototipo/`](monpoint-ms/docs/prototipo/) |
| Cómo se trabaja en el repositorio | [`monpoint-ms/CONTRIBUTING.md`](monpoint-ms/CONTRIBUTING.md) |

El tablero de actividades está en Asana y es la fuente sobre fechas y secuencia.
Las incidencias técnicas y las solicitudes de integración viven aquí. No se
duplica información entre las dos.

---

## Estado

Desarrollo del 28 de septiembre al 6 de noviembre de 2026, en seis sprints
semanales. La planeación, la especificación, el modelo de datos y el prototipo
están cerrados.

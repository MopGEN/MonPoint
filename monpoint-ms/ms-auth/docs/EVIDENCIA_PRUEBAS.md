# Evidencia de Pruebas de Calidad (QA) — `ms-auth`

Este documento consolida las evidencias visuales de la suite de pruebas del microservicio de autenticación (`ms-auth`), validando los flujos funcionales, la seguridad perimetral y la persistencia en MongoDB.

---

## 1. Suite de Pruebas Automatizadas en Postman (21/21 Passed)
Ejecución completa mediante el **Collection Runner** de Postman contra el servidor local (`http://localhost:8081`):
* Autenticación exitosa y emisión de token JWT (`HS256`).
* Validación de identidad en `/auth/me`.
* Registro dinámico de vendedores (`201 Created`).
* Control de acceso basado en roles (RBAC): rechazo `403 Forbidden` a vendedores intentando registrar usuarios.
* Borrado lógico con `204 No Content` y rechazo `401 Unauthorized` a usuarios inactivos.
* Validación estricta de contraseñas (H-012) con desglose de reglas incumplidas (`400 Bad Request` en RFC 9457).

![Resultados de Postman Runner](evidencias/postman-runner-passed.png)

---

## 2. Persistencia y Estado en Base de Datos (DataGrip / MongoDB)
Visualización directa de la colección `usuarios` en `auth_db` tras las pruebas:
* Creación de documentos con hash de contraseña BCrypt (costo 12).
* Normalización de correos (`Locale.ROOT`).
* Campo `activo` reflejando el borrado lógico (`true` / `false`).

![Tabla de Usuarios en DataGrip](evidencias/datagrip-mongo-usuarios.png)

---

## 3. Colección y Entorno Utilizados
Los archivos de prueba para importar en Postman se encuentran organizados en:
* [`docs/postman/monpoint-ms-auth.postman_collection.json`](postman/monpoint-ms-auth.postman_collection.json)
* [`docs/postman/monpoint-local.postman_environment.json`](postman/monpoint-local.postman_environment.json)

# MonPoint

Sistema de gestión para punto de venta (POS) enfocado en la administración comercial de micro y pequeñas empresas. El proyecto abarca el control de inventario, registro de ventas, clientes, proveedores y emisión de comprobantes, con el objetivo académico de desacoplar su lógica actual hacia una arquitectura de microservicios con Spring Boot.

Como siguiente paso para el proyecto, estamos proponiendo dividir el sistema en seis microservicios (autenticación/usuarios, inventario/catálogo, ventas/facturación, notificaciones, reportes/dashboard), comunicados vía API REST. Con esto buscamos cubrir requerimientos de escalabilidad, mantenibilidad y disponibilidad que el diseño monolítico actual no resuelve del todo.

## Tecnologías
- Java 21 (LTS)
- JavaFX 21
- Hibernate ORM 6.4
- MariaDB
- Apache Maven
- iText 9 (generación de tickets PDF)

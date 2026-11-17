# Entorno local de Postman

La colección conserva los recorridos de ms-auth. Antes de ejecutarla, importar
el entorno y configurar **en la copia privada de Postman** `adminEmail`,
`adminPassword`, `vendedorEmail` y `vendedorPassword` con cuentas de esa
instalación. Las direcciones genéricas del archivo son referencias históricas;
no acreditan que esas cuentas existan o estén activas en la semilla actual.

Las contraseñas se distribuyen vacías. No exportar ni versionar valores reales,
`authToken` o `vendedorToken`. Marcar una variable como `secret` solo cambia su
presentación en Postman: no cifra una exportación JSON ni sustituye dejarla vacía.

Los literales históricos de pruebas y expedientes de aceptación se conservan
como fijaciones de esos recorridos; no son claves recomendadas para instalar el
sistema. Desactivar una cuenta impide nuevos inicios de sesión, pero los JWT
emitidos siguen vigentes hasta su vencimiento o una rotación coordinada de
`JWT_SECRET`; ver [seguridad](../../../docs/api/seguridad.md).

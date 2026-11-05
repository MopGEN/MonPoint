# 3.1 — semilla por API, fase 1

Fecha local: 4 de noviembre de 2026 (America/Mexico_City). Base de la rama:
`4ab860d`, después del catálogo 2.5. Cambios probados: guion de fase 1 y paso de
`ADMIN_DEFAULT_PASSWORD` desde Compose. No se da por terminada toda la 3.1.

## Resultado observado

| Comprobación | Resultado |
|---|---|
| Pruebas HTTP locales con `node --test monpoint-ms/scripts/semilla.test.mjs` | 11 pasan, ninguna omitida |
| Primera ejecución contra el conjunto existente | 6 altas: VENDEDOR, 2 clientes, 3 productos |
| Segunda ejecución contra ese conjunto | 0 altas, mismos ids |
| Base nueva en proyecto separado | Antes: 1 ADMIN, 0 clientes, 0 productos. Después: 2 usuarios, 2 clientes, 3 productos |
| Contraseñas distintas de las predeterminadas | Login del ADMIN inicial y del VENDEDOR válidos |
| Segunda ejecución sobre la base nueva | 0 altas, todos los ids estables |
| Existencia cambiada por API a 7 | La tercera ejecución conserva 7 y reutiliza el producto |
| Contraseña ADMIN incorrecta | HTTP 401, sin otras peticiones del guion ni cambios en los conteos |
| Ventas emitidas por la semilla | Ninguna |

Los tres productos nuevos tienen existencia 10 y umbral 5. El cambio posterior
a 7 se hizo mediante PUT exclusivamente para probar que la semilla no repone
existencias. No fue una venta ni prueba de reserva de inventario.

## Entorno y método

Linux, Node v24.19.0, Docker Compose v2.40.3. La comprobación real usa las APIs
de los servicios a través del Gateway, sin simulaciones, acceso directo a sus
bases ni eventos fabricados. `base-nueva.json` contiene los resultados, conteos
y el registro de métodos, rutas y estados HTTP, sin tokens ni contraseñas.

El proyecto temporal `monpoint-semilla31` tuvo sus propios volúmenes y red,
Gateway en `127.0.0.1:18080` y ocho servicios saludables: MongoDB, RabbitMQ,
Eureka, Gateway, auth, clientes, inventario y ventas. Se reutilizaron las imágenes
locales del conjunto ya construido; sus identificadores están en
`imagenes-prueba.json`. No se levantó el cliente web para esta prueba por API.
El conjunto habitual permaneció separado, con sus nueve contenedores saludables.

El primer intento del proyecto temporal no ejecutó mongo-init: los archivos de
este workspace tenían permisos 0600, ilegibles para el usuario mongodb del
contenedor. Se preparó una copia de los mismos guiones públicos con archivos
0644 y directorio 0755 y se repitió con volúmenes nuevos del proyecto temporal.
Esa adaptación del montaje es específica de la nube; no modifica mongo-init ni
escribe datos de negocio en él. Con ese montaje, el inicio creó las seis bases
y sus usuarios. La semilla de negocio siguió entrando exclusivamente por API.

El ADMIN se creó con la variable `ADMIN_DEFAULT_PASSWORD` personalizada desde
el primer arranque. El guion recibió ese mismo valor y una contraseña propia
para el VENDEDOR. Los conteos iniciales se comprobaron por API antes de la carga.
Al finalizar se retiró solo el proyecto temporal y sus volúmenes.

## Archivos conservados

- `primera.json` y `segunda.json`: ejecución sobre el conjunto existente.
- `base-nueva.json`: carga, repetición, conservación de existencias y rechazo
  de credenciales en el proyecto temporal.
- `verificar-real.mjs`: guion exacto de esa comprobación. Es un registro histórico
  con rutas absolutas de esta sesión Linux; requiere una base nueva y modifica
  un producto de prueba a existencia 7. Para ejecutar la semilla en Windows,
  seguir [scripts/README.md](../../../monpoint-ms/scripts/README.md).
- `imagenes-prueba.json`: imágenes y estados del proyecto temporal al validar.
- `SHA256.json`: integridad de los archivos y del guion/pruebas de la semilla.

## Lo que no acredita

- No implementa ni prueba la fase 2 de ventas, reportes o notificaciones.
- No observa las réplicas de ventas ni garantiza que un evento publicado haya
  sido consumido. No usa MongoDB para suplir la falta de un endpoint de réplica.
- La repetición probada es serial. Los endpoints no ofrecen unicidad por nombre
  para productos/clientes ni listados de productos inactivos. Renombres, bajas,
  borrados y ejecuciones concurrentes están fuera de esa garantía.
- La respuesta perdida y el POST colgado se probaron con el servidor HTTP de
  pruebas; no mediante fallos inyectados a los contenedores reales.
- No hay transacción global: el resumen identifica lo confirmado antes de un
  fallo y advierte de altas cuyo resultado es incierto.
- No se ejecutó esta fase en Windows ni se evaluó CONTROL 1 con el equipo.

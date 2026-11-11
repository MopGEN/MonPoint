# Cliente: revisión de navegación y Notificaciones

11 de noviembre de 2026. Base de revisión: `75549d7`, posterior a la fusión del
PR #23 de evidencia Windows. Parche recibido `0b52679c`, aplicado con `git am`
como `2dac162`, conservando autoría y mensaje. El tarball y los archivos del
parche aplicado coincidieron byte a byte antes de las correcciones de revisión.

## Alcance y correcciones

Se revisaron App, rutas/roles, navegación, useRecurso, estados de carga, tabla y
la pantalla de alertas contra el controlador y servicio de ms-notificaciones.
GET devuelve una lista plana y admite `leida`; PUT sin cuerpo devuelve 204 y
pone `leida=true`, `activa=false` y fechaLectura. Se mantuvieron package.json y
package-lock.json sin cambios. No se modificó el backend.

- Se sustituyó el mensaje vacío que afirmaba que todo el inventario estaba por
  encima del umbral. Un listado vacío con filtro no permite esa conclusión.
- Se completó la prueba de navegador: antes definía una respuesta 403 pero no
  la ejercitaba. Ahora observa Sin permiso, ausencia de tabla/reintento y un
  rechazo de la guarda al volver por historial con el vendedor autenticado.
- El simulador ahora cambia realmente el estado de la alerta al recibir PUT,
  y comprueba su presencia en Leídas, no solo su desaparición de Sin leer.
- Se documentó el contrato y se añadió un recorrido independiente contra los
  servicios reales. La seguridad de rol se atribuye al servicio, además de la
  autenticación del Gateway, no a ocultar el enlace.

## Validación

| Comprobación | Resultado |
|---|---|
| `npm test` | 21 pruebas, 0 fallos, 0 omitidas |
| `npm run build` | Compilación de producción correcta |
| `test/notificaciones.browser.mjs` | API simulada: filtros, PUT, Leídas, 403, guarda sin perder sesión y enlace profundo sin sesión |
| `test/caja.browser.mjs` | Siete escenarios correctos: confirmada, cancelada, sin respuesta, cancelada histórica, 401, precio de tres decimales y precio inválido |
| `test/notificaciones.real.browser.mjs` | Gateway y servicios reales, sin intercepciones: PASS |

Node 24.19.0, Vite 6.4.5, Playwright y Chromium del entorno Linux. La prueba real
usó Vite en `http://localhost:5173` y el Gateway en `http://localhost:8080`,
ejercitando CORS. La prueba simulada usó la compilación en el puerto 4173.
`fuentes.json` identifica los archivos revisados y `SHA256.json` los artefactos
de este expediente; no contienen contraseñas ni tokens.

## Recorrido real

Operado por navegador automatizado el 11 de noviembre, 07:51:41–07:51:44 hora
de Ciudad de México (`13:51:41–13:51:44Z`). Los datos crudos están en
[resultado.json](resultado.json).

1. Login ADMIN desde el cliente con las credenciales locales del entorno.
2. Alta por API de un producto de prueba exclusivo de esta ejecución:
   `6af472ee57cdec548cbc93a4`, precio 41.15, existencia 10, umbral 5.
3. Venta por API de cinco unidades, folio **53**, CONFIRMADA por **205.75**.
   Su reserva produjo la alerta `6af472ef99e4723b517aee81`, existencia 5,
   activa y sin leer. Las réplicas y la saga se esperaron con plazo acotado;
   un POST incierto no se repite.
4. Navegación por el enlace Notificaciones y comprobación de la fila real.
5. Marcado desde el botón de la pantalla: PUT **204**, desaparición de Sin leer
   y aparición en Leídas. La API confirmó `activa=false`, `leida=true` y
   `fechaLectura=2026-11-11T13:51:43.698Z` para el mismo id.
6. Comprobación visual en 1280x900 y 390x844. La tabla se desplaza dentro de su
   envoltura y el documento no desborda el ancho de la pantalla móvil.
7. Alta de un VENDEDOR de prueba y login real desde el cliente: no aparece el
   enlace a Notificaciones. Su token recibió **403** al consultar la API,
   con `detail: Solo un ADMIN puede consultar o leer notificaciones`.

No hubo errores JavaScript. Se crearon un producto, una venta y un vendedor;
solo se marcó la alerta del producto creado. Las otras alertas de la instalación
se conservaron. Las capturas muestran datos de pruebas anteriores además de la
fila nueva: no son una ejecución desde volúmenes vacíos.

## Límites

- Es una revisión Linux con navegador automatizado, no una sesión humana de
  Windows ni el expediente 6.1 ya fusionado. No sustituye aquella evidencia.
- Se probó la compilación con API simulada y Vite contra el backend real. No se
  reconstruyó el contenedor nginx con este cambio ni se probó el nuevo cliente
  empaquetado en Windows.
- El vendedor real probó enlace oculto y 403 de API. La guarda al navegar con
  sesión viva se comprobó por separado con API simulada y navegación Atrás.
- No se acredita comparación visual con el archivo del prototipo: no se
  recibió ese archivo. Se usan las primitivas y tokens existentes del cliente.
- Esto añade navegación y Notificaciones. No implementa Productos, Clientes,
  Usuarios, Reportes, historial de ventas, formularios compartidos ni diálogos.
  No cambia estados de Asana ni cancela o evalúa controles.

## Incidencia preexistente fuera del parche

La base `75549d7` contiene tres eliminaciones posteriores al PR #23. Faltan
`09-carrito-papel.png`, `10-folio4-confirmado-red.png` y
`11-catalogo-actualizado-papel-7.png` de `6.1-windows-2026-11-11/02-escenarios/`,
aunque aún figuran en su manifiesto. Esta rama hereda esas eliminaciones y no
las restaura ni modifica el expediente Windows. Su manifiesto necesita una
decisión separada: conservar los archivos históricos o documentar su retirada.

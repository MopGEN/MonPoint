# Productos: revisión del parche y CRUD real

11 de noviembre de 2026. Base `d3766cb` (concha y Notificaciones fusionadas).
Parche recibido `8e249323`, aplicado con `git am` como `d5fcce4`, conservando
autoría y mensaje. Las capturas históricas de 6.1 y el empaquetado se dejan
aplazados por indicación del usuario; este cambio no modifica aquel expediente.

## Hallazgos corregidos

1. `disabled` se aplica al renderizar: no evita dos submit sin esperar ese
   render. Se añadieron guardas síncronas a guardar y confirmar baja. Los campos
   quedan deshabilitados mientras se guarda. La prueba mantiene un alta en
   vuelo, dispara dos submit, exige un único POST y comprueba que Escape no
   cierre la operación. El doble clic de baja también produce un único DELETE.
2. El simulador rechazaba un nombre duplicado «como el servicio», pero la API
   no exige unicidad de nombres. Ahora el 400 se identifica como respuesta
   ficticia para comprobar la presentación de `campos`. El simulador aplica
   además el umbral 5 cuando el alta recibe null.
3. La restricción de dos decimales y doce enteros pertenece al formulario, no al
   DTO BigDecimal del backend. Se aclaró esa diferencia y se añadió una prueba
   de conservación del valor decimal en el JSON enviado en los límites
   admitidos. No se afirma que Number represente exactamente esas fracciones
   en binario. El formulario no redondea automáticamente precios previos.
4. El diálogo compartido tiene nombre accesible vinculado al h2 y sincroniza
   su apertura también al montarse con `abierto=true`.

No cambiaron backend, dependencias, lockfile, Caja.vue ni cobro.test.js.

## Extracción decimal

Se compararon las declaraciones de `decimal`, `redondear`, `precioValido`,
`centavos` y `dinero` con las de `caja/cobro.js` en `d3766cb`, extrayéndolas con
el parser de JavaScript. Sus cuerpos son idénticos. El módulo anterior importa
los auxiliares y reexporta la interfaz anterior. Las catorce pruebas de Caja
permanecen sin modificar y pasan, incluidos los precios de tres decimales.

## Validación ejecutada

| Comprobación | Resultado |
|---|---|
| `npm test` | 36 pruebas: 14 de cobro, 15 de formulario y 7 de useRecurso; 0 fallos/omitidas |
| `npm run build` | Correcta |
| Productos con API simulada | Filtro, errores por campo, altas, edición, baja, nombre de diálogo, bloqueo durante envío y doble submit/clic |
| Caja con API simulada | Siete escenarios correctos, sin errores JS ni desbordamiento móvil |
| Notificaciones con API simulada | Filtros, marcado, 403 y guarda de rol correctos |
| Productos con Gateway real | Alta 201, edición 200, baja 204 y permisos por rol correctos |

Entorno Linux con Node 24.19.0, Vite 6.4.5 y Chromium/Playwright. La compilación
se sirvió en 4173 para los recorridos simulados; el CRUD real usó Vite en
`http://localhost:5173` y Gateway en `http://localhost:8080`, sin interceptar las
peticiones y ejercitando CORS.

## Resultado real

08:23:26–08:23:29 de Ciudad de México (`14:23:26–14:23:29Z`), navegador
automatizado. [resultado.json](resultado.json) conserva las respuestas recibidas,
sin contraseñas ni tokens.

- Alta desde el formulario: producto `6af47a5f57cdec548cbc93a6`, precio 41.15,
  existencia 10, umbral omitido que el servicio convirtió en 5. Respuesta 201.
- Edición desde el formulario: mismo id, descripción nueva, precio 75.50,
  existencia 8 y umbral 7. Respuesta 200. Las capturas incluyen la tabla en
  390x844, sin desbordamiento del documento.
- Consulta de validación por API: nombre vacío y números negativos devolvieron
  400 con el mapa `campos` esperado. Esto contrasta su forma; la presentación
  de errores en el formulario se prueba por separado con la API simulada.
- VENDEDOR creado para la prueba: login real, consulta 200, sin acciones ni
  diálogos en la pantalla. Sus intentos de POST, PUT y DELETE devolvieron 403.
- Cancelar la confirmación de baja conservó el producto activo. Confirmarla
  desde el cliente devolvió 204 y quitó la fila. GET por id devolvió 200 con
  `activo=false`; el listado activo ya no contenía ese id.

Se crearon un producto y un usuario VENDEDOR. Solo se dio de baja el producto
creado por esta prueba. No hubo errores JavaScript ni reinicio de volúmenes.
No se crearon ventas para este CRUD.

## Alcance y límites

- No se reconstruyó nginx ni se ejecutó el nuevo cliente en Windows; el usuario
  aplazó ese empaquetado hasta terminar las pantallas.
- Los datos pertenecen al conjunto de nube ya existente, distinto del recorrido
  Windows de 6.1. No es una aceptación nueva desde estado cero.
- El umbral nulo se verificó al crear. El formulario impide dejarlo vacío al
  editar; que null conserve el umbral actual se contrastó contra Producto.java.
- La pantalla rechaza precios de más de dos decimales aunque la API los admita.
  Al abrir un producto así conserva el texto, pero exige corregir el precio
  antes de guardar otros cambios. No se oculta esa limitación como una regla
  del servidor.
- PUT reemplaza también existencia y la API no tiene versión optimista. Esta
  revisión no acredita ausencia de pérdidas de actualización frente a ventas
  concurrentes. El listado tampoco promete actualización en tiempo real.
- La insignia de umbral refleja los datos cargados. Editar existencia no emite
  StockBajo: ese evento nace de reservar stock para una venta.
- Las verificaciones de doble envío y bloqueo se hicieron con API simulada.
  El CRUD real valida los contratos; no es una prueba de carga o concurrencia.
- No se tocó Asana ni se evaluaron/cancelaron controles. Clientes, Reportes,
  Usuarios e historial no forman parte de este parche.

`fuentes.json` identifica las fuentes revisadas y `SHA256.json` los bytes de
este expediente. Las fechas visibles en las capturas usan la zona del navegador
de nube; los instantes crudos del resultado están en UTC.

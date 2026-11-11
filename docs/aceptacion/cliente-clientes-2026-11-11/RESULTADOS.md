# Clientes: revisión del parche y del patrón de escritura

11 de noviembre de 2026. Base `601d1e3` (Productos fusionado, PR #25).
Parche recibido `7bbee301`, aplicado con `git am` como `78fff9f`, conservando
autoría y mensaje. El empaquetado y la incidencia de capturas antiguas quedan
aplazados por indicación del usuario. No se modifica ese expediente.

## Revisión

El cerrojo privado de `useAccion` se toma antes de llamar a la escritura y se
libera en finally, tanto ante éxito como fallo. Las llamadas omitidas no ejecutan
el callback ni limpian el estado de la que sigue pendiente. Los errores locales
y remotos se exponen en `campos`; los demás, en `error`. No se añadieron reintentos
automáticos ni idempotencia HTTP. Clientes lo usa para guardar y eliminar;
Productos y Notificaciones conservan sus guardas previas.

Se contrastaron los permisos con ClienteServiceImpl: ambos roles crean y
modifican; solo ADMIN elimina. El borrado usa repository.delete y no publica
evento de baja. El DTO no exige unicidad de nombre/correo ni formato de teléfono
o dirección. Nombre es obligatorio y correo opcional se valida con @Email.

Correcciones de esta revisión:

1. La prueba negativa de borrado podía pasar mientras Cargando ocultaba la
   tabla. Ahora espera el cierre y la presencia positiva del listado recargado
   antes de comprobar la ausencia. El CRUD real añade GET por id y listado.
2. La comprobación local de correo rechazaba espacios o una segunda arroba sin
   distinguir una parte local entre comillas. Se comprobó que la API admite
   `"Ana Ruiz"@example.test`, y el formulario ahora permite esa forma. Una
   prueba unitaria cubre también la arroba dentro de las comillas. La autoridad
   final sigue siendo @Email, no una supuesta equivalencia completa con el
   validador de Java.
3. Se precisaron dos comentarios: un ref cambia de forma síncrona, aunque el
   DOM espere al render, y Clientes sí ofrece búsqueda por nombre en la API.
   El filtro común elige buscar localmente para combinar correo y teléfono y
   quitar acentos; no se presenta como una carencia de aquel endpoint.

## Pruebas ejecutadas

| Comprobación | Resultado |
|---|---|
| `npm test` | 56 pruebas: 14 cobro, 15 Productos, 11 Clientes, 9 useAccion y 7 useRecurso; 0 fallos/omitidas |
| `npm run build` | Correcta |
| Clientes con API simulada | Campos opcionales, filtros, validación local, doble clic en el mismo turno, modificación y borrado tras recarga |
| Productos con API simulada | Regresión correcta, incluido doble submit/baja |
| Notificaciones con API simulada | Regresión correcta, incluido 403 y navegación por rol |
| Clientes con Gateway real | CRUD desde ambos roles, 403 al borrar como vendedor y errores de campo reales: PASS |

Las pruebas de Productos, Caja y useRecurso no se modificaron. Las quince de
Productos pasan con la delegación a `comun/filtro.js`, conservando su firma.
No cambiaron dependencias, lockfile ni backend. Node 24.19.0, Vite 6.4.5 y
Chromium/Playwright en Linux. Los recorridos simulados usaron la compilación en
4173; el real usó Vite en `http://localhost:5173` y Gateway en 8080 con CORS,
sin interceptar peticiones.

## Recorrido real conservado

09:07:03–09:07:05 de Ciudad de México (`15:07:03–15:07:05Z`).
[resultado.json](resultado.json) conserva los datos y estados sin tokens ni
contraseñas. Navegador automatizado sobre el conjunto de nube ya existente.

1. Login ADMIN y alta de un VENDEDOR exclusivo de la prueba.
2. Login VENDEDOR, navegación a Clientes y alta desde el formulario con correo,
   teléfono y dirección vacíos: **201**, opcionales null, id
   `6af48498ca3cccd0ca939c0e`.
3. Modificación desde la pantalla, teléfono `5544332211`: **200**. No se ofrece
   Eliminar. DELETE por API con el token de vendedor: **403**; GET mantiene 200.
4. Login ADMIN y alta con un correo que pasa la comprobación básica local,
   `ana@..example.test`, pero el servidor rechaza: **400**. Se observa
   `campos.correo` en el campo, `aria-invalid=true` y ningún aviso general
   duplicado. No se creó un cliente en ese rechazo.
5. Corregir a `"Ana Ruiz"@example.test`: **201**, id
   `6af48499ca3cccd0ca939c0f`. Editar dirección desde el formulario: **200**,
   conservando el correo citado. Captura a 390x844 y documento sin desbordamiento.
6. Para ambos clientes de la prueba, cancelar el diálogo conserva GET **200**.
   Confirmar elimina con **204**. Tras la recarga, no hay fila ni entrada en el
   listado y la consulta directa devuelve **404**.

No hubo errores JavaScript. Esta ejecución creó un usuario y dos clientes;
eliminó solo sus dos clientes y conservó el usuario. Las repeticiones de este
recorrido crean otro juego de datos, no son idempotentes. Las capturas muestran
la zona del navegador de nube; los instantes crudos están en UTC.

## Límites

- No se ejecutó el nuevo cliente empaquetado en Windows ni se reconstruyó nginx.
- No es el recorrido de 6.1, no parte de volúmenes nuevos y no cambia Asana ni
  evalúa/cancela controles.
- La protección de doble envío es por instancia en el cliente; no garantiza
  idempotencia ante múltiples pestañas ni permite recuperar un POST incierto.
- La comprobación de correo es básica, no un parser RFC completo. Aceptar una
  forma localmente no garantiza que el servidor la acepte. Se verificaron
  concretamente el correo citado y el rechazo remoto descritos arriba.
- Se contrastó que eliminar no publica eventos leyendo el código. No se
  midieron colas ni se afirma que desaparezca una réplica en ventas/reportes.
- La selección de cliente en Caja se actualiza al consultar el directorio;
  esta prueba no elimina referencias históricas ni modifica ventas existentes.
- No se realizó una prueba de carga o concurrencia del backend. El doble clic
  se verifica con promesa pendiente en la prueba unitaria y en el simulador.

`fuentes.json` identifica los archivos revisados; `SHA256.json` describe los
bytes de las capturas y archivos de este expediente.

# cliente-web

Cliente web de MonPoint MS. Vue 3 + Vite + Pinia. Habla unicamente con el
`api-gateway`; ningun microservicio se expone al navegador.

## Arrancar

```bash
cd monpoint-ms/cliente-web
cp .env.example .env     # ajustar VITE_GATEWAY_URL si el Gateway no esta en 8080
npm install
npm run dev              # http://localhost:5173
```

Requiere la infraestructura levantada (`infra/docker-compose.yml`) con
`eureka-server`, `api-gateway` y `ms-auth` en marcha.

El puerto 5173 es obligatorio en desarrollo: es el que el Gateway trae en
`monpoint.cors.origenes`. Por eso `vite.config.js` fija `strictPort`, para que
Vite no se mueva en silencio a otro puerto y el fallo aparezca como un error de
CORS sin causa visible.

## Estructura

```
src/
├── main.js            arranque y cableado (inyecta el token al cliente HTTP)
├── App.vue            lateral de marca, cabecera interior y RouterView
├── api/gateway.js     cliente HTTP unico: token, errores, expiracion
├── stores/sesion.js   sesion en Pinia (token EN MEMORIA)
├── stores/preferencias.js tema del sistema o eleccion local del usuario
├── router/index.js    rutas y guarda de sesion y rol
├── assets/base.css    variables del prototipo, reinicio y primitivas
├── componentes/       navegacion, estados de carga y tabla accesible
├── composables/       useRecurso: cancelacion, ultima respuesta y errores
│                     useAccion: guarda de escritura y errores por campo
└── vistas/
    ├── IniciarSesion.vue   autenticacion real, marca y conmutador de tema
    ├── Caja.vue            catálogo, carrito y seguimiento del cobro (5.5)
    ├── Productos.vue       catálogo activo y CRUD por rol
    ├── Clientes.vue        altas/cambios para ambos roles; eliminación ADMIN
    ├── Reportes.vue        agregación, tres PDF y datos de empresa (ADMIN)
    ├── Usuarios.vue        cuentas activas, edición y desactivación (ADMIN)
    ├── Notificaciones.vue  alertas, filtros de lectura y marcado (solo ADMIN)
    └── NoEncontrada.vue
```

## Tres decisiones que conviene no revertir sin leer esto

**1. El token vive solo en memoria.** No esta en `localStorage` ni en
`sessionStorage`. Historias H-080 y H-081 del Documento 03: cualquier JavaScript
que llegue a ejecutarse en la pagina puede leer esos almacenes completos.

El costo aceptado es que **recargar la pagina cierra la sesion**. Es el
comportamiento esperado, no un defecto. Si alguna vez se decide quitar esa
molestia, la via correcta es una cookie `HttpOnly; Secure; SameSite` emitida por
el Gateway (que JavaScript no puede leer), con la proteccion anti-CSRF que eso
exige, no mover el token a `localStorage`.

**2. No hay proxy de desarrollo.** `vite.config.js` no define `server.proxy`. Un
proxy haria que el navegador nunca viera una peticion de origen cruzado en
desarrollo, y el CORS del Gateway solo se probaria al desplegar. Sin proxy se
ejercita desde el primer dia.

**3. La guarda del enrutador no es seguridad.** Evita mostrar pantallas que
fallarian; no protege datos. Quien edite el estado de Pinia desde las
herramientas del navegador ve cualquier pantalla. Lo que protege es que el
Gateway valide token y rol en cada peticion. Toda ruta nace privada salvo que
declare `meta.publica`, para que olvidar la meta cierre la pantalla en lugar de
abrirla.

## Contrato con el Gateway

| Metodo | Ruta | Notas |
|---|---|---|
| POST | `/auth/login` | `{correo, password}` → `{token, tipo, correo, nombre, rol, expiraEn}` |
| GET | `/auth/me` | perfil actual e id estable de la cuenta autenticada |
| GET | `/auth/usuarios` | solo ADMIN; cuentas activas |
| POST | `/auth/register` | alta ADMIN; 201 |
| PUT | `/auth/usuarios/{id}` | ADMIN sobre otras cuentas; ambos roles sobre su perfil, sin cambiar rol propio |
| PATCH | `/auth/usuarios/{id}/desactivar` | solo ADMIN, excepto su propia cuenta; 204 |
| POST | `/ventas` | 202 con la venta **PENDIENTE**; no es confirmacion |
| GET | `/ventas/{id}` | detalle y estado de la venta — tarea 5.2 |
| GET | `/inventario/productos` | activos; ADMIN y VENDEDOR |
| POST | `/inventario/productos` | alta ADMIN; 201 con producto |
| PUT | `/inventario/productos/{id}` | reemplaza datos editables, incluida existencia; solo ADMIN |
| DELETE | `/inventario/productos/{id}` | baja lógica ADMIN; 204 |
| GET | `/clientes` | listado para ambos roles; la API también admite `?nombre=` |
| POST | `/clientes` | alta para ambos roles; 201 |
| PUT | `/clientes/{id}` | modificación para ambos roles; 200 |
| DELETE | `/clientes/{id}` | eliminación física, solo ADMIN; 204 |
| GET | `/reportes/ventas/agregado?periodo=DIA&desde=YYYY-MM-DD&hasta=YYYY-MM-DD` | ADMIN; DIA, MES o ANIO |
| GET | `/reportes/ventas/historial?desde=YYYY-MM-DD&hasta=YYYY-MM-DD` | ADMIN; PDF, hasta 366 días inclusivos |
| GET | `/reportes/ventas/{id}/ticket` | ADMIN y VENDEDOR; PDF de confirmada, 404 si falta la proyección y 409 si cancelada |
| GET | `/reportes/productos/listado` | ADMIN; PDF del catálogo activo proyectado |
| GET | `/reportes/clientes/listado` | ADMIN; PDF del directorio proyectado |
| GET | `/reportes/configuracion` | ambos roles; 404 si todavía no existe |
| PUT | `/reportes/configuracion` | ADMIN; registra o modifica; 200 |
| GET | `/notificaciones?leida=false` | solo ADMIN; omitir el filtro para todas o usar `true` para leídas |
| PUT | `/notificaciones/{id}/leida` | solo ADMIN; sin cuerpo; 204; el servidor cierra la alerta |

`expiraEn` es la **vigencia en milisegundos**, no una fecha. `stores/sesion.js`
la convierte a instante absoluto (`Date.now() + expiraEn`). Tratarla como fecha
de epoca da 1970 y la sesion nace caducada.

Los errores llegan como ProblemDetail (RFC 9457). `api/gateway.js` extrae
`detail` y lo entrega en `ErrorDeGateway.message`, ya redactado para mostrarse
al cajero; las vistas no deben reescribirlo.

## Navegación y notificaciones

La concha sigue la referencia visual del prototipo: lateral marino de 232 px
(64 px colapsada), marca local, cabecera interior con negocio, título y ruta,
conmutador de tema y menú de usuario. «Salir» está dentro de ese menú. En móvil
la navegación pasa a una fila desplazable para conservar el espacio del
contenido. Los títulos de las seis pantallas se muestran una sola vez, en la
cabecera compartida.

Inter se sirve desde `src/assets/fuentes/`, con su licencia OFL, sin Google
Fonts ni peticiones externas. Los dos subconjuntos variables pesan 133.324
bytes en total. Los tokens conservan sus nombres `--mp-*`, incorporan los
tríos de texto/fondo/borde para éxito, aviso, error y proceso, y separan radios
de controles (8 px) y tarjetas/diálogos (12 px).

El tema sigue `prefers-color-scheme` hasta elegir uno. Solo esa preferencia se
persiste en `localStorage` (`monpoint.tema`); no se guarda la sesión. Si el
almacenamiento está bloqueado, el interruptor funciona en la pestaña actual.

La cabecera consulta `GET /reportes/configuracion`, permitido para ambos roles.
Muestra el nombre registrado, «Auto Tachi» si responde 404 y «MonPoint» mientras
carga o si falla. Guardar la configuración en Reportes invalida esta lectura.
ADMIN tiene además la campana, que consulta `GET /notificaciones?leida=false`
al iniciar, al abrir el panel y tras marcar una alerta en la pantalla. El número
corresponde a la última lectura; **no hay actualización en tiempo real**. Ante
carga o error se omite el número, sin convertir el fallo en cero alertas.
VENDEDOR no monta la campana ni hace esas consultas.

La rejilla de productos de Caja, sus atajos y stepper del prototipo quedan para
otra intervención. No se añaden rutas de Proveedores, Historial, Ventas en
proceso, Configuración ni Guía de estilo.

Recorrido visual con API simulada, build y preview:

```bash
PLAYWRIGHT_MODULE=/ruta/a/playwright/index.mjs node test/concha.browser.mjs
```

Comprueba ambos temas y roles, geometría, móvil a 390 px, fuentes locales sin
peticiones externas, menú con Escape, contador tras marcar, error/reintento de
avisos, actualización del nombre de empresa y preferencia con almacenamiento
bloqueado. Acepta las variables de navegador y salida de los recorridos de abajo.

Con Vite en `http://localhost:5173` y servicios locales de prueba:

```bash
PLAYWRIGHT_MODULE=/ruta/a/playwright/index.mjs node test/concha.real.browser.mjs
```

Este recorrido lee las seis pantallas, contrasta negocio/contador con la API,
abre y cancela un diálogo y comprueba VENDEDOR y F5. **Crea una cuenta temporal
de vendedor y la desactiva al terminar**; no crea ventas, productos ni alertas.
No es idempotente ni se ejecuta con `npm test`. Usa las mismas variables de
credenciales y navegador de los demás recorridos reales. Su evidencia no
acredita ejecución en Windows ni empaquetado nginx.

[Capturas claras/oscuras y resultados de la concha](../../docs/aceptacion/cliente-concha-visual-2026-11-12/RESULTADOS.md).

La barra deriva sus enlaces de `meta.nav` y comparte `rolPermitido` con la
guarda. Ambos roles ven Caja, Productos y Clientes; ADMIN ve además Reportes,
Notificaciones y Usuarios. Las pantallas que
todavía no existen no se anuncian. El Gateway valida el token y cada servicio
autoriza las operaciones; ocultar un enlace no protege la API.

Notificaciones empieza en «Sin leer» y admite «Leídas» y «Todas». Marcar una
alerta envía PUT sin cuerpo y vuelve a consultar el filtro actual. El servicio
pone `leida=true`, `activa=false` y `fechaLectura`; por eso desaparece de «Sin
leer». Un listado vacío no demuestra que el inventario esté por encima de sus
umbrales: puede haber alertas ya leídas. Las fechas se muestran en la zona del
navegador. Actualmente la API devuelve una lista plana de entidades.

`useRecurso` aborta la carga anterior y descarta respuestas de secuencias viejas.
`EstadoRecurso` distingue carga, error, 403 y vacío. `TablaDatos` concentra la
tabla accesible y su desplazamiento horizontal en pantallas estrechas.

Recorrido con API simulada, después de compilar y arrancar `npm run preview`:

```bash
PLAYWRIGHT_MODULE=/ruta/a/playwright/index.mjs node test/notificaciones.browser.mjs
```

Acredita filtros y marcado, error 403, navegación por rol sin recargar y enlace
profundo sin sesión. No consulta los servicios reales. Acepta `BASE_URL`
(por omisión `http://localhost:4173`), `GATEWAY_URL`, `SALIDA`, `CHROMIUM_PATH`
y `CHROMIUM_NO_SANDBOX=1` para entornos que requieran Chromium sin sandbox.
Playwright se instala fuera del checkout como se explica en las pruebas de Caja;
si no se indica `CHROMIUM_PATH`, requiere sus navegadores instalados.

El recorrido real es independiente y requiere el conjunto ya levantado y Vite
en `http://localhost:5173`, origen permitido por CORS:

```bash
PLAYWRIGHT_MODULE=/ruta/a/playwright/index.mjs node test/notificaciones.real.browser.mjs
```

**Crea datos de prueba**: un producto, una venta que cruza el umbral y un usuario
VENDEDOR; lee únicamente la alerta del producto creado. No se ejecuta con
`npm test` y no debe usarse contra producción. Usa `ADMIN_DEFAULT_PASSWORD`
(por omisión la clave local de desarrollo), `PRUEBA_VENDEDOR_PASSWORD`,
`BASE_URL`, `GATEWAY_URL`, `SALIDA`, `PLAYWRIGHT_MODULE` y las opciones de
Chromium anteriores. La repetición crea otro juego de datos, no es idempotente.
Solo reintenta el rechazo 400 exacto de producto aún no disponible en la réplica;
un POST incierto o cualquier otro rechazo detiene la prueba.

[Revisión con servicios reales y capturas](../../docs/aceptacion/cliente-concha-notificaciones-2026-11-11/RESULTADOS.md).

## Productos y formularios compartidos

ADMIN puede dar de alta, editar y dar de baja. VENDEDOR ve el catálogo y la
búsqueda, sin acciones ni diálogos de escritura. El filtro por nombre o
descripción es local, ignora acentos y mayúsculas y consulta solo los activos
ya cargados. No es una búsqueda paginada del servidor.

`CampoFormulario` asocia etiqueta, ayuda y error al control; las claves de
`campos` coinciden con los ids. `DialogoModal` usa el diálogo nativo, un título
accesible y bloqueo de cierre durante el envío. Una guarda síncrona en cada
mutación evita dos peticiones antes de que Vue actualice `disabled`.

El umbral vacío al crear viaja como null y el servicio aplica 5. Al editar es
obligatorio: null conservaría el anterior. El precio viaja como número JSON;
el formulario limita a dos decimales y doce enteros, sin notación científica.
**Es una restricción del cliente:** el DTO remoto acepta BigDecimal no negativo
sin esa restricción de escala. Un precio previo con más de dos decimales no se
redondea al abrir la edición, pero impide guardar hasta corregirlo expresamente.
El límite conserva el valor decimal al serializar; no supone que Number sea
aritmética decimal exacta. La Caja sigue admitiendo su escala original.

Las funciones de importe viven en `comun/importe.js` y siguen reexportadas por
`caja/cobro.js`. Al editar se envía también existencia: la API no ofrece versión
optimista para proteger cambios simultáneos con reservas de ventas. El listado
no se actualiza en tiempo real y cambiar existencias manualmente no publica
StockBajo; esa alerta se emite en la reserva de una venta.

Con la compilación servida por `npm run preview`, el recorrido simulado es:

```bash
PLAYWRIGHT_MODULE=/ruta/a/playwright/index.mjs node test/productos.browser.mjs
```

Prueba filtros, errores por campo, alta, edición, baja, permisos y dos envíos
en el mismo turno con una sola petición. El 400 es una respuesta ficticia para
verificar la presentación de `campos`; no prueba unicidad de nombres, que el
backend no exige. Admite las mismas variables de navegador que Notificaciones.

Contra Vite y el conjunto real:

```bash
PLAYWRIGHT_MODULE=/ruta/a/playwright/index.mjs node test/productos.real.browser.mjs
```

**Crea un producto y un VENDEDOR**, da de baja únicamente su producto y conserva
el usuario. No es idempotente ni debe ejecutarse en producción. Usa las mismas
variables y credenciales de prueba que el recorrido real de Notificaciones.
Comprueba el CRUD de la pantalla, el mapa remoto `campos` por API y los tres
rechazos de escritura con token real de VENDEDOR. No forma parte de `npm test`.

[Revisión y capturas de Productos](../../docs/aceptacion/cliente-productos-2026-11-11/RESULTADOS.md).

## Clientes y useAccion

Ambos roles consultan, crean y modifican clientes. Solo ADMIN ve y ejecuta la
eliminación. Esta es física: el listado deja de incluir al cliente y GET por id
responde 404. El backend no publica un evento de borrado; las réplicas de otros
servicios no se actualizan por esta operación. El diálogo advierte esta
consecuencia. No es la baja lógica de Productos.

Nombre es obligatorio; correo, teléfono y dirección son opcionales. Los textos
vacíos se envían como null, que es lo que el modelo almacena tras limpiarlos.
El correo se comprueba de forma básica, admitiendo partes locales entre
comillas como `"Ana Ruiz"@example.test`. No es un parser RFC completo: `@Email`
del servidor decide finalmente y sus errores se muestran junto al campo.

`useAccion` centraliza la guarda por instancia, la bandera visible, el error
general y el mapa `campos`. Un segundo envío pendiente devuelve
`{ ok: false, omitida: true }`. `fijarCampos` recibe la validación local y
`limpiar` borra mensajes sin liberar la operación. El ref cambia de inmediato;
solo la actualización del DOM espera al render. No hay reintento automático ni
idempotencia HTTP. Productos y Notificaciones conservan sus guardas anteriores;
la incorporación de este helper no las migra.

`comun/filtro.js` sirve a Productos y Clientes. Clientes filtra localmente por
nombre, correo y teléfono, sin dirección. La API sí ofrece búsqueda por nombre,
pero no se usa en esta pantalla para combinar esos campos y quitar acentos.

Después de compilar y arrancar preview:

```bash
PLAYWRIGHT_MODULE=/ruta/a/playwright/index.mjs node test/clientes.browser.mjs
```

Comprueba el doble envío, errores locales, opcionales, filtros y los botones de
cada rol. La ausencia de una fila se comprueba tras el cierre y la carga del
contenido final; una ausencia durante Cargando no demuestra que se eliminó.

Con Vite y los servicios reales, sin intercepciones:

```bash
PLAYWRIGHT_MODULE=/ruta/a/playwright/index.mjs node test/clientes.real.browser.mjs
```

Usa las mismas variables de navegador, ADMIN y vendedor de los recorridos
anteriores. **Crea un vendedor y dos clientes**; al terminar elimina solo esos
clientes con ADMIN y conserva el usuario. No es idempotente ni se ejecuta con
`npm test`. Solo entornos de prueba. Verifica permisos, CRUD, correo citado y un
400 real presentado en el campo, además de la ausencia final por listado y 404.

[Revisión y evidencia de Clientes](../../docs/aceptacion/cliente-clientes-2026-11-11/RESULTADOS.md).

## Reportes y descargas

La pantalla es de ADMIN. Consulta ventas confirmadas por día, mes o año, con
fechas inclusivas en la zona que devuelve el servicio. El rango inicial son
treinta días del calendario del navegador. Cambiar los controles no cambia los
datos ni la descripción de la gráfica hasta consultar; el resultado muestra sus
propias fechas. Las alturas SVG son aproximadas y los importes se formatean con
`comun/importe.js`. La misma serie está en una tabla para lectores de pantalla;
los periodos en cero conservan su posición.

Hay dos límites distintos: la API admite hasta 1.000 puntos de agregación y la
pantalla elige 370 como límite visual; el historial PDF admite hasta 366 días,
independientemente de la granularidad. El botón de historial explica y bloquea
rangos inválidos aunque la gráfica pueda consultarse por mes o año. El historial
usa las fechas actuales de los controles, indicadas junto al botón.

`gateway.descargar` usa fetch con el Bearer de memoria y entrega el Blob solo
cuando terminó de leerlo. Conserva el tratamiento de 401 y ProblemDetail; una
conexión interrumpida antes o después de las cabeceras se presenta como error de
red. La pantalla descarga mediante URL de objeto y la revoca diez segundos
después. El nombre se construye en el cliente: CORS del Gateway no expone
Content-Disposition. No cambian las cabeceras del servidor.

Un GET 404 de configuración permite registrar los primeros datos. Nombre y RFC
son obligatorios; el **servidor sí valida el formato del RFC** y normaliza a
mayúsculas. Sus errores aparecen en el campo correspondiente. Los máximos del
DTO son 120 para nombre, 200 para dirección y 30 para teléfono. La configuración
es única para la empresa, no por usuario.

Con build y preview, `test/reportes.browser.mjs` prueba la presentación con API
simulada, rangos, descarga, configuración ausente y navegación por historial
con sesión de vendedor. El PDF simulado prueba transporte, no validez documental.
Con Vite y servicios vivos, el recorrido independiente es:

```bash
PLAYWRIGHT_MODULE=/ruta/a/playwright/index.mjs node test/reportes.real.browser.mjs
```

Requiere también `pdftotext` en PATH (o `PDFTOTEXT_PATH`) y acepta las variables
de Gateway, navegador, salida y credenciales de los recorridos anteriores.
**Solo entornos de prueba:** crea producto, cliente, vendedor y dos ventas;
los conserva. Modifica la configuración y restaura sus campos anteriores si
existían; la fecha de actualización cambia. Si antes respondía 404, conserva la
empresa de prueba porque no hay DELETE. No es idempotente ni parte de npm test.
Verifica proyecciones nuevas, tres PDF legibles y permisos con token real.

Estos listados salen de proyecciones asíncronas de ms-reportes. No aseguran
actualización inmediata ni reconstruyen eventos anteriores a sus colas. La
pantalla incluye tres exportaciones y configuración. El ticket por venta vive
en Caja; no se incorpora un listado paginado nuevo en ms-ventas.

[Revisión, PDF y capturas](../../docs/aceptacion/cliente-reportes-2026-11-11/RESULTADOS.md).

## Usuarios y ticket en Caja

Usuarios lista únicamente cuentas activas. ADMIN puede crear y modificar otras
cuentas, cambiarles contraseña/rol y desactivarlas. No hay reactivación por API
ni en esta pantalla. No se ofrece desactivar la propia cuenta y su selector de
rol está bloqueado. `GET /auth/me` aporta el id estable para reconocerla incluso
si cambia el correo. Al editar el perfil propio se sincronizan nombre/correo
visibles, sin renovar el JWT ni alterar su rol.

El formulario exige los obligatorios y deja a ms-auth validar el correo y la
política de contraseña. Presenta `campos`, la lista `errores` de la política y
el 409 por correo duplicado junto al campo correspondiente. No recorta la
contraseña; al editar, vacía se envía como null para conservar la actual.
Se vacía el control al cancelar o guardar. Rol null conserva el actual.

**Cambiar rol o desactivar no revoca JWT ya emitidos.** La nueva autenticación
usa el rol actual y rechaza una cuenta inactiva; los tokens anteriores pueden
seguir sirviendo hasta vencer. La interfaz lo explica antes de desactivar y al
editar el rol. No se añade revocación ni se presenta el cierre de la sesión
ajena como una propiedad del sistema actual.

`TicketVenta` ofrece **Descargar ticket (PDF)** en el resultado confirmado y en
operaciones confirmadas por seguimiento. Usa `gateway.descargar`, con Bearer de
memoria, y el nombre `ticket-folio-N.pdf`. El PDF se guarda mediante el mismo
helper de Reportes. El doble clic se bloquea sin depender del render. Desmontar
el componente aborta la petición y descarta una respuesta tardía.

Si ms-reportes aún no proyectó la confirmación, su 404 `VENTA_NO_DISPONIBLE` se
muestra conservando la venta confirmada y permitiendo pedir el ticket de nuevo.
No hay reintento automático ni se vuelve a enviar el cobro. Pendientes y
canceladas no ofrecen ticket; 401 conserva la expiración común de sesión.

Después de compilar y arrancar preview:

```bash
PLAYWRIGHT_MODULE=/ruta/a/playwright/index.mjs node test/usuarios.browser.mjs
PLAYWRIGHT_MODULE=/ruta/a/playwright/index.mjs node test/ticket.browser.mjs
```

Aceptan las variables de navegador anteriores. `SALIDA` es prefijo de capturas
para Usuarios y directorio para Ticket. Ticket incluye 404/reintento manual,
doble clic, cancelada, seguimiento, desmontaje y 401 con API simulada.

Con Vite y todos los servicios vivos, sin intercepciones:

```bash
PLAYWRIGHT_MODULE=/ruta/a/playwright/index.mjs node test/usuarios-ticket.real.browser.mjs
```

Requiere `pdftotext` o `PDFTOTEXT_PATH`. **Solo prueba:** crea un ADMIN, un
VENDEDOR y un producto; registra dos ventas y descarga sus tickets desde ambos
roles. Modifica y desactiva solo sus dos cuentas de prueba; conserva producto y
ventas. No modifica la cuenta ADMIN inicial. No es idempotente ni parte de npm
test. Usa las variables habituales de navegador y credenciales de prueba, y
`PRUEBA_PASSWORD_NUEVA` para el cambio de contraseña. La evidencia guarda
estados y respuestas sin tokens ni contraseñas.

[Revisión, recorrido real y PDF](../../docs/aceptacion/cliente-usuarios-ticket-2026-11-11/RESULTADOS.md).

## Cliente empaquetado con nginx (5.6)

Desde `monpoint-ms/infra`, configurar `.env` a partir de `.env.example` y ejecutar:

```powershell
docker compose up -d --build
docker compose ps
```

Abrir **http://localhost:3000**. No se necesita Node ni Vite en la máquina anfitriona:
Docker compila con Node 22 (`npm ci` y `npm run build`) y la imagen final contiene
nginx y `dist/`. El puerto 5173 queda reservado para desarrollo.

| Variable en `infra/.env` | Valor local | Cuándo se aplica |
| --- | --- | --- |
| `VITE_GATEWAY_URL` | `http://localhost:8080` | Al compilar; después ejecutar `docker compose up -d --build cliente-web` |
| `CORS_ORIGENES` | `http://localhost:5173,http://localhost:3000` | Al recrear el Gateway; ejecutar `docker compose up -d api-gateway` |

Si ya existe `.env`, **añadir `http://localhost:3000` a CORS_ORIGENES**, conservando
los demás orígenes. Copiar el nuevo `.env.example` encima borraría la configuración
local. `docker compose restart` no actualiza variables del contenedor: usar `up -d`.

La URL del Gateway debe ser alcanzable por el **navegador**; `api-gateway:8080` es
un nombre interno de Docker y no sirve como URL pública. Para una demostración
por LAN, por ejemplo, configurar `VITE_GATEWAY_URL=http://192.168.1.20:8080` y añadir
`http://192.168.1.20:3000` a CORS_ORIGENES. Recompilar cliente y recrear Gateway.
No introducir claves ni tokens en variables `VITE_*`: se incluyen en el JavaScript.

Nginx sirve archivos estáticos, sin proxy de la API: el navegador sigue hablando
con el Gateway y su política CORS. `try_files` entrega `index.html` en `/login` y
`/caja`; recargar `/caja` pide iniciar sesión de nuevo porque el token vive en
memoria, pero no devuelve un 404 de nginx. Los assets con hash se cachean; el
HTML exige revalidación. Un asset inexistente devuelve 404, no HTML.

`/healthz` y el healthcheck prueban el servidor estático. La condición
`depends_on: service_healthy` espera al Gateway cuando Compose arranca el conjunto, para evitar
presentar un login antes de que la API esté lista. No vigila ni detiene nginx
cuando el Gateway se cae o reinicia después: el cliente ya iniciado sigue
sirviendo la interfaz y muestra los fallos de comunicación del backend. Con el
Gateway caído desde el principio, Compose sí retrasa el arranque del cliente.
El registro de los servicios en Eureka puede tardar unos segundos más. Un nginx
healthy no garantiza disponibilidad del backend.

Nginx entrega `X-Content-Type-Options: nosniff` en HTML, assets y errores. Las
ubicaciones con `add_header Cache-Control` repiten el encabezado porque nginx
1.28 no hereda los `add_header` del servidor si hay alguno declarado localmente.

Evidencia en nube del empaquetado y de los tres desenlaces desde nginx:
[aceptación 5.6](../../docs/aceptacion/5.6-nginx-2026-11-01/README.md).

### Comprobar antes de la demostración

[Guía completa para Windows y PowerShell](../../docs/aceptacion/GUIA-WINDOWS.md):
preparación, producto de prueba, tres desenlaces, recuperación y evidencia.

1. Con los nueve contenedores healthy, entrar en `http://localhost:3000/login`.
2. Iniciar sesión, cargar catálogo y clientes y realizar una venta de prueba.
   Verificar el folio y el total confirmado, y que no haya error CORS.
3. Abrir o recargar `http://localhost:3000/caja`: debe aparecer login y, al
   autenticarse, la caja. La sesión se pierde al recargar por diseño.
4. Confirmar que se sirve una compilación: no hay peticiones a `/@vite/client`
   ni a `/src/`, y `docker compose exec cliente-web nginx -t` pasa.

La verificación en Linux no sustituye este recorrido en Windows/Docker Desktop.

[Resultado del recorrido manual en Windows del 4 de noviembre](../../docs/aceptacion/windows-2026-11-04/RESULTADOS.md):
folios 44, 45 y 46, recuperación de inventario y existencia final 5, documentados
a partir de las capturas compartidas por el usuario.

Prueba automatizada de empaquetado contra los servicios reales, con Playwright
instalado como se indica más abajo:

```bash
PLAYWRIGHT_MODULE=/tmp/monpoint-browser/node_modules/playwright/index.mjs node test/empaquetado.browser.mjs
```

Acepta `CLIENTE_ORIGEN`, `GATEWAY_URL`, `CHROMIUM_PATH`, `ADMIN_CORREO` y
`ADMIN_DEFAULT_PASSWORD`. Verifica login y catálogo reales, CORS permitido y
rechazado, recarga de `/caja` y assets de producción. No crea ventas ni reemplaza
la aceptación de caja.

### Compilar detrás de un proxy HTTPS

El Dockerfile admite un secreto opcional BuildKit `proxy_ca` con el certificado
público de confianza del proxy. Se monta solo durante `npm ci` y activa
`NODE_EXTRA_CA_CERTS`; no se incluye en la imagen. Sin ese secreto usa la confianza
normal de Node. No desactivar la verificación TLS. La configuración de proxy y
el montaje del certificado corresponden al entorno de compilación.

## Caja (5.5)

La caja consulta `/inventario/productos` y `/clientes` mediante el Gateway. Permite
buscar productos, indicar cantidades enteras y elegir un cliente opcional. La existencia
mostrada es informativa: inventario decide la reserva al procesar la venta. El carrito
calcula un **estimado** mediante aritmética decimal exacta, redondeando solo la suma
a centavos (mitad hacia arriba); el resultado y los subtotales finales se
muestran tal como los entrega `GET /ventas/{id}`.

El cobro tiene seis estados: inactivo, enviando, enProceso, confirmada, cancelada y
sinRespuesta. La guarda sincrónica evita doble envío. Después del 202, consulta cada
800 ms tras completar la respuesta anterior, con un límite de reloj de 10 segundos
que también aborta una consulta colgada. Confirmar vacía el carrito; cancelar conserva
las líneas editables y muestra `motivo` literalmente. `productoIdRechazado` señala la
línea correspondiente, cuando está presente; su incorporación al backend es seguimiento
de 5.2. La caja funciona también con ventas históricas sin ese campo.

Al vencer el límite, el folio pasa a la bandeja y el carrito queda libre para otra venta.
«Consultar de nuevo» actualiza esa operación sin alterar el carrito actual. Un fallo de
red nunca se presenta como cancelación. Si se pierde la respuesta del POST (o tarda
más de 10 segundos), se registra un aviso sin folio: **no se reenvía automáticamente**.
No existe consulta por folio ni idempotencia HTTP; ese caso requiere verificar la
operación antes de cobrarla otra vez.

La bandeja y el carrito viven en Pinia durante la sesión. Al salir de la vista se abortan
peticiones y temporizadores; una operación activa pasa a la bandeja. Cerrar sesión limpia el carrito en el mismo objeto, conserva la bandeja y permite consultar
los folios después de volver a autenticarse. La máquina conserva también la identidad
del cobro en vuelo para archivarlo aunque el Gateway cierre la sesión antes de lanzar
el 401. Recargar pierde este estado en memoria y exige iniciar sesión de nuevo.
El navegador advierte antes de recargar si queda trabajo pendiente.

### Validación para revisión

```bash
npm test
npm run build
```

Las pruebas de la máquina usan un reloj controlado: verifican doble clic, importes,
conservación del carrito, ausencia de solapamiento, corte exacto, errores de red,
respuestas tardías, POST incierto y limpieza al desmontar.

Prueba de navegador con Chromium y **API simulada** (Vite debe estar activo):

```bash
npm run dev -- --host 127.0.0.1
# En otra terminal; las herramientas quedan fuera del checkout:
npm install --prefix /tmp/monpoint-browser --cache /tmp/monpoint-npm-cache --no-audit --no-fund playwright
PLAYWRIGHT_MODULE=/tmp/monpoint-browser/node_modules/playwright/index.mjs node test/caja.browser.mjs
```

`CHROMIUM_PATH` permite seleccionar el ejecutable (por omisión `/usr/bin/chromium`).
La prueba recorre login, selección de cliente/producto, cobro, los tres desenlaces y
cancelaciones históricas; comprueba doble clic, importes, edición, errores JS y ancho móvil.
No sustituye la aceptación contra los servicios reales: con el conjunto levantado,
registrar una venta con existencia suficiente, otra con cantidad mayor a la existencia
y otra con inventario detenido para comprobar el corte. Reiniciar inventario y consultar
el folio pendiente. La aceptación real de la 5.5 se conserva en `docs/aceptacion/` en la raíz del
repositorio, mediante su rama de documentación; la verificación completa de 6.1
sigue pendiente.


Seguimiento de revisión: el límite del POST es ahora 10 segundos, una decisión de
interfaz coherente con el sondeo, no una medición de latencia ni un SLA. Al vencer,
la operación sigue siendo incierta y no se reenvía. Se aceptan precios con más de dos
decimales: se conserva su precisión al multiplicar y sumar, y los importes del servidor
se muestran con su precisión original. Un dato malformado muestra «Importe no disponible»
y bloquea agregar/cobrar ese producto; nunca se sustituye por cero ni rompe el render.
Las regresiones incluyen 401 real del cliente HTTP → cierre Pinia → login → consulta
del mismo folio, además de precios de tres decimales y precios malformados.

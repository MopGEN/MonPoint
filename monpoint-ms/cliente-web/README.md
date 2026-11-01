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
├── App.vue            armazon: barra superior y RouterView
├── api/gateway.js     cliente HTTP unico: token, errores, expiracion
├── stores/sesion.js   sesion en Pinia (token EN MEMORIA)
├── router/index.js    rutas y guarda de sesion y rol
├── assets/base.css    variables del prototipo, reinicio y primitivas
└── vistas/
    ├── IniciarSesion.vue   minima y funcional; la 5.3 la completa
    ├── Caja.vue            catálogo, carrito y seguimiento del cobro (5.5)
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
| POST | `/ventas` | 202 con la venta **PENDIENTE**; no es confirmacion |
| GET | `/ventas/{id}` | detalle y estado de la venta — tarea 5.2 |

`expiraEn` es la **vigencia en milisegundos**, no una fecha. `stores/sesion.js`
la convierte a instante absoluto (`Date.now() + expiraEn`). Tratarla como fecha
de epoca da 1970 y la sesion nace caducada.

Los errores llegan como ProblemDetail (RFC 9457). `api/gateway.js` extrae
`detail` y lo entrega en `ErrorDeGateway.message`, ya redactado para mostrarse
al cajero; las vistas no deben reescribirlo.

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

`/healthz` y el healthcheck prueban el servidor estático. El cliente espera al
healthcheck del Gateway al arrancar; el registro de los servicios en Eureka puede
tardar unos segundos más. Un nginx healthy no garantiza disponibilidad del backend.

### Comprobar antes de la demostración

1. Con los nueve contenedores healthy, entrar en `http://localhost:3000/login`.
2. Iniciar sesión, cargar catálogo y clientes y realizar una venta de prueba.
   Verificar el folio y el total confirmado, y que no haya error CORS.
3. Abrir o recargar `http://localhost:3000/caja`: debe aparecer login y, al
   autenticarse, la caja. La sesión se pierde al recargar por diseño.
4. Confirmar que se sirve una compilación: no hay peticiones a `/@vite/client`
   ni a `/src/`, y `docker compose exec cliente-web nginx -t` pasa.

La verificación en Linux no sustituye este recorrido en Windows/Docker Desktop.

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

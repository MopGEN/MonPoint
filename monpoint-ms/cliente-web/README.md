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
    ├── Caja.vue            esqueleto con el contrato; la 5.5 la construye
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
| POST | `/auth/login` | `{correo, contrasena}` → `{token, tipo, correo, nombre, rol, expiraEn}` |
| POST | `/ventas` | 202 con la venta **PENDIENTE**; no es confirmacion |
| GET | `/ventas/{id}` | **no existe aun** — tarea 5.2, prerrequisito de la 5.5 |

`expiraEn` es la **vigencia en milisegundos**, no una fecha. `stores/sesion.js`
la convierte a instante absoluto (`Date.now() + expiraEn`). Tratarla como fecha
de epoca da 1970 y la sesion nace caducada.

Los errores llegan como ProblemDetail (RFC 9457). `api/gateway.js` extrae
`detail` y lo entrega en `ErrorDeGateway.message`, ya redactado para mostrarse
al cajero; las vistas no deben reescribirlo.

## Pendiente de la tarea 5.6 (empaquetado)

- `VITE_GATEWAY_URL` se sustituye **en tiempo de compilacion**, no se lee en
  ejecucion: cambiar la direccion del Gateway exige recompilar.
- Con `createWebHistory`, nginx necesita `try_files $uri $uri/ /index.html` o
  cualquier recarga fuera de `/` devolvera 404.
- El origen de produccion debe agregarse a `CORS_ORIGENES` del Gateway.

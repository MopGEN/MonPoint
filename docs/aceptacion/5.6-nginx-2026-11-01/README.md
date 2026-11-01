# 5.6 — cliente compilado servido por nginx

Implementación: `360baf11255b17c0648050ab1bd335a360e54e1c`.
Seguimiento del healthcheck: `3518475` (consulta por loopback sin proxy).
Pruebas en Linux de la nube el 1 de noviembre de 2026. La prueba en Windows
sigue pendiente; ejecutar el recorrido del README del cliente antes de la entrega.

## Criterios H-087 comprobados

- Dockerfile de dos etapas: Node 22 ejecuta `npm ci` y `vite build`; nginx sirve `dist/`.
- Compose incluye cliente-web en `:3000`, sin ejecutar Vite en el anfitrión.
- Login, catálogo y clientes reales desde Chromium a Gateway `:8080`, con CORS.
- Entrada directa y recarga de `/caja`: nginx devuelve 200; Vue pide login porque
  la sesión solo vive en memoria. `/login` y `/` también devuelven HTML válido.
- Un asset inexistente devuelve 404. HTML con revalidación y assets con hash.
- `VITE_GATEWAY_URL` se recibe como argumento de compilación. Una segunda imagen
  de prueba, sin desplegar, incorporó `http://gateway-ejemplo.invalid:8080` al JS.
  La imagen desplegada conserva `http://localhost:8080`. La imagen final no tiene
  Node, `/app` ni el certificado de compilación.

## Caja desde nginx, servicios reales

| Desenlace | Folio | Resultado |
| --- | --- | --- |
| Confirmada | 25 | $123.45 por tres unidades; existencia 10 → 7 |
| Cancelada | 26 | Cantidad 20, carrito conservado y línea señalada mediante productoIdRechazado; existencia 7 |
| Sin respuesta | 27 | Con inventario detenido, bandeja a los 10.328 segundos y caja libre; backend PENDIENTE |
| Recuperación | 27 | Tras reiniciar inventario, confirmada por $82.30; existencia final 5 |

Exactamente tres POST de cobro pese al doble clic. Máximo de una consulta de
venta simultánea. Cero errores HTTP y cero errores inesperados en el navegador.
No se interceptaron ni simularon respuestas de la API.

[Registro del recorrido](real-results.json), [script exacto](real-browser.mjs),
[captura confirmada](confirmada.png), [cancelada](cancelada.png),
[sin respuesta](sin-respuesta.png) y [recuperada](recuperada.png).
El script histórico usa las rutas del entorno original; para repetir la
comprobación de empaquetado, usar `test/empaquetado.browser.mjs` y las variables
documentadas en `monpoint-ms/cliente-web/README.md`.

## Incidencia de salud y comprobación final

El primer contenedor servía correctamente la aplicación pero figuraba unhealthy:
el wget de BusyBox enviaba incluso la consulta a 127.0.0.1 por el proxy heredado
del entorno y recibía un 403. El seguimiento limita `wget -Y off` al healthcheck
HTTP de loopback. Las descargas de la compilación siguen usando el proxy y TLS.
Se reconstruyó y volvió a levantar con Compose; se comprobó el estado healthy,
el registro de los servicios y se repitió la prueba de empaquetado sobre la imagen
final. La lógica de caja y nginx.conf no cambiaron en ese seguimiento.

- [Prueba de empaquetado final](empaquetado-final.json)
- [Estado de nueve contenedores y Eureka](readiness.json)
- [Imagen y revisión finales](imagen-final.json)

Las 14 pruebas unitarias del cliente pasaron, sin omisiones. `nginx -t` también
pasó. La compilación de nube empleó secretos BuildKit con la CA suministrada por
el entorno y overrides externos para los servicios Java; no se modificaron sus
Dockerfiles ni se desactivó TLS. No se borraron volúmenes.

Esta evidencia prepara la revisión de 5.6; no cierra 6.1. No cubre POST sin
respuesta, Windows, reportes/ticket ni arranque con volúmenes vacíos.

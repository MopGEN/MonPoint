# Seguimiento de revisión de 5.6 — 4 de noviembre de 2026

Revisión de código comprobada: `8ad3238`. Entorno: Linux de la nube.

## Encabezados y caché

La imagen se reconstruyó con Compose y se levantó sobre los datos existentes.
`nginx -t` pasó. Las consultas HTTP reales comprobaron `X-Content-Type-Options:
nosniff` en `/`, `/login`, `/caja`, `/index.html`, `/healthz`, un asset JavaScript
existente y `/assets/no-existe.js`. El último respondió 404; los demás, 200.
HTML conservó `Cache-Control: no-cache`; el asset compilado conservó `immutable`.

El encabezado se declara también en las ubicaciones con `add_header Cache-Control`:
nginx 1.28 deja de heredar los `add_header` del nivel superior cuando existe una
declaración local. Se usa `always` para cubrir respuestas de error.

La prueba `test/empaquetado.browser.mjs` incorpora estas aserciones. En este
seguimiento se ejecutaron comprobaciones HTTP sobre la imagen y validación de
configuración nginx; no se repitió el recorrido completo de ventas ni la prueba
Playwright de login. Los recorridos previos de Linux y Windows siguen documentados
por separado.

[Respuestas HTTP observadas](headers.json).

## Dependencia del Gateway

Se conserva `depends_on` con `service_healthy` para el arranque inicial de Compose.
Por tanto, si el Gateway no llega a healthy durante ese arranque, Compose espera
antes de iniciar el cliente. Es una decisión de arranque; no es supervisión
continua de disponibilidad ni implica que la API permanezca lista.

Comprobación real sobre un cliente ya iniciado: se detuvo Gateway, se verificó
que nginx respondía 200 en `/login`, `/caja` y `/healthz`, que seguía healthy y
que no cambió su instante de inicio. Después se restauró Gateway y se verificaron
los nueve contenedores healthy, registros Eureka UP y enrutamiento hacia auth.

[Gateway detenido y cliente disponible](gateway-stopped.json) ·
[Estado final restaurado](readiness-final.json).

## Integración de ramas

Se simularon localmente los dos órdenes con `git merge-tree` sobre `origin/main`
`0caf3e8`, sin actualizar ninguna rama de main ni publicar una fusión. Ambos
órdenes resultaron sin conflictos y produjeron el mismo árbol. Se comprobó que
hay un solo `docs/aceptacion/README.md` y que coincide con el aportado por la rama
`docs/6.1-evidencia-caja`; la rama 5.6 añade sus propios subdirectorios y guía.

[Revisiones y resultado de la simulación](merge-check.json).
La simulación usa la revisión de código indicada arriba; este directorio de
resultados se añade después y no existe en la rama de 6.1.

## Demostración y consola

La guía de Windows recomienda un perfil limpio o invitado sin extensiones.
El revisor identifica `bootstrap-autofill-overlay.js` como un script de Bitwarden;
confirmar su URL `chrome-extension://` en el equipo permite cerrar la atribución.
No se declara observada una URL que no se compartió. No se cambió código de Vue
para compensar modificaciones del DOM realizadas por extensiones.

Este seguimiento no marca actividades en Asana ni declara completada 6.1.

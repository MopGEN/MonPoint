# 3.1 — fase 2 de la semilla: ventas por API

Prueba ejecutada el **11 de noviembre de 2026**, a las 00:35 de
America/Mexico_City (06:35 UTC). Base `884aa00`, rama
`feat/3.1-semilla-ventas`. Los hashes de los guiones probados están en
`fuentes-probadas.json`.

Se levantó un proyecto Compose separado, `monpoint-semilla32`, con **volúmenes
nuevos de MongoDB y RabbitMQ**. No se borraron ni reutilizaron los volúmenes del
conjunto habitual. `arranque.txt` registra su creación; `volumenes.json` conserva
las fechas y nombres. Los diez contenedores de backend e infraestructura
quedaron sanos: los seis microservicios, Gateway, Eureka, MongoDB y RabbitMQ.
Al terminar se detuvo únicamente este proyecto aislado (`cierre.txt`), conservando
sus volúmenes. El conjunto habitual continuó funcionando.

Antes de sembrar se verificaron las ocho colas consumidoras con consumidores
activos, sin mensajes pendientes. Las APIs devolvieron productos, clientes y
notificaciones vacíos y una agregación de cero (`recorrido/base-nueva.json`).

## Resultado

Se ejecutaron las dos órdenes de Node por separado, a través del Gateway del
conjunto aislado:

```bash
node --env-file=infra/.env scripts/semilla.mjs
node --env-file=infra/.env scripts/semilla.mjs --ventas
```

La fase 1 creó VENDEDOR, dos clientes y tres productos; reutilizó el ADMIN
inicial. La fase 2 terminó en `COMPLETADA` con **tres ventas CONFIRMADAS**, que
después se consultaron individualmente por API:

| Folio | Cliente | Línea | Total del servidor | Existencia final |
|---|---|---|---|---|
| 1 | Semilla 3.1 - Ana | Tinta × 5 | 205.75 | 5 |
| 2 | Semilla 3.1 - Luis | Papel × 2 | 151.00 | 8 |
| 3 | Sin cliente | Sello × 1 | 120.00 | 9 |

Los ids del producto y del cliente proceden de la fase 1; no se reconstruyeron
ni se escribieron directamente en MongoDB. Las consultas de las ventas
confirmaron también los nombres de Ana y Luis.

**Reportes sí se ejercitó en ejecución:**
`GET /reportes/ventas/agregado?periodo=DIA&desde=2026-11-11&hasta=2026-11-11`
pasó de total 0 a **476.75**, con un punto del día por el mismo importe
(`recorrido/agregado.json`). Es la suma 205.75 + 151.00 + 120.00. Se observa la
proyección de las ventas en la agregación, además de verificar la topología.

**Notificaciones recibió el StockBajo real:** un aviso no leído, activo,
con producto Tinta y existencia 5 (`recorrido/notificaciones.json`). No se
publicó ningún mensaje sintético desde la semilla ni desde este guion de prueba.

Una segunda ejecución de `--ventas` terminó con código 1 por existencia inicial
distinta de 10, con cero ventas nuevas en el resumen y sin venta en curso.
La fase 1 volvió a ejecutarse: reutilizó los ids y conservó existencias 5, 8 y 9.
La agregación siguió en 476.75. Esto comprueba la guarda para una repetición
normal; **no convierte en idempotente la fase 2**.

## Pruebas locales de fallos

**38 pruebas pasan, cero fallos y cero omisiones** (`pruebas.txt`): 14 de la
fase 1 y 24 de la fase 2. Usan servidores HTTP locales, distintos del conjunto
real. Comprueban:

- Rechazos 400 de producto y cliente: se reenvía la misma solicitud y solo
  esos mensajes exactos permiten repetir el POST.
- Otro 400, un POST sin respuesta, timeout, 5xx o 202 incompleto: se detiene
  sin repetirlo ni continuar con otra venta; conserva los folios ya confirmados.
- Un 202 PENDIENTE no se cuenta como éxito. CANCELADA interrumpe el lote.
- GET transitorio: se consulta otra vez sin reenviar el POST; otros errores
  conservan la identidad de la operación pendiente.
- El plazo de una venta se comparte entre espera de réplicas y saga; el 202
  no inicia otro plazo. Se prueba también un GET colgado después de esperar réplica.
- Datos ausentes, ambiguos o con stock consumido se detectan antes de vender.
- La CLI conserva el resumen parcial y no imprime contraseñas, tokens ni
  cuerpos arbitrarios de error.

En el conjunto real las réplicas ya estaban disponibles: `rechazosReplica`
registró cero para ambos tipos. El recorrido no se presenta como prueba en vivo
de retrasos, cancelaciones o POST inciertos; esos caminos se cubrieron localmente.

## Archivos y reproducción

`verificar-real.mjs` es el guion exacto ejecutado. Requiere Node 22.9 o posterior,
un conjunto **nuevo** con los seis servicios sanos y las credenciales en el
entorno. La semilla solo usa el Gateway; este verificador también consulta la
API de administración de RabbitMQ para observar colas, sin publicar mensajes.
Desde la raíz del repositorio, con un directorio de salida que aún no exista:

```powershell
$env:SEMILLA_GATEWAY_URL = 'http://localhost:18081'
$env:SEMILLA_RABBIT_API_URL = 'http://localhost:15673/api'
node --env-file=monpoint-ms/infra/.env docs/aceptacion/3.1-fase2-2026-11-11/verificar-real.mjs evidencia-semilla-nueva
```

Adaptar los puertos al conjunto preparado. Las variables de la terminal tienen
prioridad sobre `.env`. Se requieren las credenciales de administrador y del
vendedor de la fase 1, y las de RabbitMQ para el verificador. No se archivaron
tokens, contraseñas del entorno ni cabeceras Authorization. Los valores por
omisión del guion son las credenciales de desarrollo ya publicadas en el proyecto.

`recorrido/` contiene las salidas de la CLI y las consultas independientes.
`SHA256.json` permite verificar los archivos de evidencia, excluyéndose a sí
mismo. No se incluyen binarios compilados ni archivos `.env`.

## Alcance y límites

- Es aceptación de la fase 2 sobre datos nuevos, separada de la **6.1**. No
  ejecuta sus siete escenarios ni el navegador y no acredita CONTROL 3.
- Las confirmaciones de venta no garantizan por sí solas la entrega de cada
  evento: en este recorrido se comprobaron agregación y alerta explícitamente.
  No se introdujo outbox ni recuperación de eventos perdidos.
- La API de ventas sigue sin clave de idempotencia, referencia externa ni
  listado. Un POST incierto sin id exige revisión manual. No hay reanudación
  automática ni archivo local que prometa reconocerlo después.
- Los JAR de los ocho servicios Java se compilaron desde esta base con JDK 21
  y Maven 3.9.16. En la nube se empaquetaron sobre un JRE local existente, como
  muestra `Dockerfile.nube`; se usó Compose con un override de nombres, puertos
  e imágenes para aislarlos. No se acredita aquí la compilación multietapa desde
  una caché Docker vacía. El cliente web no formó parte de esta prueba de backend.
- No se modificó Asana ni se fusionó esta rama: queda para revisión.

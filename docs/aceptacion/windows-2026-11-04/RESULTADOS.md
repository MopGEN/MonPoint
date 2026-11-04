# Recorrido manual en Windows — 4 de noviembre de 2026

**Resultado del recorrido de caja y nginx: satisfactorio según las capturas
compartidas por el usuario.**

El usuario ejecutó los comandos y operó el navegador en su equipo. Este registro
transcribe lo observado en las capturas del chat; no corresponde a una ejecución
remota del asistente en Windows. Los archivos originales de esas capturas y las
respuestas JSON locales no están incorporados en este directorio.

## Entorno y preparación

- Proyecto: `C:\Users\osber\MonPoint`.
- Revisión mostrada antes de compilar: `a722f25`, rama `infra/5.6-cliente-nginx`.
- Windows PowerShell, Docker Desktop y Chrome.
- Cliente nginx: `http://localhost:3000/caja`. Gateway: `http://localhost:8080`.
- CORS_ORIGENES incluye `http://localhost:5173,http://localhost:3000`.
- Los nueve contenedores alcanzaron healthy antes del recorrido.
- `/healthz` devolvió `ok`; `docker compose exec cliente-web nginx -t` pasó.
- Login, productos y clientes respondieron 200 desde el navegador.
- La recarga de `/caja` volvió a login sin mostrar un 404 de nginx.
- Se desactivó Slow 4G; las pruebas de venta muestran No throttling.

Producto exclusivo de prueba: `Aceptacion Windows 20261104-133421`.
Precio: **41.15**; existencia inicial: **10**; umbral: **0**.

## Tres desenlaces y recuperación

| Caso | Folio | Evidencia visible | Existencia consultada por API |
| --- | --- | --- | --- |
| Confirmada | 44 | Tres unidades, total cobrado $123.45, carrito vacío; POST 202 seguido de GET 200 | 7 |
| Cancelada | 45 | Cantidad 20 conservada y editable, motivo de existencia insuficiente en español y producto señalado en la línea | 7, comprobado nuevamente antes de detener inventario |
| Sin desenlace dentro del plazo | 46 | Inventario detenido antes del cobro; POST 202, sondeos GET 200, folio en Seguimiento de operaciones, aviso de 10 segundos y carrito vacío | No se consultó inventario mientras estaba detenido |
| Recuperación | 46 | Tras iniciar inventario y consultar la operación, mismo folio confirmado por $82.30 | 5 |

Las cantidades e importes son consistentes: 3 × 41.15 = 123.45 y existencia
10 → 7; la cancelación no descontó; 2 × 41.15 = 82.30 y existencia 7 → 5.

Se observó un POST por operación en los registros de Network compartidos. No se
hizo una medición automatizada de solapamiento ni de milisegundos en Windows.
El aviso de diez segundos se acreditó visualmente. No se aportó la respuesta JSON
PENDIENTE del folio 46 antes de restaurar inventario; el resultado sin confirmar
en la bandeja y su recuperación sí quedaron visibles.

## Estado final

La consulta de producto por Gateway devolvió existencia **5**. La última salida de
`docker compose ps` mostró healthy en los nueve contenedores:

- monpoint-auth, monpoint-cliente, monpoint-clientes.
- monpoint-eureka, monpoint-gateway, monpoint-inventario.
- monpoint-mongodb, monpoint-rabbitmq, monpoint-ventas.

Inventario se restauró y la venta pendiente se resolvió. El recorrido no requiere
repetir ningún cobro ni reinicializar los datos.

## Observación de consola y alcance

Se compartió un error `NotFoundError` de `insertBefore` cuyo origen visible es
`bootstrap-autofill-overlay.js`. El nombre sugiere un componente de autocompletado,
pero no se compartió su URL completa y no se confirmó si pertenece a una extensión.
Se conserva esta observación; este informe no afirma que la consola esté libre de
errores. Para atribuirlo, comprobar la URL del enlace en Console: un origen
`chrome-extension://` identifica una extensión.

Queda acreditado el recorrido manual solicitado en Windows del cliente nginx y
los tres desenlaces de caja. La aceptación formal y el tablero corresponden al
usuario. Este informe no cierra 6.1: no incluye POST sin respuesta/id/folio,
reportes/ticket, notificaciones, reentrega de eventos ni arranque con datos vacíos.

[Guía utilizada](../GUIA-WINDOWS.md) ·
[Evidencia automatizada previa en Linux](../5.6-nginx-2026-11-01/README.md)

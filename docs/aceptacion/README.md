# Evidencia de aceptación

## Recorrido 6.1 — siete escenarios en una sola ejecución

[Recorrido manual en Windows del 11 de noviembre](6.1-windows-2026-11-11/RESULTADOS.md),
sobre volúmenes destruidos y reconstruidos, con `main` en `1244393`. Cubre los
siete escenarios y la reentrega de CONTROL 2 en una única ejecución declarada: venta
confirmada desde la caja, descuento, agregación, rechazo sin descuento, alerta
bajo umbral, reentrega descartada por `eventoId` y 403 por rol. Incluye las
incidencias del recorrido y los límites de lo que acredita. La recolección de
instantáneas finales y del manifiesto en Windows sigue pendiente; no acredita
por sí solo todos los criterios de CONTROL 3.

## Semilla 3.1 — fase 2, para revisión

[Tres ventas confirmadas sobre volúmenes nuevos](3.1-fase2-2026-11-11/RESULTADOS.md),
con clientes, agregación real de reportes por 476.75 y aviso de tinta en el umbral.
Incluye las pruebas de fallos y el resumen parcial. Es una prueba de la semilla,
separada del recorrido de siete escenarios de 6.1 y de CONTROL 3.

## Reentrega de VentaCreada — criterio de CONTROL 2

[Recorrido por la consola de RabbitMQ del 5 de noviembre](6.1-reentrega-2026-11-05/RESULTADOS.md):
mensaje original republicado, stock 10 → 9 → 9, descarte por eventoId y limpieza
de las colas de captura. Incluye el método de automatización y sus límites;
aporta evidencia a la 6.1, que sigue parcial.

## Notificaciones 5.4 — para revisión

[Cinco criterios por Gateway y RabbitMQ reales](5.4-2026-11-10/RESULTADOS.md).
Incluye concurrencia con MongoDB, lectura, reentrega y rechazo a la cola de
fallidos. Documenta las decisiones de deduplicación y la configuración efectiva
de reintentos en Spring Boot 4.1.1; no declara cerrada la 6.1.

## Semilla 3.1 — fase 1

[Carga por API sobre una base nueva y repetición sin altas](3.1-fase1-2026-11-04/RESULTADOS.md).
Incluye usuarios, clientes y productos. Las ventas y sus consumidores quedan
pendientes; esta evidencia no declara cerrada toda la 3.1 ni CONTROL 1.
La [revisión posterior](3.1-fase1-2026-11-04/REVISION.md) documenta la contraseña
vacía y la comparación decimal, sin reemplazar la evidencia original.

## Caja 5.5 — aceptada

[Informe y capturas del 1 de noviembre de 2026](5.5-2026-11-01/RESULTADOS.md).
La revisión probada es `0caf3e8417860296155e5fc895d4a3ee1992a0fd` (PR #12 y #13).
Los folios del recorrido aprobado son 22, 23 y 24. El usuario confirmó el cierre
de 5.5; estos resultados aportan evidencia parcial a 6.1, sin declarar su cierre.

Los archivos del recorrido se conservaron byte a byte, incluido el primer
recorrido que falló por la aserción de consola. `SHA256.json` permite comprobarlo.
`real-browser.mjs` es el guion exacto ejecutado: sus rutas de herramientas y
salida corresponden a la sesión Linux original. Es un registro histórico; para
repetir manualmente en otra máquina usar el [guion](GUION.md). No contiene tokens
ni contraseñas del entorno; la contraseña que aparece es la del usuario de
prueba por defecto del proyecto y puede reemplazarse mediante variable.

## Pendientes de verificación

- POST que no devuelve respuesta: revisar en pantalla el aviso sin folio y la
  ausencia de reenvío. Las pruebas automatizadas lo cubren; este recorrido real
  detuvo inventario antes del cobro, pero el backend sí respondió al POST con
  202 y folio. Por eso ejercitó el corte del sondeo, no el POST colgado.
- ~~Recorrido en Windows con Docker Desktop y navegador local antes de la
  entrega.~~ Ejecutado el 11 de noviembre; el arranque con volúmenes nuevos fue
  declarado por el operador, sin conservar la salida de su destrucción.
- Recorrido de caja con sesión de **VENDEDOR**: el del 11 de noviembre se hizo
  con sesión de ADMIN. El rol se ejercitó por API, no desde la pantalla.
- Cuerpo del `ProblemDetail` en un 403 por rol: solo quedó registrado el código.
- Segunda alerta de stock bajo del mismo producto, para observar el índice
  parcial de ms-notificaciones actualizando la notificación activa en lugar de
  crear otra.
- Para 6.1, contrastar el alcance vigente del tablero con H-094: ticket y
  gráfica de historial siguen sin evidencia en pantalla. La notificación y la
  reentrega sí tienen evidencia; los límites sobre el arranque limpio y el
  estado visual En proceso se detallan en el informe del 11 de noviembre.

El avance y las fechas se mantienen en Asana; este directorio conserva pruebas,
no replica el tablero.

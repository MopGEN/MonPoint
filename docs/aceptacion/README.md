# Evidencia de aceptación

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
- Recorrido en Windows con Docker Desktop y navegador local antes de la entrega.
- Para 6.1, contrastar el alcance vigente del tablero con H-094: ticket,
  historial/gráfica, notificación bajo umbral, reentrega de VentaCreada sin doble
  descuento y arranque desde cero con el cliente empaquetado. Estos elementos
  no se acreditan con el recorrido de 5.5. Los volúmenes usados ya tenían datos.

El avance y las fechas se mantienen en Asana; este directorio conserva pruebas,
no replica el tablero.

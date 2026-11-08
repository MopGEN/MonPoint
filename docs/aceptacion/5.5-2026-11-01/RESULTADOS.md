# Aceptación real de 5.5 — MonPoint

Resultado: **PASS**. Ejecución: 2026-11-01T22:16:38.784Z a 2026-11-01T22:17:00.751Z (UTC).

Revisión probada: `main`, `0caf3e8417860296155e5fc895d4a3ee1992a0fd`, incluye PR #12 y PR #13.
Entorno: Linux de esta sesión en la nube; no se accedió al equipo Windows del usuario.
Chromium ejecutó la interfaz Vue en `http://localhost:5173` contra el Gateway real
`http://localhost:8080`, MongoDB, RabbitMQ y los microservicios. Sin API simulada.

| Desenlace | Folio | Tiempo observado desde el 202 |
| --- | --- | --- |
| CONFIRMADA | 22 | 1.316 s |
| CANCELADA | 23 | 1.319 s |
| SIN_RESPUESTA | 24 | 10.329 s |

- Confirmada: pasó por «Venta en proceso» con folio; mostró $123.45 del servidor; vació el carrito. Existencias: 10 → 7.
- Cancelada: cantidad 20 frente a existencia 7; conservó el carrito editable, mostró el motivo en español y resaltó la línea cuyo productoId coincide con productoIdRechazado del backend. Existencias sin cambio.
- Sin respuesta: se detuvo ms-inventario antes del cobro. A los 10.329 segundos quedó el folio 24 en seguimiento y la caja disponible. El backend seguía PENDIENTE.
- Recuperación: tras reiniciar ms-inventario, «Consultar de nuevo» mostró el folio 24 confirmado y $82.30. Existencias finales: 5.
- Doble clic en cada cobro: exactamente 3 POST /ventas para los tres casos.
- Máximo de consultas GET /ventas/:id simultáneas observado: 1.
- CORS: respuestas del Gateway con Access-Control-Allow-Origin: http://localhost:5173, consumidas por el navegador.
- Errores inesperados de JavaScript/consola: 0.

## Evidencia

- [Registro completo JSON](real-results.json)
- [Script ejecutado](real-browser.mjs)
- [Confirmada](confirmada.png)
- [Cancelada y producto señalado](cancelada.png)
- [Sin respuesta y folio conservado](sin-respuesta.png)
- [Recuperación desde la bandeja](recuperada.png)
- [Estado final de contenedores](estado-final.txt)
- [Verificación de salud y registro Eureka](readiness.json)

## Incidencias de la comprobación

El primer recorrido completó los tres casos y la recuperación, pero el script
falló en una aserción demasiado amplia que exigía cero errores HTTP incluso
al consultar inventario durante su reinicio. Se conservaron sus resultados y
capturas en `primer-recorrido/`.

Se corrigió exclusivamente el script externo de aceptación: registra todos los
errores y tolera únicamente el 404 de `/favicon.ico` y el 503 de la consulta del
producto de prueba durante la recuperación deliberada. Los demás errores siguen
fallando la prueba. La repetición completa terminó con PASS.
Respuestas HTTP de error registradas en la repetición: {}.

## Estado del entorno y alcance

Los ocho contenedores quedaron saludables y ms-inventario se restauró.
La compilación Docker empleó archivos externos de adaptación al proxy/CA y
permisos de este entorno, sin modificar el código del repositorio ni desactivar
TLS. Los datos y folios de aceptación se conservan como evidencia.

Esta comprobación satisface los tres desenlaces solicitados para 5.5. No se
actualizó Asana. El repositorio permanece en main sin cambios locales; no se
publicó una nueva configuración del entorno.

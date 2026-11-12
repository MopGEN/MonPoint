# F12 físico en Windows: folio 7

Ejecución manual del usuario el 12 de noviembre de 2026, guiada en el chat.
Se conserva el JSON original y los cinco PNG extraídos del RAR sin editar ni
reescalar. El resultado observado es: F12 inició el cobro, Caja mostró el folio
7 pasando de «En proceso» a «Confirmada», por una unidad de Sello y $120.00;
el usuario declaró que DevTools no se abrió.

## Entorno y revisión

- Windows; el user agent declara Windows NT 10.0, que no distingue Windows 10
  de Windows 11. Chrome 157.0.0.0 según ese mismo registro; no se capturó una
  versión más detallada del navegador.
- Cliente en `http://localhost:5173` según el recorrido guiado, con Vite y el
  backend local levantado en Docker. No es una prueba del cliente nginx en 3000.
- Rama del usuario `feat/5.5-caja-visual`, con revisión `0a7f48e` según su salida
  de git log tras aplicar los tres parches. En el clon que prepara este acta,
  los mismos parches terminan en `bdde228`. No se recibió un hash de árbol del
  clon de Windows para contrastarlo de forma independiente.
- La cabecera muestra la sesión «Administrador». No se conservan contraseñas
  ni tokens en este expediente.

## Método y recorrido

1. Con el carrito vacío, el usuario abrió la Consola con Ctrl+Mayús+J e instaló
   el observador visible en [01](capturas/01-observador-preparado.png). Escucha
   solo keydown de F12 mediante addEventListener con passive=true: no genera
   eventos de teclado ni llama a preventDefault. Registra isTrusted,
   cancelable, defaultPrevented, repeat y modificadores; vuelve a leer
   defaultPrevented en una microtarea.
2. Cerró DevTools, eligió una unidad de «Semilla 3.1 - Sello» y escribió «tinta»
   en el buscador sin Enter. [02](capturas/02-carrito-antes.png) muestra una
   línea de Sello, cantidad 1, total estimado $120.00 y Cobrar habilitado,
   mientras el catálogo filtrado ofrece Tinta.
3. Conforme a su ejecución del recorrido, pulsó F12 físicamente una vez.
   [03](capturas/03-en-proceso.png) muestra «Venta en proceso», folio 7, con
   controles de escritura deshabilitados y el paso 2 activo.
4. [04](capturas/04-confirmada.png) muestra «Venta confirmada», folio 7, total
   cobrado $120.00, una unidad de Sello y el paso 3 activo. La línea mostrada no
   incluye Tinta. Es evidencia de pantalla, no una captura del cuerpo del POST.
5. El usuario declaró literalmente: **«DevTools no se abrio.»** Después recuperó
   el registro por Consola, visible en [05](capturas/05-registro-f12.png), y
   entregó [f12-windows.json](f12-windows.json). Tras copiar el JSON retiró el
   observador; esta limpieza fue compartida en otra captura del chat que no
   está incluida en el RAR recibido.

## Fuentes de cada conclusión

| Observación | Fuente | Alcance |
|---|---|---|
| Un F12 registrado, isTrusted=true | JSON y captura 05 | Evento confiable recibido por la página; la pulsación física la reporta el usuario |
| cancelable=true y defaultPrevented=true | JSON y captura 05 | Estado del evento; por sí solo no determina qué hizo la interfaz de Chrome |
| Sin repetición ni Ctrl/Alt/Mayús/Meta | JSON y captura 05 | Todos esos indicadores son false |
| DevTools no se abrió con F12 | Declaración manual del usuario | Observación separada del registro del evento |
| En proceso → Confirmada, mismo folio 7 | Capturas 03 y 04 | Transición visible en esta ejecución, sin inferir el cuerpo de respuestas HTTP |
| Sello × 1, total final $120.00 | Capturas 02 y 04 | Resultado mostrado en Caja |

El campo fecha del JSON es `2026-11-12T08:49:12.456Z`, equivalente a las
02:49:12.456 de Ciudad de México: corresponde a la **preparación del observador**,
no al instante de F12 ni al de confirmación. Los nombres de los PNG contienen
horas de captura, que no se usan como cronómetro de la saga.

El campo foco es una cadena vacía. La captura previa muestra el buscador
enfocado, pero el registro no identifica el elemento enfocado en el instante de
F12. No se usa esa cadena vacía para afirmar que se conservó el foco exacto ni
como otra prueba del atajo Ctrl+Enter.

## Resultado y límites

La prueba aporta evidencia manual de que **en esta sesión de Windows/Chrome 157,
F12 cobró y DevTools permaneció cerrado**, según la observación del usuario.
También aporta la transición visible En proceso → Confirmada que faltaba en la
evidencia anterior de la Caja. No modifica lo que se observó o dejó de observar
en la ejecución del 11 de noviembre ni marca hitos en Asana.

No se generaliza a otros navegadores, perfiles, teclados o configuraciones.
No se hizo un control físico de F12 con el manejador de la aplicación retirado;
por tanto no se atribuye causalidad exclusiva a preventDefault sobre los
aceleradores de Chrome. No se capturaron el cuerpo o número de peticiones
HTTP, las existencias finales, un PDF del folio 7 ni su persistencia tras
reinicio. Un evento registrado y una venta visible no prueban por sí solos la
ausencia de otros POST. No se repitió la venta para ampliar esta evidencia.

## Archivos e integridad

- [RAR original](entrada/prueba-f12.rar): contiene los cinco PNG recibidos.
- [JSON original](f12-windows.json): 448 bytes, UTF-8 sin BOM; idéntico al archivo
  que el usuario había entregado antes de enviar el RAR. Conserva sus CRLF;
  se exporta como binario en los parches Git para mantener esos bytes.
- [procedencia.json](procedencia.json): nombres originales dentro del RAR,
  nombres de destino, dimensiones, tamaños y SHA-256. Renombrar las copias no
  cambió sus bytes.
- El manifiesto [SHA256.json](../SHA256.json) de Caja incluye este subdirectorio
  y se verificó contra los blobs preparados para Git. No hay un segundo
  manifiesto parcial que pueda omitir capturas de este mismo expediente.

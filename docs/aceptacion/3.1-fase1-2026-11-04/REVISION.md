# Seguimiento de la revisión de fase 1

Se mantiene la evidencia original de `666a5d1`: los archivos del recorrido real
no se sobrescriben. En `SHA256.json`, las entradas que apuntan al guion y sus
pruebas corresponden a ese commit, no a la versión posterior de esos archivos.
Para contrastarlas se obtiene su contenido con `git show 666a5d1:<ruta>`.
`REVISION-SHA256.json` identifica el código y las pruebas de esta corrección.

## Cambios

- `ADMIN_DEFAULT_PASSWORD` ausente o vacía usa `Admin123!` en Compose (`:-`),
  DataInitializer y el guion. Las claves personalizadas no se recortan ni se
  sustituyen. Una base con usuarios conserva sus contraseñas existentes.
- La coincidencia del precio acepta números JSON y cadenas decimales, con
  ceros finales o notación exponencial. Normaliza coeficiente y exponente sin
  convertir cadenas a doble ni redondearlas. `41.15` y `"41.1500"` coinciden;
  `"41.150000000000000001"` sigue siendo un conflicto.
- Se precisa que incluso una base nueva no garantiza la entrega del catálogo:
  los publicadores no tienen outbox. La fase 1 no acredita réplicas ni las repara.

## Validación

- `node --test monpoint-ms/scripts/semilla.test.mjs`: **14 pasan**, sin omisiones.
  Incluye los casos de contraseña ausente/vacía, precios representados como texto
  y diferencias que una conversión a doble ocultaría.
- Suite completa de `ms-auth`: **72 pasan**, sin fallos, errores ni omisiones.
  Incluye cuatro pruebas nuevas con contexto Spring: variable ausente, vacía,
  personalizada y base con usuarios. Se comprueba el hash BCrypt guardado y que
  no autentica una contraseña vacía. Las pruebas de contexto e integración del
  servicio también pasaron contra MongoDB local.
- Configuración Compose renderizada para los tres casos de ADMIN_DEFAULT_PASSWORD:
  ausente, vacía y personalizada. Se compararon los valores sin imprimir claves.
- `git diff --check`: correcto.

Se usó el JDK 21 completo del workspace para Maven; el Java 21 del sistema es
solo un runtime y el primer intento de compilación no encontró el compilador
adecuado. No se modificaron las versiones ni las dependencias del proyecto.

Esta revisión no repite el recorrido de contenedores con base nueva documentado
en `RESULTADOS.md`, ni afirma que esas capturas de resultados procedan del código
corregido. La fase 2 y la reentrega de RabbitMQ siguen pendientes; no se declara
cerrada la actividad 3.1 ni ningún control del equipo.

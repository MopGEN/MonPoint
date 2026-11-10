# 5.4 — aceptación de ms-notificaciones

Fecha: 10 de noviembre de 2026. Rama `feat/5.4-ms-notificaciones`, desde
`61115b5`, actualizada sobre `79d0f89` (PR #19, solo documentación). Servicio nuevo con puerto interno 8086. Pruebas ejecutadas en Linux
de la nube, contra MongoDB replica set, RabbitMQ y Gateway reales.

## Resultado de los cinco criterios

| Criterio | Comprobación observada | Evidencia |
| --- | --- | --- |
| StockBajo genera aviso no leído con producto, existencia y fecha | Venta real de 5 unidades; existencia 10 → 5, aviso activo no leído | `recorrido-final/01-no-leida.json` y `resultado.json` |
| Avisos del mismo producto no se duplican | Otra venta de 1 unidad actualiza a 4 el mismo id y conserva fechaCreacion | `recorrido-final/02-actualizada.json` |
| Lectura 204; inexistente 404 | Dos PUT devuelven 204 con fecha estable; id ausente devuelve 404; filtros true/false contrastados | `recorrido-final/03-leida.json`, `04-inexistente.json`, `aceptacion.mjs` |
| Solo ADMIN | GET y PUT con VENDEDOR: 403; sin token: 401, por el Gateway | `recorrido-final/05-permisos.json` |
| Reentrega sin efecto adicional | Se republicó el payload capturado con el mismo message_id; la alerta leída quedó idéntica y el consumidor registró DESCARTADO | `recorrido-final/payload-original.json`, `06-tras-reentrega.json`, `consumidor.txt` |

Folios del recorrido final: **32, 33 y 34**. Importes: 205.75, 41.15 y 41.15.
Existencia final consultada por API: **3**. Producto de prueba:
`6af34e1057cdec548cbc93a0`. Evento reentregado:
`cb5e8fc7-7e92-4ac1-b63f-7d7734e7c7f3`.

Después de leer el aviso, la tercera venta creó un **nuevo** aviso activo con
existencia 3 y conservó el anterior inactivo. Esa es la decisión explícita de
5.4: leer significa cerrar el aviso, no confirmar reposición de existencias.

La captura se hizo con una cola temporal enlazada antes de vender. Se publicó
el payload original, sin reconstruirlo. Se eliminó esa cola al terminar; no se
purgaron colas de negocio. Quedan los productos y ventas de prueba como rastro.

## Pruebas automatizadas y fallo controlado

**27 pruebas, cero fallos, cero errores y cero omisiones**: 14 casos de contrato
y 13 de integración/configuración. `pruebas.json` recoge nombres y resultados
de Surefire. La suite con MongoDB verifica, entre otros:

- Doce eventos distintos concurrentes: un aviso activo y doce marcadores.
- Doce entregas concurrentes del mismo evento: un aviso y un marcador.
- Lectura concurrente con un evento: el evento queda aplicado, sin dos activas.
- Fallo inducido al escribir el aviso: se revierte también el marcador; la
  reentrega posterior puede aplicarse.
- Índice parcial efectivo y rechazo de una segunda activa; varios históricos
  inactivos permitidos. `persistencia.json` conserva los índices reales.
- Lectura repetida, filtros, roles, 404 y eventos entregados fuera de orden.

Se publicó además un mensaje **intencionalmente inválido**, con existencia -1.
Llegó a `notificaciones.stock-bajo.fallidos` con `x-death.reason=rejected` y el
payload intacto. No dejó marcador ni aviso. `fallidos.json` y `persistencia.json`
lo documentan. Se retiró únicamente el mensaje de prueba, después de comprobar
que no había fallidos preexistentes; no se purgó la cola.

## Corrección de la premisa sobre reintentos

El JAR instalado de **Spring Boot 4.1.1** declara `max-retries` como propiedad
vigente. `max-attempts` figura retirado desde 4.0.0, con reemplazo `max-retries`.
`metadatos-reintentos.json` conserva esa extracción y el SHA-256 del artefacto.
Por tanto, la afirmación anterior de que `max-retries=2` en ms-reportes era una
propiedad inexistente **no aplica a la versión de este repositorio**.

En notificaciones se usa `max-retries=2`: entrega inicial más dos reintentos.
La prueba `configuracionRealDeBootEntregaTresVecesYRechazaSinReencolar` usa el
interceptor creado por la autoconfiguración real, cuenta **tres invocaciones**
y comprueba el rechazo sin reencolado. No basta con leer un archivo de propiedades.
No se modificaron otros servicios ni sus contratos en esta rama.

`recorrido/` conserva el primer recorrido exitoso (folios 29–31), anterior a
esta corrección de configuración. `recorrido-final/` es la repetición con el
artefacto final, incluidos los reintentos corregidos. El guion del recorrido
funcional es el mismo en ambos casos; el ensayo de fallidos fue posterior.

## Reproducción y límites

Para las pruebas de código, seguir el [README del servicio](../../../monpoint-ms/ms-notificaciones/README.md).
Se ejecutó `./mvnw package` con JDK 21, Maven 3.9.16, repositorio Maven local y
`MONGO_NOTIFICACIONES_TEST_URI` habilitada. El contexto de integración desactiva
los escuchadores y Eureka; la evidencia por Gateway/RabbitMQ los ejercita en
el contenedor desplegado.

Para repetir los guiones, se necesita la fase 1 de la semilla, incluido
`vendedor.semilla@monpoint.test`, el servicio sano y Node 22 o posterior.
Desde la raíz del repositorio, con las credenciales de desarrollo en `.env`:

```powershell
node --env-file=monpoint-ms/infra/.env docs/aceptacion/5.4-2026-11-10/aceptacion.mjs evidencia-5.4-nueva
node --env-file=monpoint-ms/infra/.env docs/aceptacion/5.4-2026-11-10/fallidos.mjs evidencia-fallidos-nueva.json
```

Cada ejecución crea datos de prueba nuevos; estos guiones no son la semilla
idempotente de 3.1. Se niegan a sobrescribir su salida. Los errores inciertos
del POST de venta no se reintentan; solo se permite repetir el 400 específico
de producto aún no disponible en la réplica, antes de persistir la venta.

Alcance delimitado:

- Es aceptación de backend automatizada por HTTP y RabbitMQ, sin navegación
  humana ni pantalla nueva. No acredita un recorrido en Windows.
- Se reutilizaron volúmenes existentes. No acredita el arranque de todo el
  sistema desde bases vacías ni la fase 2 de la semilla.
- En la nube se compiló el JAR fuera de Docker y se ejecutó sobre el JRE de una
  imagen local existente, reemplazando su aplicación. El Dockerfile multietapa
  del servicio queda preparado para Compose; esta ejecución no acredita una
  descarga limpia de sus imágenes base y dependencias dentro de Docker.
- ms-reportes no estaba desplegado en este conjunto: no se acredita la
  proyección de estas ventas en reportes. Había diez contenedores sanos al
  terminar, incluidos notificaciones y el cliente nginx.
- La ausencia de efecto adicional en el recorrido está acotada a las lecturas
  guardadas y al log de descarte; la garantía ante concurrencia se contrasta
  por separado con MongoDB real. No se simularon caídas durante el commit.
- Se acepta la existencia del último evento procesado, que puede ser antigua
  por desorden de entrega. No se inventa una versión causal a partir de `fecha`.
- No marca la 5.4, los controles ni la 6.1 en Asana: queda para revisión del usuario.

`SHA256.json` permite verificar los archivos de este directorio, excluyéndose
a sí mismo. No se archivaron credenciales del entorno, tokens ni cabeceras de autorización.
Los guiones usan los valores por omisión de desarrollo ya publicados en el
repositorio, reemplazables mediante variables de entorno.

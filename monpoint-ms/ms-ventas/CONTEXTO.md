# Contexto de continuidad de ms-ventas

Actualizado: 2026-10-25. Nota de continuidad del desarrollo; las fuentes de
planeación y los contratos vigentes siguen siendo la autoridad del proyecto.

## Fuentes y alcance

- Repositorio: https://github.com/MopGEN/MonPoint
- Documentos 01 a 04 en `../docs/planeacion/`: plan del equipo, requisitos,
  backlog y modelo de datos. El Documento 01 vigente delimita el alcance
  efectivo: 35 actividades, 144 puntos y entrega el 6 de noviembre de 2026.
- `../CONTRIBUTING.md`: ramas y commits por actividad, integración mediante PR,
  revisión posterior a la fusión y verificación de Compose antes de fusionar.
- `../ms-auth/README.md`: referencia de convenciones de implementación.
- `../docs/prototipo/index.html`: referencia de experiencia de usuario.
  Se revisó estáticamente; sus API simuladas, identificadores y folios no deben
  sustituir los contratos reales. La implementación web prevista es Vue.
- Login y caja son la prioridad web. Outbox transaccional, comprobantes PDF,
  proveedores y varias pantallas administrativas quedaron fuera del alcance.
  La ausencia de outbox es una limitación aceptada, no una tarea nueva implícita.
- La coordinación de actividades vive en Asana; no se consultó ese tablero
  durante la revisión del repositorio.

## Estado observado en GitHub el 24 de octubre

- Hugo: autenticación, Eureka, Gateway y documentación integrados.
- Victor: inventario y clientes integrados (PR #1 y #5).
- Alejandro: responsable de ventas; Hugo revisa el servicio.
- Rama revisada: `feat/3.6-ms-ventas-replica-local`, un commit por delante de
  `main`, con las tres entidades en el commit
  `23b133be0aab58536da2addca07e3eb2ac222f9e`.
- En ese corte, ventas en `main`, reportes, notificaciones y cliente web
  conservaban marcadores de posición. El estado debe volver a verificarse
  antes de integrar; esta nota no representa seguimiento automático.
- En el PR #1, Hugo pidió acordar los campos provisionales de
  `VentaCreadaEvento` con ventas. `docs/eventos` todavía no contenía el catálogo.
- El control del 25 de octubre requiere la saga completa; el repositorio
  revisado aún no evidenciaba el recorrido completo. No se ejecutó una prueba
  de integración durante esa revisión.

## Punto actual: modelos de la actividad 3.6

- `ProductoReplica`: mismo id de inventario, nombre, precio `BigDecimal`
  con `DECIMAL128`, activo `boolean` y fecha del evento `Instant`.
  No almacena existencia: inventario es la única autoridad sobre stock.
- `ClienteReplica`: únicamente id, nombre y fecha del evento.
  Clientes elimina físicamente sin emitir bajas; la réplica puede conservar
  clientes eliminados. Resolverlo exige acuerdo entre servicios, no añadir
  unilateralmente `activo` ni un nuevo evento.
- `EventoProcesado`: id generado al persistir, eventoId único, tipo y fecha.
  La fábrica exige eventoId no nulo mediante `Objects.requireNonNull`.
- Se mantienen `@Getter`, constructor vacío protegido y mutación mediante
  métodos con nombre. `actualizarDesde` asigna los datos; el consumidor decide
  si corresponde aplicar o descartar el evento.
- `EventoProcesado.de` recibe `fechaProcesado` explícitamente. Es una diferencia
  deliberada respecto al `Instant.now()` interno de inventario y permite
  controlar el tiempo desde el llamador. Mantener esta firma.
- `ProductoEvento.Datos.activo` usa `Boolean`: un campo JSON ausente o nulo
  debe ser detectable. El consumidor debe resolverlo antes de convertir a
  `boolean`; no tratarlo silenciosamente como `false`. La entidad conserva
  `boolean` primitivo.

## Recordatorios para los pasos siguientes

- Paso 3: con `auto-index-creation` desactivado, `@Indexed(unique = true)`
  documenta la intención. El guion debe crear el índice real y el verificador
  debe comprobarlo al arrancar.
- Paso 4: inventario puede publicar `fechaActualizacion = null`. La orientación
  acordada con el usuario es aplicar el evento sin fecha, no descartarlo por
  considerarlo antiguo. Comparar antigüedad solo cuando ambas fechas existan;
  cubrir también el caso de una réplica cuya fecha almacenada sea nula.
  Esta política está implementada en ambos consumidores del Paso 4.
- La idempotencia depende de eventoId y debe coordinarse atómicamente con el
  efecto persistido. Las entidades por sí solas no completan la actividad 3.6.
- Acordar los contratos de eventos antes de publicar ventas. El flujo previsto
  responde 202/PENDIENTE y se resuelve por StockReservado o StockRechazado.
  No introducir llamadas HTTP entre servicios para resolver la saga.

## Infraestructura local: continuación del Paso 3

- Se incorporó `../infra/mongo-init/02-indices-ventas.js` tal como lo suministró
  el usuario: cinco índices adicionales, dos únicos. MongoDB también lista
  los índices automáticos `_id_` de las colecciones.
- Compose incluye ms-ventas, sin puerto publicado, y pasa la clave de ventas
  desde `.env` tanto a MongoDB como a la URI de ms-ventas.
- El guion 01 valida `MONGO_VENTAS_PASSWORD` antes de crear cualquiera de los
  seis usuarios de servicio. La fila de ventas utiliza esa clave del entorno.
- En este equipo el stack llamado `infra` pertenece al proyecto anterior
  `C:\Users\osber\Proyectos\MonPoint\infra` y contiene MariaDB. Para este
  checkout se usa `monpoint-ms`; `COMPOSE_PROJECT_NAME=monpoint-ms` queda en el
  `.env` local, junto con las credenciales generadas, sin versionarse.
- Ejecutar Compose desde `../infra/`: el compose fija `name: monpoint-ms`, por
  lo que basta `docker compose up -d --build`.
  `docker compose down -v` elimina los datos de este proyecto;
  el usuario autorizó la recreación local para este paso.

## Paso 4: consumidores y repositorios

- Infraestructura subida por separado en `d71fde8`; el aviso para Hugo y Victor
  está en `infra/README.md`. El usuario enviará personalmente el mensaje.
- Tres repositorios MongoRepository; el de eventos expone `existsByEventoId`.
- `ProductoEventoListener` y `ClienteEventoListener` ejecutan la decisión y
  ambas escrituras dentro de `@Transactional`. No capturan errores.
- Los eventos antiguos se registran sin modificar la réplica. Las fechas nulas
  se aplican; fechas iguales también. No hay ramas por tipo de producto.
- `activo` ausente o nulo provoca error; no se convierte silenciosamente a false.
- Pruebas unitarias de la tabla de decisión y pruebas opcionales contra MongoDB
  real mediante `MONGO_VENTAS_TEST_URI`. Ver instrucciones en `README.md`.
- Verificación del 25 de octubre: 23 pruebas unitarias y seis de integración
  correctas, sin omisiones. Las cuatro variantes de conflicto real del índice
  único revirtieron creación/actualización de producto y cliente.
- Prueba sobre los consumidores desplegados en Docker: cuatro entregas RabbitMQ
  (baja, duplicado, antiguo y cliente sin fecha), tres eventos registrados y
  réplicas correctas. Resultado: `RABBIT_REPLICAS_VERIFICADAS resultado=ok`.
  Se eliminaron únicamente los documentos de prueba. Los ocho servicios están
  saludables y ventas conserva `INDICES_VERIFICADOS resultado=ok`.

### Estado de la verificación local

- La configuración de Compose valida y el guion 02 coincide con el adjunto.
  Se comprobó que la guarda del guion 01 rechaza una clave ausente antes de
  acceder a las bases y permite crear los seis usuarios con la clave presente.
- El primer arranque falló por falta de espacio en C:, dejando Docker en modo
  de solo lectura. El usuario autorizó reiniciar Docker y después todo WSL.
- Tras apartar de forma reversible los directorios de sockets temporales
  inaccesibles y ejecutar `wsl --shutdown`, Docker volvió a responder.
  El contenedor antiguo `monpoint-mariadb` volvió a arrancar.
- El usuario autorizó trasladar el almacenamiento de Docker a D: conservando
  los datos. Con Docker y WSL detenidos se copió el disco virtual, se verificó
  igualdad SHA-256 y se enlazó la ruta original mediante una junction NTFS:
  `C:\Users\osber\AppData\Local\Docker\wsl\disk` apunta a
  `D:\DockerDesktop\disk`. La aplicación y la distribución de sistema de
  Docker siguen en C:; las imágenes, contenedores y volúmenes están en D:.
- El respaldo previo al traslado está en
  `D:\DockerDesktop\backup-before-move\docker_data.vhdx`, con registro de la
  migración en `D:\DockerDesktop\migration-record.json`. Se comprobaron los
  mismos identificadores de contenedores e imágenes y el volumen
  `infra_monpoint-data`; `monpoint-mariadb` volvió a arrancar.
- Las primeras imágenes descargadas cuando C: se llenó quedaron dañadas:
  `/usr/bin/bash` de MongoDB y el entrypoint de RabbitMQ tenían cero bytes.
  Se retiraron y descargaron nuevamente. Para MongoDB fue necesario eliminar
  específicamente la variante `linux/amd64` con `docker image rm --platform
  linux/amd64 --force mongo:7`, tras retirar su contenedor fallido: eliminar
  únicamente la etiqueta no liberaba la variante dañada.
- Verificación completada el 25 de octubre: los ocho servicios de
  `monpoint-ms` están saludables; Gateway, auth, inventario, clientes y ventas
  están registrados como UP en Eureka. MongoDB reporta PRIMARY.
- Los seis usuarios existen con permisos readWrite en su propia base.
  `ventas_user` autentica con `MONGO_VENTAS_PASSWORD` del entorno.
- Se verificaron directamente los cinco índices adicionales, incluyendo sus
  campos y unicidad: `ux_eventos_procesados_eventoId` y `ux_ventas_folio` son
  únicos; `ix_ventas_estado_fecha`, `ix_ventas_fecha` e
  `ix_productos_replica_activo` no lo son. Los índices `_id_` son adicionales.
  Ventas registra `evento=INDICES_VERIFICADOS resultado=ok`.
- El respaldo de D: corresponde al momento previo al traslado; no se actualiza
  automáticamente con los datos nuevos del stack. El contenedor antiguo de
  MariaDB se verificó en ejecución después de completar la infraestructura.

## Actividad 4.1: registro asíncrono con folio

- Se verificó la 3.6 en main: PR #6 fusionado, commit `4c1c315`, Hugo asignado
  para revisión posterior. El usuario se encarga de marcar Asana y enviar el
  aviso de la credencial. No se confirmó aquí que ya lo haya hecho.
- Rama `feat/4.1-ms-ventas-registro-asincrono`, creada desde main actualizado.
- Se incorporaron el puerto, adaptador Rabbit y siete pruebas del archivo
  `ms-ventas-41-publicador.tar.gz` entregado por el usuario.
- Corrección expresamente acordada: `clienteNombre` en el evento y ningún
  `subtotal` por línea. La fecha del payload se mantiene como `fecha` y se toma
  de `ventas.fechaCreacion`. Contrato en `../docs/eventos/venta-creada.md`.
- Cliente opcional. Si se envía uno no disponible en clientes_replica, devolver
  400 con el texto exacto acordado para 5.5 (constante en VentaService). No se
  distingue un cliente inexistente de un evento de alta aún en tránsito.
- Venta, líneas embebidas y Contador respetan el esquema: clienteNombre,
  fechaCreacion, fechas de resolución/cancelación reservadas para 4.4,
  importes Decimal128, referencias ObjectId y folio Int64. No se guarda subtotal.
- El DTO solo acepta clienteId y líneas con productoId/cantidad. Totales,
  nombres, precios, folio y estado enviados por el cliente se ignoran.
- Revisión de 4.1: clienteId no nulo y productoId deben ser ObjectId válidos.
  Un formato inválido devuelve 400 con detalle del campo antes de abrir la
  transacción. El mensaje de consistencia eventual se reserva para identificadores
  válidos ausentes en la réplica. Se mantiene la política de reintentos acordada.
  Verificación de esta corrección: 42 pruebas de servicio y HTTP aprobadas,
  sin fallos ni omisiones; ocho nuevas ejecutan controlador y servicio real.
- VentaService valida y congela, calcula, obtiene folio con findAndModify/$inc,
  inserta y llama al publicador dentro de TransactionTemplate. Los conflictos
  transitorios reintentan la transacción completa; nunca se reintenta un commit
  de resultado desconocido. Venta y contador se revierten juntos.
- El adaptador publica en afterCommit y conserva el contrato de no propagar
  fallos de envío. El log de fallo incluye ventaId, folio y eventoId para poder
  reconstruir el evento desde el snapshot persistido. Sin outbox sigue existiendo
  la ventana entre commit y publicación, y el POST no tiene clave de idempotencia.
- POST /ventas responde 202 con id, folio y PENDIENTE; exige ADMIN/VENDEDOR.
  SecurityConfig, ProblemDetail y RequestIdFilter siguen el patrón de inventario.
- Verificación: 74 pruebas, cero fallos/omisiones con MongoDB habilitado; diez
  son de integración. Ocho ventas concurrentes obtuvieron folios distintos y
  consecutivos. Se verificaron rollback y visibilidad de la venta antes del envío.
- Recorrido real Gateway → ventas → RabbitMQ → inventario: 202, total 123.45
  aunque la petición mandaba 0.01, stock pasó de 10 a 7. Se conserva la venta
  de demostración folio 11, id `6adeba6ef5098dca5fd7cab3`, PENDIENTE hasta 4.4;
  producto `6adeba6cd1357790e8c2d983`, cliente `6adeba6dca4889987a5e63d1`.
  La cola temporal de captura del evento se eliminó al terminar la prueba.
- Pendiente para la consulta por fecha: el guion 02 heredado indexa `fecha`,
  mientras el esquema de venta usa `fechaCreacion`. Se documenta y no se cambian
  índices compartidos en este paso. El índice único de folio sí se exige al arrancar.

## Actividad 4.4: resolución de la saga

- La 4.1 se fusionó por autorización del usuario: PR #8, commit `58b6a65` en
  main, incluida la corrección de formatos de identificadores. Se creó desde
  ese main la rama `feat/4.4-ms-ventas-resolucion-saga`.
- Se incorporaron los parches del puerto/adaptador y las seis pruebas entregadas
  en `ms-ventas-44-cierre.tar.gz`. El mecanismo afterCommit se conserva.
- StockEventoListener resuelve y registra eventoId en una misma transacción.
  Duplicados no consultan la venta; venta ausente propaga; venta resuelta registra
  sin modificar ni publicar. Confirmación y cancelación son transiciones terminales.
- fechaResolucion es la hora local del servicio al resolver, truncada a milisegundos
  para coincidir exactamente con BSON y con el evento de cierre.
- Los cierres llevan la venta completa y sin subtotal. VentaCancelada lleva el
  código de inventario; la frase comprensible vive en motivoCancelacion.
- El nombre y el solicitado salen de la venta congelada; se suman líneas repetidas
  del producto. No hay consulta de stock ni acceso a productos_replica al resolver.
- El usuario pidió preparar un mensaje para Victor sobre disponible/solicitado:
  `docs/mensaje-victor-stock.md`. No se envió. Mientras se acuerda la ampliación,
  el mensaje del cajero funciona sin disponible.
- Se conservan los tres escuchadores independientes: el consumidor de stock tiene
  transiciones y publicación diferentes a las réplicas. No se introdujo clase base.
- La limitación sin outbox incluye ahora cierres: la venta puede quedar resuelta
  sin que reportes reciba el evento; reentregar stock no repara la publicación.
  Se documenta en README y en `../docs/eventos/venta-cierre.md`.
- Verificación final (26 de octubre): 115 pruebas aprobadas, cero fallos, errores
  u omisiones; 18 usan MongoDB real. Las ocho nuevas de integración incluyen
  reversión por índice único en ambos desenlaces y dos respuestas concurrentes.
- Recorrido real Gateway → ventas → RabbitMQ → inventario → ventas:
  folio 40 (`6adf4d3a2b81803d3a3320eb`) CONFIRMADA, total 123.45;
  folio 41 (`6adf4d3a2b81803d3a3320ed`) CANCELADA, total 329.20.
  Producto `6adf4d3996990119d4102583`: stock 10 → 7; el rechazo de 8 unidades
  no descontó nada. Ambos POST respondieron 202 PENDIENTE antes del cierre.
  Reentregas reales no publicaron cierres adicionales; tres colas de fallidos en cero.
- La venta de demostración de la 4.1, folio 11, también pasó a CONFIRMADA al
  consumir la respuesta de inventario que estaba esperando en ventas.stock.
- Imagen local de ms-ventas reconstruida y desplegada; los ocho servicios saludables.
  Evidencia local en `target/evidencia-cierre-44.json` y guion de comprobación en
  `target/verificar-cierre-44.ps1` (ignorados por Git). Se conservaron ventas de demo.

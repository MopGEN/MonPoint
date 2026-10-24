# Contexto de continuidad de ms-ventas

Actualizado: 2026-10-24. Nota de continuidad del desarrollo; las fuentes de
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
  Esta política corresponde al consumidor y todavía no está implementada.
- La idempotencia depende de eventoId y debe coordinarse atómicamente con el
  efecto persistido. Las entidades por sí solas no completan la actividad 3.6.
- Acordar los contratos de eventos antes de publicar ventas. El flujo previsto
  responde 202/PENDIENTE y se resuelve por StockReservado o StockRechazado.
  No introducir llamadas HTTP entre servicios para resolver la saga.

// =============================================================================
// MonPoint MS — Indices de ventas_db
// =============================================================================
// Va en infra/mongo-init/, junto a 01-crear-bases-y-usuarios.js. Los guiones de
// esa carpeta se ejecutan EN ORDEN ALFABETICO, asi que este corre despues del 01
// y las bases y los usuarios ya existen cuando llega.
//
// POR QUE AQUI Y NO CON auto-index-creation
// Los otros tres servicios activan spring.data.mongodb.auto-index-creation=true,
// que crea los indices de forma perezosa a partir de las anotaciones. ms-ventas no.
// Dos razones:
//
//   1. El Doc 04 declara el esquema CERRADO: agregar o cambiar un indice lo decide
//      el Product Owner. Con la creacion automatica, cualquier @Indexed que alguien
//      agregue se materializa sin pasar por esa revision.
//   2. El indice se crearia al arrancar la aplicacion, no al aprovisionar la base.
//      Si falla, falla en silencio, y la garantia de idempotencia del indice unico
//      sobre eventoId desaparece sin que nadie se entere.
//
// LIMITACION QUE HAY QUE CONOCER
// mongo-init SOLO se ejecuta sobre un volumen de datos vacio. Si la base ya existe,
// este guion no corre. Por eso ms-ventas lleva VerificadorDeIndices, que detiene el
// arranque con un mensaje explicito si algun indice falta. Para aplicarlo sobre un
// volumen existente:
//
//   docker compose down -v && docker compose up -d
//
// (el -v borra los datos; en desarrollo no hay nada que conservar)
// =============================================================================

const db_ventas = db.getSiblingDB("ventas_db");

// -----------------------------------------------------------------------------
// eventos_procesados — la clave de la idempotencia (RNF-16)
// -----------------------------------------------------------------------------
// El indice UNICO es la garantia real. La comprobacion previa en codigo
// (existsByEventoId) es solo una optimizacion: no protege contra dos entregas
// simultaneas del mismo evento, porque ambas pueden leer "no existe" antes de que
// ninguna escriba. El indice unico hace fallar a la segunda insercion.
//
// RabbitMQ entrega cada mensaje AL MENOS una vez, no exactamente una vez. Sin esto,
// un StockReservado reentregado confirmaria la venta dos veces.
db_ventas.getCollection("eventos_procesados").createIndex(
  { eventoId: 1 },
  { unique: true, name: "ux_eventos_procesados_eventoId" }
);

// -----------------------------------------------------------------------------
// ventas
// -----------------------------------------------------------------------------
// El folio es el identificador legible del ticket y no puede repetirse. Lo genera
// un incremento atomico sobre contadores, pero el indice unico es la red de
// seguridad: si alguien cambia esa logica por una lectura-y-suma, el duplicado se
// detecta al escribir en vez de aparecer impreso en dos tickets.
db_ventas.getCollection("ventas").createIndex(
  { folio: 1 },
  { unique: true, name: "ux_ventas_folio" }
);

// Para la consulta de ventas pendientes (RF-3.10): es como se detectan las ventas
// que quedaron atascadas si ms-ventas cayo entre persistir y publicar VentaCreada.
// Sin outbox esa deteccion es manual, asi que la consulta tiene que ser barata.
db_ventas.getCollection("ventas").createIndex(
  { estado: 1, fecha: -1 },
  { name: "ix_ventas_estado_fecha" }
);

// Historial por rango de fechas (RF-3.9, en su version recortada: sin filtros
// adicionales ni ordenamiento por campo arbitrario).
db_ventas.getCollection("ventas").createIndex(
  { fecha: -1 },
  { name: "ix_ventas_fecha" }
);

// -----------------------------------------------------------------------------
// Replicas
// -----------------------------------------------------------------------------
// _id ya tiene indice unico por definicion en MongoDB, y las replicas se consultan
// por _id (el MISMO identificador que en ms-inventario y ms-clientes), asi que no
// hacen falta indices adicionales para la validacion de la venta.
//
// Este si hace falta: el descarte de eventos desordenados compara fechaActualizacion,
// y la busqueda del catalogo en la pantalla de caja filtra por activo.
db_ventas.getCollection("productos_replica").createIndex(
  { activo: 1 },
  { name: "ix_productos_replica_activo" }
);

print("");
print("  Indices de ventas_db creados:");
["eventos_procesados", "ventas", "productos_replica"].forEach(function (c) {
  db_ventas.getCollection(c).getIndexes().forEach(function (i) {
    print("    " + c + "." + i.name + (i.unique ? "  [UNICO]" : ""));
  });
});
print("");

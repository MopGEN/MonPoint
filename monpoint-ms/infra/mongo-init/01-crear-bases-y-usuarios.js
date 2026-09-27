// =============================================================================
// MonPoint MS — Inicializacion de MongoDB
// =============================================================================
// Este guion se ejecuta UNA SOLA VEZ, cuando el volumen de datos esta vacio.
// Docker lo ejecuta automaticamente porque esta montado en
// /docker-entrypoint-initdb.d/ dentro del contenedor.
//
// Hace dos cosas:
//   1. Crea una base de datos por servicio.
//   2. Crea un usuario por base, con permiso UNICAMENTE sobre la suya.
//
// La inicializacion del replica set NO ocurre aqui: se hace desde el
// healthcheck del contenedor, por la razon que se explica en docker-compose.yml.
// =============================================================================

// -----------------------------------------------------------------------------
// Un usuario por servicio
// -----------------------------------------------------------------------------
// Cada servicio recibe credenciales que solo funcionan sobre su propia base.
// Esto materializa el patron "base de datos privada por servicio" del
// Documento 02, seccion 5.2: el aislamiento es LOGICO (por permisos), no
// fisico (por instancias separadas).
//
// Consecuencia practica: si ms-ventas intenta leer inventario_db, MongoDB
// devuelve un error de autorizacion. La frontera entre servicios deja de
// depender de la disciplina del equipo y pasa a estar impuesta por la base.
//
// El rol "readWrite" permite leer, escribir y crear indices sobre esa base,
// pero no administrar el servidor ni tocar otras bases.
// -----------------------------------------------------------------------------

const servicios = [
  { base: "auth_db",           usuario: "auth_user",           clave: "auth_pass" },
  { base: "inventario_db",     usuario: "inventario_user",     clave: "inventario_pass" },
  { base: "ventas_db",         usuario: "ventas_user",         clave: "ventas_pass" },
  { base: "clientes_db",       usuario: "clientes_user",       clave: "clientes_pass" },
  { base: "reportes_db",       usuario: "reportes_user",       clave: "reportes_pass" },
  { base: "notificaciones_db", usuario: "notificaciones_user", clave: "notificaciones_pass" }
];

servicios.forEach(function (s) {
  const db_servicio = db.getSiblingDB(s.base);

  db_servicio.createUser({
    user: s.usuario,
    pwd:  s.clave,
    roles: [{ role: "readWrite", db: s.base }]
  });

  // MongoDB no crea una base hasta que contiene al menos un documento.
  // Insertamos un marcador para que la base exista de inmediato y sea
  // visible al inspeccionarla con Compass o mongosh.
  db_servicio.createCollection("_inicializacion");
  db_servicio._inicializacion.insertOne({
    creadaEn: new Date(),
    nota: "Marcador de creacion. Se puede borrar sin consecuencias."
  });

  print("Base creada: " + s.base + " con usuario " + s.usuario);
});

print("");
print("=== Inicializacion completada: 6 bases, 6 usuarios ===");

// Ejecutar con Node >= 22 desde la raíz del repositorio. Usa credenciales del
// entorno; nunca las guarda. Crea un producto y tres ventas reales de prueba.
// node --env-file=monpoint-ms/infra/.env docs/aceptacion/5.4-2026-11-10/aceptacion.mjs SALIDA
// SALIDA debe ser un directorio nuevo; no sobrescribe evidencia previa.
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { randomUUID } from 'node:crypto';

const salida = process.argv[2];
assert.ok(salida, 'Indicar un directorio nuevo para la evidencia');
await mkdir(salida); // falla si existe, deliberadamente
const gateway = process.env.GATEWAY_URL || 'http://localhost:8080';
const rabbit = process.env.RABBITMQ_API_URL || 'http://localhost:15672/api';
assert.ok(process.env.RABBITMQ_USER && process.env.RABBITMQ_PASSWORD, 'Faltan credenciales RabbitMQ');
const rabbitAuth = 'Basic ' + Buffer.from(`${process.env.RABBITMQ_USER}:${process.env.RABBITMQ_PASSWORD}`).toString('base64');
const cola = `captura.5.4.${randomUUID()}`;
const registro = { inicio: new Date().toISOString(), colaTemporal: cola, comprobaciones: [], ventas: [] };
const guardar = (nombre, valor) => writeFile(join(salida, nombre), JSON.stringify(valor, null, 2) + '\n');
const pausa = () => new Promise(r => setTimeout(r, 300));
async function pedir(base, ruta, { method = 'GET', body, auth } = {}) {
  const r = await fetch(base + ruta, { method, headers: {
    ...(auth ? { Authorization: auth } : {}), ...(body ? { 'Content-Type': 'application/json' } : {})
  }, body: body ? JSON.stringify(body) : undefined, signal: AbortSignal.timeout(15000) });
  const texto = await r.text();
  return { status: r.status, body: texto ? JSON.parse(texto) : null };
}
const amqp = (ruta, opciones = {}) => pedir(rabbit, ruta, { ...opciones, auth: rabbitAuth });
async function esperar(accion, comprobar, nombre) {
  const limite = Date.now() + 30000;
  while (Date.now() < limite) {
    const valor = await accion();
    if (comprobar(valor)) return valor;
    await pausa();
  }
  throw new Error(`Plazo agotado: ${nombre}`);
}
async function login(correo, password) {
  const r = await pedir(gateway, '/auth/login', { method: 'POST', body: { correo, password } });
  assert.equal(r.status, 200, 'Autenticación fallida; no se imprimen credenciales ni cuerpo de login');
  assert.ok(r.body.token);
  return 'Bearer ' + r.body.token;
}
let admin;
let producto;
const api = (ruta, opciones = {}) => pedir(gateway, ruta, { auth: admin, ...opciones });
const avisos = async filtro => {
  const r = await api('/notificaciones' + (filtro === undefined ? '' : `?leida=${filtro}`));
  assert.equal(r.status, 200);
  return r.body.filter(n => n.productoId === producto.id);
};
async function vender(cantidad) {
  // Solo el 400 preciso de réplica aún no disponible se puede repetir:
  // esa validación ocurre antes de asignar folio y de insertar la venta.
  const r = await esperar(async () => {
    const respuesta = await api('/ventas', { method: 'POST', body: { lineas: [{ productoId: producto.id, cantidad }] } });
    if (respuesta.status === 400 && respuesta.body.detail?.startsWith('El producto no está disponible o está inactivo:')) return null;
    assert.equal(respuesta.status, 202, 'POST incierto o rechazado: no se repite');
    return respuesta;
  }, Boolean, 'réplica del producto');
  const venta = await esperar(() => api(`/ventas/${r.body.id}`), v => v.status === 200 && v.body.estado !== 'PENDIENTE', 'resolución de venta');
  assert.equal(venta.body.estado, 'CONFIRMADA');
  registro.ventas.push(venta.body);
  return venta.body;
}
async function capturar(existencia) {
  return esperar(async () => {
    const r = await amqp(`/queues/%2F/${cola}/get`, { method: 'POST', body: { count: 20, ackmode: 'ack_requeue_false', encoding: 'auto' } });
    assert.equal(r.status, 200);
    return r.body.find(m => {
      const e = JSON.parse(m.payload);
      return e.datos.productoId === producto.id && e.datos.existenciaResultante === existencia;
    });
  }, Boolean, 'captura del evento original');
}
async function republicar(mensaje) {
  const r = await amqp('/exchanges/%2F/inventario.eventos/publish', { method: 'POST', body: {
    properties: mensaje.properties, routing_key: 'stock.bajo', payload: mensaje.payload, payload_encoding: 'string'
  } });
  assert.equal(r.status, 200); assert.equal(r.body.routed, true);
}

try {
  admin = await login(process.env.ADMIN_CORREO || 'admin@monpoint.com', process.env.ADMIN_DEFAULT_PASSWORD || 'Admin123!');
  const vendedor = await login('vendedor.semilla@monpoint.test', process.env.SEMILLA_VENDEDOR_PASSWORD || 'Vendedor123!');
  await esperar(() => api('/notificaciones'), r => r.status === 200, 'registro del servicio en Gateway');
  const topologia = await amqp('/queues/%2F/notificaciones.stock-bajo');
  assert.equal(topologia.status, 200);
  assert.equal(topologia.body.durable, true);
  assert.equal(topologia.body.arguments['x-dead-letter-routing-key'], 'notificaciones.stock-bajo.fallidos');
  const enlaces = await amqp('/queues/%2F/notificaciones.stock-bajo/bindings');
  assert.ok(enlaces.body.some(b => b.source === 'inventario.eventos' && b.routing_key === 'stock.bajo'));
  await guardar('topologia.json', { cola: topologia.body, bindings: enlaces.body });
  assert.equal((await amqp(`/queues/%2F/${cola}`, { method: 'PUT', body: { durable: false, auto_delete: false, arguments: {} } })).status, 201);
  assert.equal((await amqp(`/bindings/%2F/e/inventario.eventos/q/${cola}`, { method: 'POST', body: { routing_key: 'stock.bajo', arguments: {} } })).status, 201);
  const alta = await api('/inventario/productos', { method: 'POST', body: {
    nombre: `Aceptación 5.4 ${randomUUID()}`, descripcion: 'Recorrido aislado de alertas', precio: 41.15, existencia: 10, umbralStock: 5
  } });
  assert.equal(alta.status, 201);
  producto = alta.body;
  registro.productoInicial = producto;
  await vender(5);
  const primero = (await esperar(() => avisos(false), a => a.length === 1, 'primer aviso'))[0];
  assert.equal(primero.existenciaResultante, 5);
  assert.equal(primero.productoNombre, producto.nombre);
  assert.equal(primero.activa, true); assert.equal(primero.leida, false); assert.ok(primero.fechaCreacion);
  await guardar('01-no-leida.json', primero);
  const original = await capturar(5);
  await guardar('mensaje-capturado.json', original);
  await writeFile(join(salida, 'payload-original.json'), original.payload);
  assert.equal(JSON.parse(original.payload).eventoId, original.properties.message_id);
  registro.comprobaciones.push('StockBajo real crea aviso no leído con producto, existencia y fecha');

  await vender(1);
  const actualizado = (await esperar(() => avisos(), a => a.length === 1 && a[0].existenciaResultante === 4, 'actualización del aviso'))[0];
  assert.equal(actualizado.id, primero.id);
  assert.equal(actualizado.fechaCreacion, primero.fechaCreacion);
  await guardar('02-actualizada.json', actualizado);
  registro.comprobaciones.push('Evento distinto actualiza la misma activa sin duplicarla');

  const lectura = await api(`/notificaciones/${primero.id}/leida`, { method: 'PUT' });
  assert.equal(lectura.status, 204);
  const cerrada = (await avisos(true))[0];
  assert.equal(cerrada.activa, false); assert.equal(cerrada.leida, true); assert.ok(cerrada.fechaLectura);
  assert.equal((await avisos(false)).length, 0);
  assert.equal((await api(`/notificaciones/${primero.id}/leida`, { method: 'PUT' })).status, 204);
  assert.deepEqual((await avisos(true))[0], cerrada);
  const inexistente = await api('/notificaciones/000000000000000000000000/leida', { method: 'PUT' });
  assert.equal(inexistente.status, 404);
  await guardar('03-leida.json', cerrada);
  await guardar('04-inexistente.json', inexistente);
  registro.comprobaciones.push('PUT 204 repetible, fecha estable, filtros de lectura y 404');

  const permisos = [];
  for (const [rol, auth, esperado] of [['sin-token', undefined, 401], ['VENDEDOR', vendedor, 403]]) {
    for (const [method, ruta] of [['GET', '/notificaciones'], ['PUT', `/notificaciones/${primero.id}/leida`]]) {
      const r = await pedir(gateway, ruta, { method, auth });
      assert.equal(r.status, esperado);
      permisos.push({ rol, method, ruta, ...r });
    }
  }
  await guardar('05-permisos.json', permisos);
  registro.comprobaciones.push('Gateway real: sin token 401; VENDEDOR 403 en ambas rutas');

  await republicar(original);
  // Esperar al consumo, no interpretar la publicación como procesamiento.
  await esperar(async () => {
    const r = await amqp('/queues/%2F/notificaciones.stock-bajo');
    return r.body;
  }, q => q.messages === 0 && q.messages_unacknowledged === 0, 'consumo de la reentrega');
  // Las métricas del broker pueden demorarse; las dos lecturas y el log se
  // contrastan también en RESULTADOS.md. No prueba ausencia indefinida.
  await new Promise(r => setTimeout(r, 1500));
  assert.deepEqual(await avisos(), [cerrada]);
  await guardar('06-tras-reentrega.json', await avisos());
  registro.eventoReentregado = original.properties.message_id;
  registro.comprobaciones.push('Payload original reentregado no modifica ni reabre la leída');

  await vender(1);
  const historial = await esperar(() => avisos(), a => a.length === 2 && a.some(n => n.activa && n.existenciaResultante === 3), 'aviso posterior a lectura');
  assert.equal(historial.filter(n => n.activa).length, 1);
  assert.ok(historial.find(n => n.activa).id !== primero.id);
  await guardar('07-historial.json', historial);
  registro.comprobaciones.push('Un evento nuevo después de leer abre otro aviso y conserva el histórico');
  const final = await api(`/inventario/productos/${producto.id}`);
  assert.equal(final.body.existencia, 3);
  registro.productoFinal = final.body;
  registro.estado = 'COMPLETADA';
} catch (error) {
  registro.estado = 'FALLIDA';
  // No imprimir cuerpos HTTP, cabeceras o argumentos que pudieran incluir secretos.
  registro.error = error.message;
  process.exitCode = 1;
} finally {
  const limpieza = await amqp(`/queues/%2F/${cola}`, { method: 'DELETE' });
  registro.colaTemporalEliminada = limpieza.status === 204 || limpieza.status === 404;
  if (!registro.colaTemporalEliminada) process.exitCode = 1;
  registro.fin = new Date().toISOString();
  await guardar('resultado.json', registro);
  console.log(JSON.stringify({ estado: registro.estado, comprobaciones: registro.comprobaciones.length, colaTemporalEliminada: registro.colaTemporalEliminada }));
}

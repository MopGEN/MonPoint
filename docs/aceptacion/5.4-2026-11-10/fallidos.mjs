// Requiere una cola de fallidos vacía. Publica UNA carga inválida identificada
// y retira únicamente ese mensaje después de comprobar su rechazo.
import assert from 'node:assert/strict';
import { writeFile } from 'node:fs/promises';
import { randomUUID } from 'node:crypto';
const salida = process.argv[2];
assert.ok(salida, 'Indicar archivo de salida nuevo');
assert.ok(process.env.RABBITMQ_USER && process.env.RABBITMQ_PASSWORD);
const base = process.env.RABBITMQ_API_URL || 'http://localhost:15672/api';
const auth = 'Basic ' + Buffer.from(`${process.env.RABBITMQ_USER}:${process.env.RABBITMQ_PASSWORD}`).toString('base64');
async function pedir(ruta, method = 'GET', body) {
  const r = await fetch(base + ruta, { method, headers: { Authorization: auth, 'Content-Type': 'application/json' },
    body: body ? JSON.stringify(body) : undefined, signal: AbortSignal.timeout(10000) });
  assert.ok(r.ok, `RabbitMQ HTTP ${r.status}`);
  return r.json();
}
const ruta = '/queues/%2F/notificaciones.stock-bajo.fallidos';
const leer = ackmode => pedir(ruta + '/get', 'POST', { count: 1, ackmode, encoding: 'auto' });
assert.equal((await leer('ack_requeue_true')).length, 0, 'No tocar fallidos preexistentes');
const eventoId = randomUUID();
const payload = JSON.stringify({ eventoId, tipo: 'StockBajo', version: '1', fecha: new Date().toISOString(),
  origen: 'ms-inventario', datos: { productoId: '000000000000000000000054', nombre: 'Prueba de rechazo 5.4', existenciaResultante: -1 } });
const inicio = Date.now();
const publicado = await pedir('/exchanges/%2F/inventario.eventos/publish', 'POST', {
  properties: { message_id: eventoId, content_type: 'application/json' }, routing_key: 'stock.bajo', payload, payload_encoding: 'string'
});
assert.equal(publicado.routed, true);
let encontrado;
while (Date.now() - inicio < 15000) {
  const mensajes = await leer('ack_requeue_true');
  if (mensajes.length) {
    assert.equal(mensajes[0].properties.message_id, eventoId, 'Apareció otro mensaje; se conserva en la cola');
    encontrado = mensajes[0]; break;
  }
  await new Promise(r => setTimeout(r, 250));
}
assert.ok(encontrado, 'El mensaje no llegó a fallidos');
assert.equal(encontrado.payload, payload);
assert.ok(encontrado.properties.headers['x-death'].some(d => d.reason === 'rejected' && d.queue === 'notificaciones.stock-bajo'));
const retirado = await leer('ack_requeue_false');
assert.equal(retirado[0].properties.message_id, eventoId);
const resultado = { fecha: new Date().toISOString(), eventoId, publicado, mensaje: encontrado,
  milisegundosHastaObservarRechazo: Date.now() - inicio, mensajeDePruebaRetirado: true };
await writeFile(salida, JSON.stringify(resultado, null, 2) + '\n', { flag: 'wx' });
console.log(JSON.stringify({ rechazado: eventoId, retirado: true }));

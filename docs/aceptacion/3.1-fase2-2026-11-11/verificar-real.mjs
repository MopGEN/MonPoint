// Guion de aceptación, separado de la semilla. Requiere un conjunto NUEVO y
// sano; escribe evidencia en un directorio nuevo, nunca sobrescribe originales.
// Desde la raíz: node --env-file=monpoint-ms/infra/.env este-guion.mjs SALIDA
import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { mkdir, writeFile } from 'node:fs/promises'
import { join, resolve } from 'node:path'
import { productos, clientes } from '../../../monpoint-ms/scripts/semilla.mjs'

const salida = process.argv[2]
assert.ok(salida, 'Indicar directorio nuevo de salida')
await mkdir(salida)
const gateway = process.env.SEMILLA_GATEWAY_URL || 'http://localhost:18081'
const rabbit = process.env.SEMILLA_RABBIT_API_URL || 'http://localhost:15673/api'
const fecha = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Mexico_City',
  year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date())
const agregadoRuta = `/reportes/ventas/agregado?periodo=DIA&desde=${fecha}&hasta=${fecha}`
const evidencia = { inicio: new Date().toISOString(), fechaNegocio: fecha, comprobaciones: [] }
const guardar = (archivo, datos) => writeFile(join(salida, archivo), JSON.stringify(datos, null, 2) + '\n', { flag: 'wx' })
let token
async function pedir(base, ruta, { method = 'GET', body, authorization } = {}) {
  const r = await fetch(base + ruta, { method, headers: {
    ...(authorization ? { Authorization: authorization } : {}), ...(body ? { 'Content-Type': 'application/json' } : {})
  }, ...(body ? { body: JSON.stringify(body) } : {}), signal: AbortSignal.timeout(10000) })
  return { status: r.status, datos: await r.json() }
}
async function api(ruta) {
  const r = await pedir(gateway, ruta, { authorization: `Bearer ${token}` })
  assert.equal(r.status, 200, `GET ${ruta}`)
  return r.datos
}
async function esperar(accion, listo, nombre) {
  const hasta = Date.now() + 60000
  while (Date.now() < hasta) {
    const valor = await accion()
    if (listo(valor)) return valor
    await new Promise(r => setTimeout(r, 500))
  }
  throw new Error(`Plazo agotado: ${nombre}`)
}
function ejecutarSemilla(args) {
  const p = spawnSync(process.execPath, [resolve('monpoint-ms/scripts/semilla.mjs'), ...args], {
    env: { ...process.env, SEMILLA_GATEWAY_URL: gateway }, encoding: 'utf8', timeout: 180000,
  })
  assert.ok(!p.error, 'El proceso de semilla no terminó normalmente; verificar operaciones antes de repetir')
  return { codigo: p.status, salida: JSON.parse(p.status === 0 ? p.stdout : p.stderr) }
}
async function colas() {
  assert.ok(process.env.RABBITMQ_USER && process.env.RABBITMQ_PASSWORD)
  const authorization = 'Basic ' + Buffer.from(`${process.env.RABBITMQ_USER}:${process.env.RABBITMQ_PASSWORD}`).toString('base64')
  const r = await pedir(rabbit, '/queues/%2F', { authorization })
  assert.equal(r.status, 200)
  return r.datos.map(q => ({ nombre: q.name, durable: q.durable, consumidores: q.consumers,
    mensajes: q.messages, pendientes: q.messages_ready, sinConfirmar: q.messages_unacknowledged }))
}

try {
  const acceso = await esperar(() => pedir(gateway, '/auth/login', { method: 'POST', body: {
    correo: 'admin@monpoint.com', password: process.env.ADMIN_DEFAULT_PASSWORD || 'Admin123!',
  } }), r => r.status === 200, 'autenticación por Gateway')
  assert.equal(acceso.datos.rol, 'ADMIN')
  token = acceso.datos.token
  const previas = await colas()
  for (const nombre of ['inventario.venta-creada', 'ventas.productos', 'ventas.clientes', 'ventas.stock',
    'reportes.productos', 'reportes.clientes', 'reportes.ventas', 'notificaciones.stock-bajo']) {
    assert.ok(previas.some(c => c.nombre === nombre && c.durable && c.consumidores > 0), `Cola no preparada: ${nombre}`)
  }
  assert.ok(previas.every(c => c.mensajes === 0))
  await guardar('colas-antes.json', previas)
  const base = { productos: await api('/inventario/productos'), clientes: await api('/clientes'),
    notificaciones: await api('/notificaciones'), agregado: await api(agregadoRuta) }
  assert.equal(base.productos.length, 0); assert.equal(base.clientes.length, 0)
  assert.equal(base.notificaciones.length, 0); assert.equal(base.agregado.total, 0)
  await guardar('base-nueva.json', base)
  evidencia.comprobaciones.push('Sin datos de negocio y con las ocho colas consumidoras declaradas antes de la fase 1')

  const fase1 = ejecutarSemilla([])
  await guardar('fase1.json', fase1)
  assert.equal(fase1.codigo, 0)
  assert.deepEqual(fase1.salida.productos.map(p => p.existencia), [10, 10, 10])
  const fase2 = ejecutarSemilla(['--ventas'])
  await guardar('fase2.json', fase2)
  assert.equal(fase2.codigo, 0)
  assert.equal(fase2.salida.estado, 'COMPLETADA')
  assert.equal(fase2.salida.ventasConfirmadas.length, 3)
  const ventas = []
  for (const v of fase2.salida.ventasConfirmadas) {
    const venta = await api(`/ventas/${v.id}`)
    assert.equal(venta.folio, v.folio); assert.equal(venta.estado, 'CONFIRMADA')
    ventas.push(venta)
  }
  assert.deepEqual(ventas.map(v => v.clienteNombre), [clientes[0].nombre, clientes[1].nombre, null])
  assert.deepEqual(ventas.map(v => v.total), [205.75, 151, 120])
  await guardar('ventas-consultadas.json', ventas)
  const catalogo = await api('/inventario/productos')
  assert.deepEqual(productos.map(p => catalogo.find(x => x.nombre === p.nombre).existencia), [5, 8, 9])
  await guardar('existencias-finales.json', catalogo)
  evidencia.comprobaciones.push('Tres ventas CONFIRMADAS consultadas por id; clientes Ana, Luis y sin cliente; existencias 5, 8 y 9')

  const agregado = await esperar(() => api(agregadoRuta), a => a.total === 476.75, 'proyección en la agregación')
  assert.equal(agregado.puntos.length, 1)
  assert.equal(agregado.puntos[0].total, 476.75)
  await guardar('agregado.json', agregado)
  evidencia.comprobaciones.push('Agregación real de reportes: total 476.75 en el día de negocio')
  const tinta = catalogo.find(p => p.nombre === productos[0].nombre)
  const avisos = await esperar(() => api('/notificaciones?leida=false'), a => a.some(n => n.productoId === tinta.id), 'aviso de tinta')
  assert.equal(avisos.length, 1)
  assert.equal(avisos[0].existenciaResultante, 5)
  assert.equal(avisos[0].productoNombre, tinta.nombre)
  assert.equal(avisos[0].activa, true)
  await guardar('notificaciones.json', avisos)
  evidencia.comprobaciones.push('StockBajo real: un aviso no leído de tinta, existencia 5')

  const repetida = ejecutarSemilla(['--ventas'])
  await guardar('fase2-repetida.json', repetida)
  assert.equal(repetida.codigo, 1)
  assert.match(repetida.salida.error, /existencia inicial/)
  assert.equal(repetida.salida.resumen.ventaEnCurso, null)
  assert.equal(repetida.salida.resumen.ventasConfirmadas.length, 0)
  const fase1Repetida = ejecutarSemilla([])
  await guardar('fase1-repetida.json', fase1Repetida)
  assert.equal(fase1Repetida.codigo, 0)
  assert.deepEqual(fase1Repetida.salida.productos.map(p => p.id), fase1.salida.productos.map(p => p.id))
  assert.deepEqual(fase1Repetida.salida.productos.map(p => p.existencia), [5, 8, 9])
  assert.equal((await api(agregadoRuta)).total, 476.75)
  evidencia.comprobaciones.push('Repetición de fase 2 rechazada por stock consumido; fase 1 reutiliza ids sin reponerlo')
  const finales = await esperar(colas, cs => cs.every(c => c.mensajes === 0), 'colas drenadas')
  await guardar('colas-despues.json', finales)
  evidencia.folios = ventas.map(v => v.folio)
  evidencia.estado = 'COMPLETADA'
} catch (e) {
  evidencia.estado = 'FALLIDA'
  evidencia.error = e.message
  process.exitCode = 1
} finally {
  evidencia.fin = new Date().toISOString()
  await guardar('resultado.json', evidencia)
  console.log(JSON.stringify(evidencia))
}

import test from 'node:test'
import assert from 'node:assert/strict'
import http from 'node:http'
import { once } from 'node:events'
import { spawn } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import { sembrarAutotachi, cuentas, productos, catalogo } from './semilla-autotachi.mjs'

async function servicio(t) {
  const env = Object.fromEntries(cuentas.map((c, i) => [c.variable, `ClavePrueba${i}!`]))
  const usuarios = cuentas.map((c, i) => ({ id: `u${i}`, nombre: c.nombre, correo: c.correo, rol: c.rol, activo: true }))
  const estado = { usuarios, productos: [], peticiones: [], fallo: null }
  const servidor = http.createServer(async (req, res) => {
    const partes = []
    for await (const parte of req) partes.push(parte)
    const cuerpo = partes.length ? JSON.parse(Buffer.concat(partes)) : null
    estado.peticiones.push({ metodo: req.method, ruta: req.url, cuerpo })
    const responder = (status, datos) => { res.writeHead(status, { 'Content-Type': 'application/json' }); res.end(JSON.stringify(datos)) }
    if (estado.fallo?.({ req, res, cuerpo, responder })) return
    if (req.url === '/auth/login') {
      const i = cuentas.findIndex(c => c.correo === cuerpo.correo)
      if (i < 0 || cuerpo.password !== env[cuentas[i].variable] || !usuarios[i].activo) return responder(401, {})
      return responder(200, { correo: usuarios[i].correo, rol: usuarios[i].rol, token: `token-${i}` })
    }
    const i = cuentas.findIndex((_, j) => req.headers.authorization === `Bearer token-${j}`)
    if (i < 0) return responder(401, {})
    if (req.url === '/auth/me') return responder(200, usuarios[i])
    if (req.url !== '/inventario/productos') return responder(404, {})
    if (req.method === 'GET') return responder(200, estado.productos)
    if (usuarios[i].rol !== 'ADMIN') return responder(403, {})
    if (req.method !== 'POST') return responder(405, {})
    const creado = { ...cuerpo, id: `p${estado.productos.length}`, activo: true }
    estado.productos.push(creado); responder(201, creado)
  })
  servidor.listen(0, '127.0.0.1'); await once(servidor, 'listening')
  t.after(() => { servidor.closeAllConnections(); servidor.close() })
  env.SEMILLA_GATEWAY_URL = `http://127.0.0.1:${servidor.address().port}`
  return { env, estado }
}
const altas = estado => estado.peticiones.filter(p => p.metodo === 'POST' && p.ruta !== '/auth/login')

test('importa exactamente los 23 activos visibles, con stock cero y sin fabricar SKU', async t => {
  const { env, estado } = await servicio(t)
  assert.equal(catalogo.length, 24)
  assert.equal(productos.length, 23)
  const r = await sembrarAutotachi({ env })
  assert.equal(r.estado, 'COMPLETADA')
  assert.deepEqual(r.usuarios.map(u => u.rol), ['ADMIN', 'ADMIN', 'ADMIN', 'VENDEDOR'])
  assert.deepEqual(r.referenciasExcluidas, ['ACC-024'])
  assert.equal(altas(estado).length, 23)
  assert.deepEqual(estado.productos.map(({ id, activo, ...p }) => p), productos)
  assert.equal(estado.productos.at(-1).existencia, 0)
  assert.ok(altas(estado).every(p => p.ruta === '/inventario/productos' && !('referencia' in p.cuerpo) && !('codigo' in p.cuerpo)))
})

test('repeticion conserva ids, stock consumido y productos historicos sin escrituras', async t => {
  const { env, estado } = await servicio(t)
  estado.productos.push({ id: 'historico', nombre: 'Semilla 3.1 - Tinta', precio: 41.15, existencia: 5 })
  const primero = await sembrarAutotachi({ env })
  estado.productos[1].existencia = 1
  estado.peticiones.length = 0
  const segundo = await sembrarAutotachi({ env })
  assert.deepEqual(segundo.productos.map(p => p.id), primero.productos.map(p => p.id))
  assert.ok(segundo.productos.every(p => p.accion === 'REUTILIZADO'))
  assert.equal(segundo.productos[0].existencia, 1)
  assert.equal(estado.productos[0].existencia, 5)
  assert.equal(altas(estado).length, 0)
  assert.ok(estado.peticiones.every(p => ['GET', 'POST'].includes(p.metodo)))
})

test('claves ausentes o vacias fallan antes de hacer peticiones; no hay fallback', async t => {
  const { env, estado } = await servicio(t)
  for (const cuenta of cuentas) {
    for (const valor of [undefined, '']) {
      await assert.rejects(sembrarAutotachi({ env: { ...env, [cuenta.variable]: valor } }), new RegExp(cuenta.variable))
    }
  }
  assert.equal(estado.peticiones.length, 0)
})

test('cuarta clave incorrecta o perfil/rol cambiado bloquea todo el catalogo', async t => {
  const { env, estado } = await servicio(t)
  await assert.rejects(sembrarAutotachi({ env: { ...env, AUTOTACHI_VENDEDOR_PASSWORD: 'OtraClave1!' } }), /HTTP 401/)
  assert.equal(altas(estado).length, 0)
  estado.usuarios[2].rol = 'VENDEDOR'
  await assert.rejects(sembrarAutotachi({ env }), /Login discordante/)
  estado.usuarios[2].rol = 'ADMIN'; estado.usuarios[2].nombre = 'Nombre editado'
  await assert.rejects(sembrarAutotachi({ env }), /Perfil discordante/)
  assert.equal(altas(estado).length, 0)
})

test('conflicto en el ultimo producto o nombres duplicados impiden todas las altas', async t => {
  const { env, estado } = await servicio(t)
  estado.productos = [{ ...productos.at(-1), id: 'ultimo', activo: true, precio: 999 }]
  await assert.rejects(sembrarAutotachi({ env }), /precio difiere/)
  assert.equal(altas(estado).length, 0)
  estado.productos = ['a', 'b'].map(id => ({ ...productos[0], id, activo: true }))
  await assert.rejects(sembrarAutotachi({ env }), /Identidad ambigua/)
  assert.equal(altas(estado).length, 0)
})

test('lectura fallida, identidad falsa o token ausente no permite altas', async t => {
  const { env, estado } = await servicio(t)
  for (const modo of ['lista', 'perfil', 'token']) {
    estado.fallo = ({ req, responder }) => {
      if (modo === 'lista' && req.url === '/inventario/productos') { responder(503, {}); return true }
      if (modo === 'perfil' && req.url === '/auth/me') { responder(200, { ...estado.usuarios[0], correo: 'otro@autotachi.mx' }); return true }
      if (modo === 'token' && req.url === '/auth/login') { responder(200, { correo: cuentas[0].correo, rol: 'ADMIN', token: '' }); return true }
      return false
    }
    await assert.rejects(sembrarAutotachi({ env }))
    assert.equal(altas(estado).length, 0)
  }
})

test('POST guardado sin respuesta no se repite; siguiente ejecucion recupera su id', async t => {
  const { env, estado } = await servicio(t)
  estado.fallo = ({ req, res, cuerpo }) => {
    if (req.method !== 'POST' || req.url !== '/inventario/productos') return false
    estado.productos.push({ ...cuerpo, id: 'guardado', activo: true }); res.destroy(); return true
  }
  await assert.rejects(sembrarAutotachi({ env }), error => {
    assert.match(error.message, /puede haberse guardado/)
    assert.equal(error.resumen.estado, 'INTERRUMPIDA')
    assert.equal(error.resumen.productos.length, 0)
    return true
  })
  assert.equal(altas(estado).length, 1)
  estado.fallo = null
  const r = await sembrarAutotachi({ env })
  assert.equal(r.productos[0].id, 'guardado')
  assert.equal(r.productos[0].accion, 'REUTILIZADO')
  assert.equal(estado.productos.length, 23)
})

test('timeout de escritura queda identificado y no reintenta', async t => {
  const { env, estado } = await servicio(t)
  estado.fallo = ({ req }) => req.method === 'POST' && req.url === '/inventario/productos'
  await assert.rejects(sembrarAutotachi({ env, timeoutMs: 150 }), error => {
    assert.match(error.message, /puede haberse guardado/)
    assert.equal(error.resumen.operacion, 'POST /inventario/productos')
    return true
  })
  assert.equal(altas(estado).length, 1)
})

test('alta con contenido discordante conserva solo las respuestas verificadas', async t => {
  const { env, estado } = await servicio(t)
  estado.fallo = ({ req, cuerpo, responder }) => {
    if (req.method !== 'POST' || req.url !== '/inventario/productos') return false
    responder(201, { ...cuerpo, id: 'mal', activo: true, existencia: 999 }); return true
  }
  await assert.rejects(sembrarAutotachi({ env }), error => {
    assert.match(error.message, /existencia difiere/)
    assert.equal(error.resumen.productos.length, 0)
    return true
  })
  assert.equal(altas(estado).length, 1)
})

test('CLI sale con 1 sin imprimir claves, token ni cuerpo del servidor', async t => {
  const { env, estado } = await servicio(t)
  estado.fallo = ({ responder }) => { responder(401, { detail: env.AUTOTACHI_VICTOR_PASSWORD + ' token-0' }); return true }
  const proceso = spawn(process.execPath, [fileURLToPath(new URL('./semilla-autotachi.mjs', import.meta.url))], { env: { ...process.env, ...env } })
  let salida = ''
  proceso.stdout.on('data', d => { salida += d }); proceso.stderr.on('data', d => { salida += d })
  const [codigo] = await once(proceso, 'close')
  assert.equal(codigo, 1)
  assert.equal(JSON.parse(salida).resumen.estado, 'INTERRUMPIDA')
  for (const cuenta of cuentas) assert.ok(!salida.includes(env[cuenta.variable]))
  assert.ok(!salida.includes('token-0'))
})

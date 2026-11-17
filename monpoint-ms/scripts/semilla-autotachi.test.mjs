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
  const estado = { usuarios, productos: [], empresa: null, peticiones: [], fallo: null }
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
    if (req.url === '/reportes/configuracion') {
      if (req.method === 'GET') return responder(estado.empresa ? 200 : 404, estado.empresa ?? {})
      if (req.method === 'PUT' && usuarios[i].rol === 'ADMIN') {
        estado.empresa = cuerpo
        return responder(200, cuerpo)
      }
      return responder(403, {})
    }
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

const empresaEntorno = {
  SEMILLA_EMPRESA_HABILITADA: 'true', AUTOTACHI_EMPRESA_NOMBRE: ' Auto Tachi Refacciones y Accesorios ',
  AUTOTACHI_EMPRESA_RFC: ' xaxx010101000 ', AUTOTACHI_EMPRESA_DIRECCION: ' Av. Prueba 1 ',
}
const empresaEsperada = { nombreEmpresa: 'Auto Tachi Refacciones y Accesorios', rfc: 'XAXX010101000', direccion: 'Av. Prueba 1', telefono: null }

test('empresa deshabilitada no consulta reportes ni cambia la importacion previa', async t => {
  const { env, estado } = await servicio(t)
  const r = await sembrarAutotachi({ env })
  assert.deepEqual(r.empresa, { accion: 'OMITIDA' })
  assert.ok(estado.peticiones.every(p => p.ruta !== '/reportes/configuracion'))
})

test('empresa requiere nombre y RFC antes de cualquier peticion; no inventa valores', async t => {
  const { env, estado } = await servicio(t)
  for (const variable of ['AUTOTACHI_EMPRESA_NOMBRE', 'AUTOTACHI_EMPRESA_RFC']) {
    await assert.rejects(sembrarAutotachi({ env: { ...env, ...empresaEntorno, [variable]: '' } }), new RegExp(variable))
  }
  await assert.rejects(sembrarAutotachi({ env: { ...env, SEMILLA_EMPRESA_HABILITADA: 'si' } }), /true o false/)
  assert.equal(estado.peticiones.length, 0)
})

test('404 confirmado crea empresa por API; repetir no hace PUT ni repone stock', async t => {
  const { env, estado } = await servicio(t)
  const opciones = { env: { ...env, ...empresaEntorno } }
  const r = await sembrarAutotachi(opciones)
  assert.equal(r.empresa.accion, 'CREADA')
  assert.deepEqual(estado.empresa, empresaEsperada)
  assert.equal(estado.peticiones.filter(p => p.metodo === 'PUT').length, 1)
  estado.productos[0].existencia = 1
  estado.peticiones.length = 0
  const repetida = await sembrarAutotachi(opciones)
  assert.equal(repetida.empresa.accion, 'REUTILIZADA')
  assert.equal(estado.productos[0].existencia, 1)
  assert.equal(estado.peticiones.filter(p => p.metodo === 'PUT').length, 0)
  assert.equal(altas(estado).length, 0)
})

test('empresa existente diferente o catalogo en conflicto bloquea escrituras sin deshacer ediciones', async t => {
  const { env, estado } = await servicio(t)
  estado.empresa = { ...empresaEsperada, nombreEmpresa: 'Nombre editado' }
  await assert.rejects(sembrarAutotachi({ env: { ...env, ...empresaEntorno } }), /nombreEmpresa/)
  assert.equal(estado.empresa.nombreEmpresa, 'Nombre editado')
  assert.ok(estado.peticiones.every(p => p.metodo !== 'PUT'))
  assert.equal(altas(estado).length, 0)
  estado.empresa = null
  estado.productos = [{ ...productos.at(-1), id: 'ultimo', activo: true, precio: 999 }]
  await assert.rejects(sembrarAutotachi({ env: { ...env, ...empresaEntorno } }), /precio difiere/)
  assert.equal(estado.empresa, null)
})

test('errores de lectura o validacion de empresa no se interpretan como ausencia', async t => {
  const { env, estado } = await servicio(t)
  for (const status of [403, 500]) {
    estado.fallo = ({ req, responder }) => {
      if (req.url !== '/reportes/configuracion') return false
      responder(status, {}); return true
    }
    await assert.rejects(sembrarAutotachi({ env: { ...env, ...empresaEntorno } }), new RegExp(`HTTP ${status}`))
    assert.equal(altas(estado).length, 0)
    assert.ok(estado.peticiones.every(p => p.metodo !== 'PUT'))
  }
  estado.fallo = ({ req, responder }) => {
    if (req.url !== '/reportes/configuracion') return false
    responder(200, null); return true
  }
  await assert.rejects(sembrarAutotachi({ env: { ...env, ...empresaEntorno } }), /nombreEmpresa/)
  assert.ok(estado.peticiones.every(p => p.metodo !== 'PUT'), '200 null no equivale a 404')
  estado.fallo = ({ req, responder }) => {
    if (req.url !== '/reportes/configuracion' || req.method !== 'PUT') return false
    responder(400, { detail: 'Validacion del servidor' }); return true
  }
  await assert.rejects(sembrarAutotachi({ env: { ...env, ...empresaEntorno } }), /HTTP 400/)
  assert.equal(altas(estado).length, 0)
})

test('PUT de empresa guardado sin respuesta no se repite y se verifica en la siguiente ejecucion', async t => {
  const { env, estado } = await servicio(t)
  estado.fallo = ({ req, res, cuerpo }) => {
    if (req.url !== '/reportes/configuracion' || req.method !== 'PUT') return false
    estado.empresa = cuerpo; res.destroy(); return true
  }
  await assert.rejects(sembrarAutotachi({ env: { ...env, ...empresaEntorno } }), error => {
    assert.match(error.message, /puede haberse guardado/)
    assert.equal(error.resumen.empresa.accion, 'SIN_CONFIRMAR')
    return true
  })
  assert.equal(estado.peticiones.filter(p => p.metodo === 'PUT').length, 1)
  assert.equal(altas(estado).length, 0)
  estado.peticiones.length = 0; estado.fallo = null
  const r = await sembrarAutotachi({ env: { ...env, ...empresaEntorno } })
  assert.equal(r.empresa.accion, 'REUTILIZADA')
  assert.equal(estado.peticiones.filter(p => p.metodo === 'PUT').length, 0)
})

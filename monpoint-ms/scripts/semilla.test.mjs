import test from 'node:test'
import assert from 'node:assert/strict'
import http from 'node:http'
import { once } from 'node:events'
import { spawn } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import { sembrar, productos, clientes, vendedor } from './semilla.mjs'

async function servicio(t) {
  const adminPassword = 'PruebaAdmin987!'
  const vendedorPassword = 'PruebaVendedor987!'
  const admin = { id: 'admin-1', correo: 'admin@monpoint.com', nombre: 'Administrador', rol: 'ADMIN', activo: true }
  const estado = { usuarios: [admin], clientes: [], productos: [], peticiones: [], fallo: null }
  let siguiente = 1
  const servidor = http.createServer(async (req, res) => {
    const fragmentos = []
    for await (const parte of req) fragmentos.push(parte)
    const cuerpo = fragmentos.length ? JSON.parse(Buffer.concat(fragmentos)) : null
    const ruta = req.url
    estado.peticiones.push({ metodo: req.method, ruta })
    const responder = (status, datos) => {
      res.writeHead(status, { 'Content-Type': 'application/json' })
      res.end(JSON.stringify(datos))
    }
    if (estado.fallo?.({ req, res, cuerpo, responder })) return
    if (ruta === '/auth/login') {
      const usuario = estado.usuarios.find(u => u.correo === cuerpo.correo)
      const password = cuerpo.correo === admin.correo ? adminPassword : vendedorPassword
      return responder(usuario && cuerpo.password === password ? 200 : 401,
        usuario && cuerpo.password === password ? { token: usuario.rol, rol: usuario.rol } : { detail: 'Credenciales inválidas' })
    }
    if (req.headers.authorization !== 'Bearer ADMIN') return responder(403, {})
    if (ruta === '/auth/me') return responder(200, admin)
    if (ruta === '/auth/usuarios') return responder(200, estado.usuarios)
    const coleccion = { '/auth/register': 'usuarios', '/clientes': 'clientes', '/inventario/productos': 'productos' }[ruta]
    if (!coleccion) return responder(404, {})
    if (req.method === 'GET') return responder(200, estado[coleccion])
    if (req.method !== 'POST') return responder(405, {})
    const { password, ...datos } = cuerpo
    const creado = { ...datos, id: `id-${siguiente++}`, ...(coleccion !== 'clientes' ? { activo: true } : {}) }
    estado[coleccion].push(creado)
    responder(201, creado)
  })
  servidor.listen(0, '127.0.0.1')
  await once(servidor, 'listening')
  t.after(() => { servidor.closeAllConnections(); servidor.close() })
  const env = { SEMILLA_GATEWAY_URL: `http://127.0.0.1:${servidor.address().port}`,
    ADMIN_DEFAULT_PASSWORD: adminPassword, SEMILLA_VENDEDOR_PASSWORD: vendedorPassword }
  return { estado, env }
}

const altas = estado => estado.peticiones.filter(p => p.metodo === 'POST' && p.ruta !== '/auth/login')

test('base sin datos de negocio: crea vendedor, dos clientes y tres productos; nunca envía ventas', async t => {
  const { estado, env } = await servicio(t)
  const r = await sembrar({ env })
  assert.equal(r.estado, 'COMPLETADA')
  assert.deepEqual(r.usuarios.map(u => u.rol), ['ADMIN', 'VENDEDOR'])
  assert.equal(estado.clientes.length, 2)
  assert.equal(estado.productos.length, 3)
  assert.ok(estado.productos.every(p => p.existencia === 10 && p.umbralStock === 5))
  assert.equal(altas(estado).length, 6)
  assert.ok(estado.peticiones.every(p => !p.ruta.startsWith('/ventas')))
  assert.ok(!JSON.stringify(r).includes(env.ADMIN_DEFAULT_PASSWORD))
  assert.ok(!JSON.stringify(r).includes(env.SEMILLA_VENDEDOR_PASSWORD))
})

test('segunda ejecución reutiliza todos los ids sin altas ni reposición de existencias', async t => {
  const { estado, env } = await servicio(t)
  const primero = await sembrar({ env })
  estado.productos[0].existencia = 3
  estado.peticiones.length = 0
  const segundo = await sembrar({ env })
  for (const coleccion of ['usuarios', 'clientes', 'productos']) {
    assert.deepEqual(segundo[coleccion].map(x => x.id), primero[coleccion].map(x => x.id))
    assert.ok(segundo[coleccion].every(x => x.accion === 'REUTILIZADO'))
  }
  assert.equal(segundo.productos[0].existencia, 3)
  assert.equal(altas(estado).length, 0)
})

test('contraseña ADMIN incorrecta detiene la ejecución antes de leer o crear datos', async t => {
  const { estado, env } = await servicio(t)
  await assert.rejects(sembrar({ env: { ...env, ADMIN_DEFAULT_PASSWORD: 'Incorrecta123!' } }), error => {
    assert.match(error.message, /HTTP 401.*Autenticación/)
    assert.equal(error.resumen.estado, 'INTERRUMPIDA')
    assert.equal(error.resumen.productos.length, 0)
    return true
  })
  assert.deepEqual(estado.peticiones, [{ metodo: 'POST', ruta: '/auth/login' }])
})

test('CLI falla con código 1 y no imprime contraseñas ni tokens', async t => {
  const { estado, env } = await servicio(t)
  const proceso = spawn(process.execPath, [fileURLToPath(new URL('./semilla.mjs', import.meta.url))], {
    env: { ...process.env, ...env, ADMIN_DEFAULT_PASSWORD: 'ClaveErronea456!' },
  })
  let salida = ''
  proceso.stdout.on('data', d => { salida += d })
  proceso.stderr.on('data', d => { salida += d })
  const [codigo] = await once(proceso, 'close')
  assert.equal(codigo, 1)
  assert.match(JSON.parse(salida).error, /HTTP 401/)
  assert.ok(!salida.includes('ClaveErronea456!'))
  assert.equal(altas(estado).length, 0)
})

test('un listado inaccesible impide todas las altas', async t => {
  const { estado, env } = await servicio(t)
  estado.fallo = ({ req, responder }) => {
    if (req.url !== '/inventario/productos') return false
    responder(503, { detail: 'No disponible' }); return true
  }
  await assert.rejects(sembrar({ env }), /HTTP 503/)
  assert.equal(altas(estado).length, 0)
})

test('duplicados por nombre se detectan antes de crear el vendedor o los clientes', async t => {
  const { estado, env } = await servicio(t)
  estado.productos.push(...['p1', 'p2'].map(id => ({ ...productos[0], id, activo: true })))
  await assert.rejects(sembrar({ env }), /Identidad ambigua/)
  assert.equal(altas(estado).length, 0)
})

test('un precio cambiado se informa como conflicto sin sobrescribir ni continuar', async t => {
  const { estado, env } = await servicio(t)
  estado.productos.push({ ...productos[0], id: 'p1', activo: true, precio: 99 })
  await assert.rejects(sembrar({ env }), /precio difiere/)
  assert.equal(altas(estado).length, 0)
  assert.equal(estado.productos[0].precio, 99)
})

test('el vendedor existente con otra contraseña se detecta antes de sembrar catálogos', async t => {
  const { estado, env } = await servicio(t)
  estado.usuarios.push({ ...vendedor, id: 'v1', activo: true })
  await assert.rejects(sembrar({ env: { ...env, SEMILLA_VENDEDOR_PASSWORD: 'OtraClave123!' } }), /HTTP 401/)
  assert.equal(altas(estado).length, 0)
})

test('caída después de guardar un cliente: no repite el POST y otra ejecución recupera su id', async t => {
  const { estado, env } = await servicio(t)
  estado.fallo = ({ req, res, cuerpo }) => {
    if (req.url !== '/clientes' || req.method !== 'POST') return false
    estado.clientes.push({ ...cuerpo, id: 'cliente-guardado-sin-respuesta' })
    res.destroy(); return true
  }
  await assert.rejects(sembrar({ env }), error => {
    assert.match(error.message, /resultado del alta es incierto/)
    assert.equal(error.resumen.usuarios[1].accion, 'CREADO')
    assert.equal(error.resumen.clientes.length, 0)
    return true
  })
  assert.equal(altas(estado).filter(p => p.ruta === '/clientes').length, 1)
  estado.fallo = null
  const r = await sembrar({ env })
  assert.equal(r.clientes[0].id, 'cliente-guardado-sin-respuesta')
  assert.equal(r.clientes[0].accion, 'REUTILIZADO')
  assert.equal(estado.clientes.length, clientes.length)
})

test('vendedor inactivo no listado: un 409 detiene las altas restantes sin intentar reactivar', async t => {
  const { estado, env } = await servicio(t)
  estado.fallo = ({ req, responder }) => {
    if (req.url !== '/auth/register') return false
    responder(409, {}); return true
  }
  await assert.rejects(sembrar({ env }), /HTTP 409/)
  assert.deepEqual(altas(estado).map(p => p.ruta), ['/auth/register'])
})

test('un POST colgado agota el plazo, no se reintenta y deja la operación identificada', async t => {
  const { estado, env } = await servicio(t)
  estado.fallo = ({ req }) => req.url === '/auth/register'
  await assert.rejects(sembrar({ env, timeoutMs: 100 }), error => {
    assert.match(error.message, /resultado del alta es incierto/)
    assert.equal(error.resumen.operacion, 'POST /auth/register')
    return true
  })
  assert.equal(altas(estado).length, 1)
})

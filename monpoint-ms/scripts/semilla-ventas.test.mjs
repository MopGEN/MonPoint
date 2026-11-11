import test from 'node:test'
import assert from 'node:assert/strict'
import http from 'node:http'
import { once } from 'node:events'
import { spawn } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import { sembrarVentas, productos, clientes } from './semilla.mjs'

async function servicio(t) {
  const estado = { peticiones: [], ventas: [], fallo: null,
    productos: productos.map((p, i) => ({ ...p, id: `p${i}`, activo: true })),
    clientes: clientes.map((c, i) => ({ ...c, id: `c${i}` })) }
  const token = 'TOKEN_NO_IMPRIMIR_123'
  const password = 'ClaveNoImprimir123!'
  const servidor = http.createServer(async (req, res) => {
    const partes = []
    for await (const p of req) partes.push(p)
    const cuerpo = partes.length ? JSON.parse(Buffer.concat(partes)) : null
    const responder = (status, datos) => { res.writeHead(status, { 'Content-Type': 'application/json' }); res.end(JSON.stringify(datos)) }
    estado.peticiones.push({ metodo: req.method, ruta: req.url, ...(req.url === '/ventas' ? { cuerpo } : {}) })
    if (estado.fallo?.({ req, res, cuerpo, responder })) return
    if (req.url === '/auth/login') return responder(200, { token, rol: 'ADMIN' })
    if (req.headers.authorization !== `Bearer ${token}`) return responder(401, {})
    if (req.url === '/auth/me') return responder(200, { id: 'admin', correo: 'admin@monpoint.com', rol: 'ADMIN', activo: true })
    if (req.url === '/notificaciones') return responder(200, [])
    if (req.url.startsWith('/reportes/ventas/agregado?')) return responder(200, { puntos: [], total: 0 })
    if (req.url === '/inventario/productos') return responder(200, estado.productos)
    if (req.url === '/clientes') return responder(200, estado.clientes)
    if (req.url === '/ventas' && req.method === 'POST') {
      const id = `v${estado.ventas.length + 1}`
      const venta = { ...cuerpo, id, folio: 100 + estado.ventas.length, estado: 'PENDIENTE', consultas: 0,
        total: cuerpo.lineas.reduce((s, l) => s + estado.productos.find(p => p.id === l.productoId).precio * l.cantidad, 0) }
      estado.ventas.push(venta)
      return responder(202, { id, folio: venta.folio, estado: 'PENDIENTE' })
    }
    if (req.url.startsWith('/ventas/') && req.method === 'GET') {
      const venta = estado.ventas.find(v => `/ventas/${v.id}` === req.url)
      if (!venta) return responder(404, {})
      if (++venta.consultas >= 2 && venta.estado === 'PENDIENTE') {
        venta.estado = 'CONFIRMADA'
        for (const l of venta.lineas) estado.productos.find(p => p.id === l.productoId).existencia -= l.cantidad
      }
      return responder(200, venta)
    }
    return responder(404, {})
  })
  servidor.listen(0, '127.0.0.1')
  await once(servidor, 'listening')
  t.after(() => { servidor.closeAllConnections(); servidor.close() })
  const env = { SEMILLA_GATEWAY_URL: `http://127.0.0.1:${servidor.address().port}`, ADMIN_DEFAULT_PASSWORD: password }
  return { estado, env, token, password, ejecutar: opciones => sembrarVentas({ env, intervaloMs: 5, ...opciones }) }
}
const posts = estado => estado.peticiones.filter(p => p.metodo === 'POST' && p.ruta === '/ventas')

test('confirma tres ventas seriales con clientes reales y una sin cliente; deja tinta en el umbral', async t => {
  const { estado, ejecutar } = await servicio(t)
  const r = await ejecutar()
  assert.equal(r.estado, 'COMPLETADA')
  assert.equal(r.ventaEnCurso, null)
  assert.deepEqual(r.ventasConfirmadas.map(v => v.estado), ['CONFIRMADA', 'CONFIRMADA', 'CONFIRMADA'])
  assert.deepEqual(r.ventasConfirmadas.map(v => v.clienteId), ['c0', 'c1', null])
  assert.deepEqual(r.ventasConfirmadas.map(v => v.total), [205.75, 151, 120])
  assert.deepEqual(estado.productos.map(p => p.existencia), [5, 8, 9])
  assert.equal(posts(estado).length, 3)
  const secuencia = estado.peticiones.filter(p => p.ruta.startsWith('/ventas')).map(p => `${p.metodo} ${p.ruta}`)
  assert.deepEqual(secuencia, ['POST /ventas', 'GET /ventas/v1', 'GET /ventas/v1',
    'POST /ventas', 'GET /ventas/v2', 'GET /ventas/v2', 'POST /ventas', 'GET /ventas/v3', 'GET /ventas/v3'])
})

test('los dos 400 conocidos repiten exactamente la misma venta hasta que llegan ambas réplicas', async t => {
  const { estado, ejecutar } = await servicio(t)
  let rechazadas = 0
  estado.fallo = ({ req, responder }) => {
    if (req.url !== '/ventas' || rechazadas >= 2) return false
    responder(400, { detail: rechazadas++ === 0 ? 'El producto no está disponible o está inactivo: p0'
      : 'el cliente no está disponible; si acaba de darlo de alta, inténtelo de nuevo en unos segundos' })
    return true
  }
  const r = await ejecutar()
  assert.equal(r.ventasConfirmadas.length, 3)
  assert.deepEqual(r.rechazosReplica, { producto: 1, cliente: 1 })
  assert.equal(posts(estado).length, 5)
  assert.deepEqual(posts(estado)[0].cuerpo, posts(estado)[1].cuerpo)
  assert.deepEqual(posts(estado)[0].cuerpo, posts(estado)[2].cuerpo)
})

for (const detail of ['Importe inválido', 'El producto no está disponible o está inactivo: otro-id',
  'el cliente no está disponible', null]) {
  test(`otro 400 no se reintenta: ${detail}`, async t => {
    const { estado, ejecutar } = await servicio(t)
    estado.fallo = ({ req, responder }) => {
      if (req.url !== '/ventas') return false
      responder(400, { detail }); return true
    }
    await assert.rejects(ejecutar(), e => e.resumen.ventaEnCurso.estado === 'RECHAZADA_HTTP' && /no reintentable/.test(e.message))
    assert.equal(posts(estado).length, 1)
  })
}

test('la ausencia persistente de réplica agota el plazo sin consumir folios', async t => {
  const { estado, ejecutar } = await servicio(t)
  estado.fallo = ({ req, responder }) => {
    if (req.url !== '/ventas') return false
    responder(400, { detail: 'El producto no está disponible o está inactivo: p0' }); return true
  }
  await assert.rejects(ejecutar({ plazoMs: 70, intervaloMs: 150 }), e => {
    assert.match(e.message, /plazo agotado/)
    assert.equal(e.resumen.ventasConfirmadas.length, 0)
    assert.equal(e.resumen.ventaEnCurso.estado, 'RECHAZADA_HTTP')
    return true
  })
  assert.equal(posts(estado).length, 1)
  assert.equal(estado.ventas.length, 0)
})

test('202 PENDIENTE nunca se cuenta como éxito y el sondeo tiene límite', async t => {
  const { estado, ejecutar } = await servicio(t)
  estado.fallo = ({ req, responder }) => {
    if (!req.url.startsWith('/ventas/')) return false
    responder(200, { id: 'v1', folio: 100, estado: 'PENDIENTE' }); return true
  }
  await assert.rejects(ejecutar({ plazoMs: 70 }), e => {
    assert.equal(e.resumen.ventasConfirmadas.length, 0)
    assert.deepEqual([e.resumen.ventaEnCurso.id, e.resumen.ventaEnCurso.folio, e.resumen.ventaEnCurso.estado], ['v1', 100, 'PENDIENTE'])
    return /plazo agotado/.test(e.message)
  })
  assert.equal(posts(estado).length, 1)
})

test('el plazo de la saga no se reinicia tras esperar la réplica', async t => {
  const { estado, ejecutar } = await servicio(t)
  let inicio
  estado.fallo = ({ req, responder }) => {
    if (req.url === '/ventas' && !inicio) {
      inicio = performance.now()
      const timer = setTimeout(() => responder(400, { detail: 'El producto no está disponible o está inactivo: p0' }), 250)
      t.after(() => clearTimeout(timer))
      return true
    }
    return req.url.startsWith('/ventas/') // GET colgado después del 202.
  }
  await assert.rejects(ejecutar({ plazoMs: 400, timeoutMs: 1000 }), e => {
    assert.equal(e.resumen.ventaEnCurso.estado, 'PENDIENTE')
    assert.equal(e.resumen.ventaEnCurso.folio, 100)
    return /plazo agotado/.test(e.message)
  })
  assert.ok(performance.now() - inicio < 600, 'El sondeo debe usar el presupuesto restante, no otros 400 ms')
  assert.equal(posts(estado).length, 2) // un rechazo conocido y una sola aceptación
})

test('una CANCELADA detiene el lote y conserva su id y folio para consultar el motivo', async t => {
  const { estado, ejecutar } = await servicio(t)
  estado.fallo = ({ req, responder }) => {
    if (!req.url.startsWith('/ventas/')) return false
    responder(200, { id: 'v1', folio: 100, estado: 'CANCELADA' }); return true
  }
  await assert.rejects(ejecutar(), e => e.resumen.ventaEnCurso.estado === 'CANCELADA' && e.resumen.ventaEnCurso.folio === 100)
  assert.equal(posts(estado).length, 1)
})

for (const fallo of ['red', 'timeout', '500', '202-incompleto', '202-ilegible']) {
  test(`POST ${fallo} tras una confirmada: conserva la frontera y no repite ni avanza`, async t => {
    const { estado, ejecutar } = await servicio(t)
    estado.fallo = ({ req, res, responder }) => {
      if (req.url !== '/ventas' || estado.ventas.length !== 1) return false
      if (fallo === 'red') res.destroy()
      if (fallo === '500') responder(500, {})
      if (fallo === '202-incompleto') responder(202, { id: 'aceptada-sin-folio', estado: 'PENDIENTE' })
      if (fallo === '202-ilegible') { res.writeHead(202); res.end('{') }
      return true // timeout: queda sin responder
    }
    await assert.rejects(ejecutar({ timeoutMs: 100 }), e => {
      assert.equal(e.resumen.estado, 'INTERRUMPIDA')
      assert.deepEqual(e.resumen.ventasConfirmadas.map(v => v.folio), [100])
      assert.equal(e.resumen.ventaEnCurso.orden, 2)
      assert.equal(e.resumen.ventaEnCurso.estado, 'INCIERTA')
      if (fallo === '202-incompleto') assert.equal(e.resumen.ventaEnCurso.id, 'aceptada-sin-folio')
      return true
    })
    assert.equal(posts(estado).length, 2)
  })
}

test('fallos transitorios del GET se reintentan sin reenviar el POST', async t => {
  const { estado, ejecutar } = await servicio(t)
  let fallos = 0
  estado.fallo = ({ req, res, responder }) => {
    if (req.url !== '/ventas/v1' || fallos >= 2) return false
    if (fallos++ === 0) responder(503, {})
    else res.destroy()
    return true
  }
  assert.equal((await ejecutar()).estado, 'COMPLETADA')
  assert.equal(posts(estado).length, 3)
})

for (const status of [401, 403, 404]) {
  test(`GET ${status} detiene el lote con la venta aceptada aún identificable`, async t => {
    const { estado, ejecutar } = await servicio(t)
    estado.fallo = ({ req, responder }) => {
      if (!req.url.startsWith('/ventas/')) return false
      responder(status, {}); return true
    }
    await assert.rejects(ejecutar(), e => e.resumen.ventaEnCurso.id === 'v1' && e.resumen.ventaEnCurso.estado === 'PENDIENTE')
    assert.equal(posts(estado).length, 1)
  })
}

test('un consumidor inaccesible impide todas las ventas', async t => {
  const { estado, ejecutar } = await servicio(t)
  estado.fallo = ({ req, responder }) => {
    if (!req.url.startsWith('/reportes/')) return false
    responder(503, {}); return true
  }
  await assert.rejects(ejecutar({ plazoMs: 60 }), /plazo agotado/)
  assert.equal(posts(estado).length, 0)
})

test('stock consumido, cliente ausente o identidad ambigua se detectan antes del primer POST', async t => {
  for (const modificar of [e => { e.productos[2].existencia = 9 }, e => { e.clientes = [] },
    e => { e.productos.push({ ...e.productos[0], id: 'duplicado' }) }]) {
    const { estado, ejecutar } = await servicio(t)
    modificar(estado)
    await assert.rejects(ejecutar())
    assert.equal(posts(estado).length, 0)
  }
})

test('repetir el lote tras completarlo se rechaza por stock cambiado, sin prometer idempotencia', async t => {
  const { estado, ejecutar } = await servicio(t)
  await ejecutar()
  estado.peticiones.length = 0
  await assert.rejects(ejecutar(), /existencia inicial/)
  assert.equal(posts(estado).length, 0)
})

test('una identidad de consulta distinta no se cuenta como venta confirmada', async t => {
  const { estado, ejecutar } = await servicio(t)
  estado.fallo = ({ req, responder }) => {
    if (!req.url.startsWith('/ventas/')) return false
    responder(200, { id: 'otra', folio: 999, estado: 'CONFIRMADA' }); return true
  }
  await assert.rejects(ejecutar(), e => e.resumen.ventasConfirmadas.length === 0 && /identidad discordante/.test(e.message))
  assert.equal(posts(estado).length, 1)
})

test('CLI --ventas conserva el resumen parcial sin imprimir cuerpo de error, contraseña ni token', async t => {
  const { estado, env, token, password } = await servicio(t)
  estado.fallo = ({ req, responder }) => {
    if (req.url !== '/ventas' || estado.ventas.length !== 1) return false
    responder(500, { detail: password + token }); return true
  }
  const proceso = spawn(process.execPath, [fileURLToPath(new URL('./semilla.mjs', import.meta.url)), '--ventas'],
    { env: { ...process.env, ...env } })
  let salida = ''
  proceso.stdout.on('data', d => { salida += d })
  proceso.stderr.on('data', d => { salida += d })
  const [codigo] = await once(proceso, 'close')
  assert.equal(codigo, 1)
  assert.equal(JSON.parse(salida).resumen.ventasConfirmadas.length, 1)
  assert.ok(!salida.includes(token) && !salida.includes(password))
  assert.equal(posts(estado).length, 2)
})

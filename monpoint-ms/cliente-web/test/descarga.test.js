import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

let modulo = 0
async function cliente () {
  // Vite sustituye import.meta.env al compilar. En Node se fija solo el origen;
  // el resto de la funcion de produccion se ejecuta tal cual.
  const fuente = readFileSync(new URL('../src/api/gateway.js', import.meta.url), 'utf8')
  assert.ok(fuente.includes('import.meta.env.VITE_GATEWAY_URL'))
  return import(`data:text/javascript;base64,${Buffer.from(fuente.replace('import.meta.env.VITE_GATEWAY_URL', JSON.stringify('http://gateway.test'))).toString('base64')}#${modulo++}`)
}

test('descargar envia el token y entrega los bytes completos sin cookies', async (t) => {
  const { gateway, configurarGateway } = await cliente()
  configurarGateway({ obtenerToken: () => 'token-de-prueba' })
  const control = new AbortController()
  t.mock.method(globalThis, 'fetch', async (url, opciones) => {
    assert.equal(url, 'http://gateway.test/reportes/productos/listado')
    assert.equal(opciones.headers.Authorization, 'Bearer token-de-prueba')
    assert.equal(opciones.credentials, 'omit')
    assert.equal(opciones.signal, control.signal)
    return new Response('%PDF-cuerpo-de-prueba', { headers: { 'Content-Type': 'application/pdf' } })
  })
  const blob = await gateway.descargar('/reportes/productos/listado', { senal: control.signal })
  assert.equal(blob.type, 'application/pdf')
  assert.equal(await blob.text(), '%PDF-cuerpo-de-prueba')
})

test('descargar convierte un 403 ProblemDetail en error legible sin cerrar sesion', async (t) => {
  const { gateway, configurarGateway } = await cliente()
  let expiraciones = 0
  configurarGateway({ alExpirarSesion: () => { expiraciones++ } })
  t.mock.method(globalThis, 'fetch', async () => new Response(JSON.stringify({ detail: 'Solo ADMIN' }), {
    status: 403, headers: { 'Content-Type': 'application/problem+json' },
  }))
  await assert.rejects(gateway.descargar('/reportes/clientes/listado'), (e) => e.estado === 403 && e.message === 'Solo ADMIN')
  assert.equal(expiraciones, 0)
})

test('descargar expira la sesion ante un 401 sin producir un documento', async (t) => {
  const { gateway, configurarGateway } = await cliente()
  let expiraciones = 0
  configurarGateway({ alExpirarSesion: () => { expiraciones++ } })
  t.mock.method(globalThis, 'fetch', async () => new Response('', { status: 401 }))
  await assert.rejects(gateway.descargar('/reportes/clientes/listado'), (e) => e.estado === 401)
  assert.equal(expiraciones, 1)
})

test('un fallo de conexion al descargar se presenta como error de red', async (t) => {
  const { gateway } = await cliente()
  t.mock.method(globalThis, 'fetch', async () => { throw new TypeError('red caida') })
  await assert.rejects(gateway.descargar('/pdf'), (e) => e.estado === 0 && e.esDeRed)
})

test('cancelar una descarga conserva AbortError', async (t) => {
  const { gateway } = await cliente()
  const aborto = new DOMException('Cancelada', 'AbortError')
  t.mock.method(globalThis, 'fetch', async () => { throw aborto })
  await assert.rejects(gateway.descargar('/pdf'), (e) => e === aborto)
})

test('un corte tras recibir cabeceras no entrega un blob parcial como exito', async (t) => {
  const { gateway } = await cliente()
  t.mock.method(globalThis, 'fetch', async () => ({ ok: true, blob: async () => { throw new TypeError('cuerpo interrumpido') } }))
  await assert.rejects(gateway.descargar('/pdf'), (e) => e.estado === 0 && e.esDeRed)
})

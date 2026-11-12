import { salirDesdeMenu } from './concha-fixture.mjs'
/**
 * CRUD real de Clientes. Sin intercepciones; requiere Vite y el conjunto vivo.
 * Crea un vendedor y dos clientes, y elimina solo esos clientes con ADMIN.
 * No es idempotente ni forma parte de npm test. Solo entornos de prueba.
 */
import assert from 'node:assert/strict'
import { mkdir, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'
const modulo = await import(process.env.PLAYWRIGHT_MODULE ?? 'playwright')
const { chromium } = modulo.default ?? modulo
const BASE = process.env.BASE_URL ?? 'http://localhost:5173'
const API = process.env.GATEWAY_URL ?? 'http://localhost:8080'
const SALIDA = resolve(process.env.SALIDA ?? '/tmp/clientes-real')
await mkdir(SALIDA, { recursive: true })
const resultado = { inicio: new Date().toISOString(), cliente: BASE, gateway: API, casos: [], errores: [] }
let token
async function api (ruta, { metodo = 'GET', cuerpo, credencial = token } = {}) {
  const r = await fetch(`${API}${ruta}`, {
    method: metodo,
    headers: { Authorization: `Bearer ${credencial}`, ...(cuerpo ? { 'Content-Type': 'application/json' } : {}) },
    ...(cuerpo ? { body: JSON.stringify(cuerpo) } : {}),
    signal: AbortSignal.timeout(10000),
  })
  return { estado: r.status, datos: r.status === 204 ? null : await r.json() }
}
const navegador = await chromium.launch({
  ...(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {}),
  args: process.env.CHROMIUM_NO_SANDBOX === '1' ? ['--no-sandbox'] : [],
})
const pagina = await navegador.newPage({ viewport: { width: 1280, height: 900 } })
pagina.on('pageerror', (e) => resultado.errores.push(e.message))
async function entrar (correo, password) {
  const respuesta = pagina.waitForResponse((r) => r.url() === `${API}/auth/login` && r.request().method() === 'POST')
  await pagina.getByLabel('Correo', { exact: true }).fill(correo)
  await pagina.getByLabel('Contraseña', { exact: true }).fill(password)
  await pagina.getByRole('button', { name: 'Entrar', exact: true }).click()
  const r = await respuesta
  assert.equal(r.status(), 200)
  await pagina.waitForURL('**/caja')
  return (await r.json()).token
}
async function abrirClientes () {
  await pagina.getByRole('link', { name: 'Clientes', exact: false }).click()
  await pagina.waitForURL('**/clientes')
  await pagina.locator('.estado[aria-busy="false"]').waitFor()
}
async function salir () {
  await salirDesdeMenu(pagina)
  await pagina.waitForURL('**/login')
}
async function guardar (metodo, ruta, modal) {
  const respuesta = pagina.waitForResponse((r) => r.url() === `${API}${ruta}` && r.request().method() === metodo)
  await modal.getByRole('button', { name: 'Guardar', exact: true }).click()
  const r = await respuesta
  assert.equal(r.status(), metodo === 'POST' ? 201 : 200)
  const cliente = await r.json()
  await modal.waitFor({ state: 'hidden' })
  await pagina.locator('.estado[aria-busy="false"]').waitFor()
  await pagina.getByText(cliente.nombre, { exact: true }).waitFor()
  return cliente
}
try {
  await pagina.goto(`${BASE}/login`)
  const claveAdmin = process.env.ADMIN_DEFAULT_PASSWORD || 'Admin123!'
  token = await entrar('admin@monpoint.com', claveAdmin)
  const etiqueta = Date.now().toString()
  const prefijo = `Revision clientes ${etiqueta}`
  const correo = `clientes.${etiqueta}@monpoint.test`
  const claveVendedor = process.env.PRUEBA_VENDEDOR_PASSWORD || 'Vendedor123!'
  const vendedor = await api('/auth/register', { metodo: 'POST', cuerpo: {
    nombre: 'Vendedor prueba clientes', correo, password: claveVendedor, rol: 'VENDEDOR',
  } })
  assert.equal(vendedor.estado, 201)
  await salir()
  const tokenVendedor = await entrar(correo, claveVendedor)
  await abrirClientes()
  assert.equal(await pagina.getByRole('button', { name: 'Eliminar', exact: true }).count(), 0)
  await pagina.getByRole('button', { name: 'Nuevo cliente', exact: true }).click()
  const modal = pagina.getByRole('dialog', { name: 'Nuevo cliente', exact: true })
  await modal.getByLabel('Nombre', { exact: true }).fill(`${prefijo} vendedor`)
  const altaVendedor = await guardar('POST', '/clientes', modal)
  assert.equal(altaVendedor.correo, null)
  resultado.altaVendedor = altaVendedor
  let fila = pagina.locator('tbody tr').filter({ hasText: altaVendedor.nombre })
  await fila.getByRole('button', { name: 'Editar', exact: true }).click()
  const edicion = pagina.getByRole('dialog', { name: 'Editar cliente', exact: true })
  await edicion.getByLabel('Teléfono (opcional)', { exact: true }).fill('5544332211')
  resultado.edicionVendedor = await guardar('PUT', `/clientes/${altaVendedor.id}`, edicion)
  assert.equal(resultado.edicionVendedor.telefono, '5544332211')
  const prohibida = await api(`/clientes/${altaVendedor.id}`, { metodo: 'DELETE', credencial: tokenVendedor })
  assert.equal(prohibida.estado, 403)
  resultado.borradoVendedor = prohibida
  assert.equal((await api(`/clientes/${altaVendedor.id}`, { credencial: tokenVendedor })).estado, 200)
  await pagina.getByLabel('Buscar por nombre, correo o teléfono', { exact: true }).fill(prefijo)
  await pagina.screenshot({ path: `${SALIDA}/01-vendedor.png`, fullPage: true })
  resultado.casos.push('VENDEDOR: alta 201 y edicion 200 desde la pantalla; DELETE 403 por API')

  await salir()
  token = await entrar('admin@monpoint.com', claveAdmin)
  await abrirClientes()
  await pagina.getByRole('button', { name: 'Nuevo cliente', exact: true }).click()
  await modal.getByLabel('Nombre', { exact: true }).fill(`${prefijo} admin`)
  // Pasa la comprobacion basica local, pero @Email lo rechaza: comprobar
  // useAccion y el mapa campos con una respuesta real, no fabricada.
  await modal.getByLabel('Correo (opcional)', { exact: true }).fill('ana@..example.test')
  const validacion = pagina.waitForResponse((r) => r.url() === `${API}/clientes` && r.request().method() === 'POST')
  await modal.getByRole('button', { name: 'Guardar', exact: true }).click()
  const rechazada = await validacion
  assert.equal(rechazada.status(), 400)
  resultado.validacionRemota = await rechazada.json()
  assert.ok(resultado.validacionRemota.campos.correo)
  await modal.locator('#correo-error').waitFor()
  assert.equal(await modal.locator('#correo').getAttribute('aria-invalid'), 'true')
  assert.equal(await modal.locator('[role="alert"]').count(), 0)
  resultado.casos.push('400 real con campos.correo: error junto al campo y sin aviso generico')
  await modal.getByLabel('Correo (opcional)', { exact: true }).fill('"Ana Ruiz"@example.test')
  const altaAdmin = await guardar('POST', '/clientes', modal)
  assert.equal(altaAdmin.correo, '"Ana Ruiz"@example.test')
  resultado.altaAdmin = altaAdmin
  fila = pagina.locator('tbody tr').filter({ hasText: altaAdmin.nombre })
  await fila.getByRole('button', { name: 'Editar', exact: true }).click()
  await edicion.getByLabel('Dirección (opcional)', { exact: true }).fill('Calle de prueba 1')
  resultado.edicionAdmin = await guardar('PUT', `/clientes/${altaAdmin.id}`, edicion)
  assert.equal(resultado.edicionAdmin.correo, altaAdmin.correo)
  await pagina.getByLabel('Buscar por nombre, correo o teléfono', { exact: true }).fill(prefijo)
  await pagina.setViewportSize({ width: 390, height: 844 })
  assert.ok(await pagina.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth))
  await pagina.screenshot({ path: `${SALIDA}/02-admin-movil.png`, fullPage: true })
  resultado.casos.push('ADMIN: alta 201 y edicion 200 con correo local citado conservado')

  resultado.eliminados = []
  for (const cliente of [altaAdmin, altaVendedor]) {
    const filaEliminar = pagina.locator('tbody tr').filter({ hasText: cliente.nombre })
    await filaEliminar.getByRole('button', { name: 'Eliminar', exact: true }).click()
    const borrado = pagina.getByRole('dialog', { name: 'Eliminar cliente', exact: true })
    await borrado.getByRole('button', { name: 'Cancelar', exact: true }).click()
    assert.equal((await api(`/clientes/${cliente.id}`)).estado, 200)
    await filaEliminar.getByRole('button', { name: 'Eliminar', exact: true }).click()
    const respuesta = pagina.waitForResponse((r) => r.url() === `${API}/clientes/${cliente.id}` && r.request().method() === 'DELETE')
    await borrado.getByRole('button', { name: 'Eliminar', exact: true }).click()
    assert.equal((await respuesta).status(), 204)
    await borrado.waitFor({ state: 'hidden' })
    await pagina.locator('.estado[aria-busy="false"]').waitFor()
    assert.equal(await filaEliminar.count(), 0)
    const directa = await api(`/clientes/${cliente.id}`)
    assert.equal(directa.estado, 404)
    const listado = await api('/clientes')
    assert.equal(listado.estado, 200)
    assert.ok(!listado.datos.some((c) => c.id === cliente.id))
    resultado.eliminados.push({ id: cliente.id, DELETE: 204, GET: directa.estado })
  }
  await pagina.screenshot({ path: `${SALIDA}/03-eliminados.png`, fullPage: true })
  resultado.casos.push('ADMIN elimina: 204, ausencia tras recarga y consulta directa 404')
  assert.deepEqual(resultado.errores, [])
  resultado.resultado = 'PASS'
} catch (e) {
  resultado.resultado = 'FAIL'
  resultado.fallo = e.message
  await pagina.screenshot({ path: `${SALIDA}/fallo.png`, fullPage: true }).catch(() => {})
  throw e
} finally {
  resultado.fin = new Date().toISOString()
  await writeFile(`${SALIDA}/resultado.json`, JSON.stringify(resultado, null, 2))
  await navegador.close()
}
console.log('Clientes reales:', resultado.resultado, resultado.casos)

/**
 * CRUD de Productos contra el Gateway real, sin interceptar peticiones.
 * Requiere Vite en localhost:5173 y los servicios ya levantados.
 * Crea un producto y un vendedor; da de baja solo su propio producto.
 * No es idempotente ni forma parte de npm test. Solo para entornos de prueba.
 */
import assert from 'node:assert/strict'
import { mkdir, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'
const modulo = await import(process.env.PLAYWRIGHT_MODULE ?? 'playwright')
const { chromium } = modulo.default ?? modulo
const BASE = process.env.BASE_URL ?? 'http://localhost:5173'
const API = process.env.GATEWAY_URL ?? 'http://localhost:8080'
const SALIDA = resolve(process.env.SALIDA ?? '/tmp/productos-real')
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
  await pagina.getByLabel('Contrasena', { exact: true }).fill(password)
  await pagina.getByRole('button', { name: 'Entrar', exact: true }).click()
  const r = await respuesta
  assert.equal(r.status(), 200)
  await pagina.waitForURL('**/caja')
  return (await r.json()).token
}
async function abrirProductos () {
  await pagina.getByRole('link', { name: 'Productos', exact: false }).click()
  await pagina.waitForURL('**/productos')
  await pagina.locator('.cargando').waitFor({ state: 'detached' })
}
try {
  await pagina.goto(`${BASE}/login`)
  const claveAdmin = process.env.ADMIN_DEFAULT_PASSWORD || 'Admin123!'
  token = await entrar('admin@monpoint.com', claveAdmin)
  await abrirProductos()
  const nombre = `Revision productos ${Date.now()}`
  await pagina.getByRole('button', { name: 'Nuevo producto', exact: true }).click()
  const modal = pagina.getByRole('dialog', { name: 'Nuevo producto', exact: true })
  await modal.waitFor()
  await modal.getByLabel('Nombre', { exact: true }).fill(nombre)
  await modal.getByLabel('Precio', { exact: true }).fill('41.15')
  await modal.getByLabel('Existencia', { exact: true }).fill('10')
  const respuestaAlta = pagina.waitForResponse((r) => r.url() === `${API}/inventario/productos` && r.request().method() === 'POST')
  await modal.getByRole('button', { name: 'Guardar', exact: true }).click()
  const alta = await respuestaAlta
  assert.equal(alta.status(), 201)
  const producto = await alta.json()
  assert.equal(producto.umbralStock, 5)
  assert.equal(producto.precio, 41.15)
  resultado.alta = producto
  await modal.waitFor({ state: 'hidden' })
  await pagina.getByLabel('Buscar por nombre o descripción', { exact: true }).fill(nombre)
  const fila = pagina.locator('tbody tr').filter({ hasText: nombre })
  await fila.waitFor()
  await pagina.screenshot({ path: `${SALIDA}/01-alta.png`, fullPage: true })
  await fila.getByRole('button', { name: 'Editar', exact: true }).click()
  const edicion = pagina.getByRole('dialog', { name: 'Editar producto', exact: true })
  await edicion.waitFor()
  assert.equal(await edicion.getByLabel('Umbral de existencia baja', { exact: true }).inputValue(), '5')
  await edicion.getByLabel('Descripción (opcional)', { exact: true }).fill('Producto editado desde el cliente')
  await edicion.getByLabel('Precio', { exact: true }).fill('75.50')
  await edicion.getByLabel('Existencia', { exact: true }).fill('8')
  await edicion.getByLabel('Umbral de existencia baja', { exact: true }).fill('7')
  const respuestaEdicion = pagina.waitForResponse((r) => r.url() === `${API}/inventario/productos/${producto.id}` && r.request().method() === 'PUT')
  await edicion.getByRole('button', { name: 'Guardar', exact: true }).click()
  const modificada = await respuestaEdicion
  assert.equal(modificada.status(), 200)
  resultado.edicion = await modificada.json()
  assert.equal(resultado.edicion.precio, 75.5)
  assert.equal(resultado.edicion.umbralStock, 7)
  assert.equal(resultado.edicion.existencia, 8)
  await edicion.waitFor({ state: 'hidden' })
  await pagina.setViewportSize({ width: 390, height: 844 })
  assert.ok(await pagina.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth))
  await pagina.screenshot({ path: `${SALIDA}/02-edicion-movil.png`, fullPage: true })
  resultado.casos.push('Alta UI 201 con umbral 5 por omision y edicion UI 200')

  // Se contrasta la forma real de campos sin eludir ni modificar la UI.
  const invalida = await api('/inventario/productos', { metodo: 'POST', cuerpo: {
    nombre: '', precio: -1, existencia: -1, umbralStock: -1,
  } })
  assert.equal(invalida.estado, 400)
  assert.ok(invalida.datos.campos.nombre && invalida.datos.campos.precio)
  resultado.validacionRemota = invalida

  const correo = `productos.${Date.now()}@monpoint.test`
  const claveVendedor = process.env.PRUEBA_VENDEDOR_PASSWORD || 'Vendedor123!'
  const vendedor = await api('/auth/register', { metodo: 'POST', cuerpo: {
    nombre: 'Vendedor prueba productos', correo, password: claveVendedor, rol: 'VENDEDOR',
  } })
  assert.equal(vendedor.estado, 201)
  await pagina.getByRole('button', { name: 'Salir', exact: true }).click()
  await pagina.waitForURL('**/login')
  const tokenVendedor = await entrar(correo, claveVendedor)
  await abrirProductos()
  assert.equal(await pagina.getByRole('button', { name: 'Nuevo producto', exact: true }).count(), 0)
  assert.equal(await pagina.getByRole('button', { name: 'Editar', exact: true }).count(), 0)
  assert.equal(await pagina.locator('dialog').count(), 0)
  const listado = await api('/inventario/productos', { credencial: tokenVendedor })
  assert.equal(listado.estado, 200)
  resultado.permisosVendedor = { GET: 200 }
  const cuerpo = { nombre, precio: 75.5, existencia: 8, umbralStock: 7 }
  for (const metodo of ['POST', 'PUT', 'DELETE']) {
    const ruta = metodo === 'POST' ? '/inventario/productos' : `/inventario/productos/${producto.id}`
    const r = await api(ruta, { metodo, ...(metodo === 'DELETE' ? {} : { cuerpo }), credencial: tokenVendedor })
    assert.equal(r.estado, 403)
    resultado.permisosVendedor[metodo] = r.estado
  }
  resultado.casos.push('VENDEDOR: lectura 200, sin acciones/dialogos y POST/PUT/DELETE 403')

  await pagina.getByRole('button', { name: 'Salir', exact: true }).click()
  await pagina.waitForURL('**/login')
  token = await entrar('admin@monpoint.com', claveAdmin)
  await abrirProductos()
  await pagina.getByLabel('Buscar por nombre o descripción', { exact: true }).fill(nombre)
  await fila.getByRole('button', { name: 'Dar de baja', exact: true }).click()
  const baja = pagina.getByRole('dialog', { name: 'Dar de baja', exact: true })
  await baja.getByRole('button', { name: 'Cancelar', exact: true }).click()
  assert.equal((await api(`/inventario/productos/${producto.id}`)).datos.activo, true)
  await fila.getByRole('button', { name: 'Dar de baja', exact: true }).click()
  const respuestaBaja = pagina.waitForResponse((r) => r.url() === `${API}/inventario/productos/${producto.id}` && r.request().method() === 'DELETE')
  await baja.getByRole('button', { name: 'Dar de baja', exact: true }).click()
  assert.equal((await respuestaBaja).status(), 204)
  await fila.waitFor({ state: 'detached' })
  resultado.baja = await api(`/inventario/productos/${producto.id}`)
  assert.equal(resultado.baja.estado, 200)
  assert.equal(resultado.baja.datos.activo, false)
  assert.ok(!(await api('/inventario/productos')).datos.some((p) => p.id === producto.id))
  resultado.casos.push('Baja UI 204: desaparece del listado, consulta directa conserva activo=false')
  await pagina.screenshot({ path: `${SALIDA}/03-baja.png`, fullPage: true })
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
console.log('Productos reales:', resultado.resultado, resultado.casos)

/** Concha con Gateway real: lecturas de negocio y una cuenta VENDEDOR temporal.
 * No crea ventas, productos ni alertas; desactiva solo su cuenta al terminar.
 * Usar exclusivamente en un conjunto local de prueba. */
import assert from 'node:assert/strict'
import { mkdir, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { salirDesdeMenu } from './concha-fixture.mjs'
const modulo = await import(process.env.PLAYWRIGHT_MODULE ?? 'playwright')
const { chromium } = modulo.default ?? modulo
const BASE = process.env.BASE_URL ?? 'http://localhost:5173'
const API = process.env.GATEWAY_URL ?? 'http://localhost:8080'
const SALIDA = resolve(process.env.SALIDA ?? '/tmp/concha-real')
await mkdir(SALIDA, { recursive: true })
const resultado = { inicio: new Date().toISOString(), cliente: BASE, gateway: API, casos: [], errores: [], lecturas: [] }
const navegador = await chromium.launch({
  ...(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {}),
  args: process.env.CHROMIUM_NO_SANDBOX === '1' ? ['--no-sandbox'] : [],
})
const pagina = await navegador.newPage({ viewport: { width: 1440, height: 900 }, colorScheme: 'light' })
let token, creada
const claveAdmin = process.env.ADMIN_DEFAULT_PASSWORD || 'Admin123!'
const claveVendedor = process.env.PRUEBA_VENDEDOR_PASSWORD || 'Vendedor123!'
pagina.on('pageerror', (e) => resultado.errores.push(e.message))
pagina.on('response', (r) => {
  if (r.url().startsWith(API) && r.request().method() === 'GET') resultado.lecturas.push({ ruta: new URL(r.url()).pathname + new URL(r.url()).search, estado: r.status() })
})
async function api (ruta, metodo = 'GET', cuerpo) {
  const r = await fetch(`${API}${ruta}`, { method: metodo, headers: { Authorization: `Bearer ${token}`, ...(cuerpo ? { 'Content-Type': 'application/json' } : {}) }, ...(cuerpo ? { body: JSON.stringify(cuerpo) } : {}), signal: AbortSignal.timeout(10000) })
  return { estado: r.status, datos: r.status === 204 ? null : await r.json() }
}
async function entrar (correo, clave) {
  const respuesta = pagina.waitForResponse((r) => r.url() === `${API}/auth/login`)
  await pagina.getByLabel('Correo', { exact: true }).fill(correo)
  await pagina.getByLabel('Contraseña', { exact: true }).fill(clave)
  await pagina.getByRole('button', { name: 'Entrar', exact: true }).click()
  const r = await respuesta
  assert.equal(r.status(), 200)
  await pagina.waitForURL('**/caja')
  return (await r.json()).token
}
try {
  await pagina.goto(`${BASE}/login`)
  token = await entrar('admin@monpoint.com', claveAdmin)
  const empresa = await api('/reportes/configuracion')
  assert.ok([200, 404].includes(empresa.estado))
  const nombre = empresa.estado === 404 ? 'Auto Tachi' : empresa.datos.nombreEmpresa
  await pagina.locator('header.barra .negocio').filter({ hasText: nombre }).waitFor()
  resultado.empresaVisible = nombre
  const alertas = await api('/notificaciones?leida=false')
  assert.equal(alertas.estado, 200)
  resultado.sinLeer = alertas.datos.length
  await pagina.getByRole('button', { name: `Notificaciones: ${alertas.datos.length} sin leer`, exact: true }).click()
  await pagina.getByRole('region', { name: 'Avisos de existencia baja', exact: true }).waitFor()
  await pagina.screenshot({ path: `${SALIDA}/01-campana-admin.png`, fullPage: true })
  await pagina.keyboard.press('Escape')
  for (const nombreRuta of ['Productos', 'Clientes', 'Reportes', 'Notificaciones', 'Usuarios']) {
    await pagina.getByRole('link', { name: nombreRuta, exact: true }).click()
    await pagina.getByRole('heading', { name: nombreRuta, exact: true }).waitFor()
    await pagina.waitForFunction(() => document.querySelectorAll('.estado[aria-busy="true"]').length === 0)
    assert.equal(await pagina.getByRole('alert').count(), 0)
    assert.equal(await pagina.getByRole('heading', { level: 1 }).count(), 1)
    await pagina.screenshot({ path: `${SALIDA}/claro-${nombreRuta.toLowerCase()}.png`, fullPage: true })
  }
  await pagina.getByRole('button', { name: 'Cambiar a tema oscuro', exact: true }).click()
  await pagina.getByRole('link', { name: 'Productos', exact: true }).click()
  await pagina.getByRole('button', { name: 'Nuevo producto', exact: true }).click()
  await pagina.getByRole('dialog', { name: 'Nuevo producto', exact: true }).waitFor()
  await pagina.screenshot({ path: `${SALIDA}/02-dialogo-oscuro.png`, fullPage: true })
  await pagina.getByRole('button', { name: 'Cancelar', exact: true }).click()
  const alta = await api('/auth/register', 'POST', { nombre: 'Vendedor concha visual', correo: `concha.${Date.now()}@monpoint.test`, password: claveVendedor, rol: 'VENDEDOR' })
  assert.equal(alta.estado, 201)
  creada = alta.datos
  resultado.cuentaTemporal = { id: creada.id, correo: creada.correo }
  await salirDesdeMenu(pagina)
  await pagina.waitForURL((url) => url.pathname === '/login')
  await entrar(creada.correo, claveVendedor)
  assert.equal(await pagina.locator('nav a').count(), 3)
  assert.equal(await pagina.getByRole('button', { name: /^Notificaciones:/ }).count(), 0)
  await pagina.screenshot({ path: `${SALIDA}/03-vendedor-oscuro.png`, fullPage: true })
  for (const nombreRuta of ['Productos', 'Clientes']) {
    await pagina.getByRole('link', { name: nombreRuta, exact: true }).click()
    await pagina.getByRole('heading', { name: nombreRuta, exact: true }).waitFor()
  }
  await pagina.reload()
  await pagina.waitForURL((url) => url.pathname === '/login')
  assert.equal(await pagina.locator('html').getAttribute('data-tema'), 'oscuro')
  assert.deepEqual(await pagina.evaluate(() => Object.keys(localStorage)), ['monpoint.tema'])
  assert.deepEqual(resultado.errores, [])
  assert.ok(resultado.lecturas.every((r) => r.estado === 200 || (r.ruta === '/reportes/configuracion' && r.estado === 404)))
  resultado.casos = ['seis rutas ADMIN con lecturas reales', 'nombre de empresa coincide con API', 'badge coincide con sin leer de API', 'dialogo oscuro abre y cancela', 'VENDEDOR: tres rutas sin campana', 'F5 cierra sesion y conserva tema']
  resultado.resultado = 'PASS'
} catch (e) {
  resultado.resultado = 'FAIL'; resultado.fallo = e.message; process.exitCode = 1
  await pagina.screenshot({ path: `${SALIDA}/fallo.png`, fullPage: true }).catch(() => {})
} finally {
  if (creada) {
    const baja = await api(`/auth/usuarios/${creada.id}/desactivar`, 'PATCH')
    resultado.desactivacionTemporal = baja.estado
    if (baja.estado !== 204) { resultado.resultado = 'FAIL'; process.exitCode = 1 }
  }
  resultado.fin = new Date().toISOString()
  await writeFile(`${SALIDA}/resultado.json`, JSON.stringify(resultado, null, 2))
  await navegador.close()
}
console.log('Concha real:', resultado.resultado, resultado.fallo ?? '', resultado.casos)

// Concha con API simulada: preferencias, roles, invalidacion, fuentes locales y geometria.
import assert from 'node:assert/strict'
import { mkdir, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { salirDesdeMenu, menuUsuario } from './concha-fixture.mjs'
const modulo = await import(process.env.PLAYWRIGHT_MODULE ?? 'playwright')
const { chromium } = modulo.default ?? modulo
const BASE = process.env.BASE_URL ?? 'http://localhost:4173'
const API = process.env.GATEWAY_URL ?? 'http://localhost:8080'
const SALIDA = resolve(process.env.SALIDA ?? '/tmp/concha-visual')
await mkdir(SALIDA, { recursive: true })
const navegador = await chromium.launch({
  ...(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {}),
  args: process.env.CHROMIUM_NO_SANDBOX === '1' ? ['--no-sandbox'] : [],
})
const contexto = await navegador.newContext({ viewport: { width: 1440, height: 900 }, colorScheme: 'light' })
const pagina = await contexto.newPage()
const resultado = { inicio: new Date().toISOString(), casos: [], errores: [], externos: [], fuentes: [] }
let rol = 'ADMIN', avisosVendedor = 0, falloAvisos = false
let empresa = { nombreEmpresa: 'Auto Tachi', rfc: 'XAXX010101000', direccion: 'Av. de prueba 1', telefono: '3312345678' }
const avisos = [{ id: 'n1', productoNombre: 'Tinta', existenciaResultante: 5, mensaje: 'Existencia baja de Tinta: 5', leida: false },
  { id: 'n2', productoNombre: 'Papel', existenciaResultante: 3, mensaje: 'Existencia baja de Papel: 3', leida: false }]
pagina.on('pageerror', (e) => resultado.errores.push(e.message))
pagina.on('response', (r) => { if (r.url().endsWith('.woff2')) resultado.fuentes.push({ ruta: new URL(r.url()).pathname, estado: r.status() }) })
await contexto.route(/^https?:\/\//, (r) => {
  if ([new URL(BASE).origin, new URL(API).origin].includes(new URL(r.request().url()).origin)) return r.continue()
  resultado.externos.push(r.request().url())
  return r.abort()
})
await pagina.route(`${API}/**`, async (ruta) => {
  const req = ruta.request(), url = new URL(req.url()), path = url.pathname
  let datos
  if (path === '/auth/login') datos = { token: 'credencial-simulada', tipo: 'Bearer', expiraEn: 3600000,
    nombre: rol === 'ADMIN' ? 'Víctor Ortiz' : 'Alejandro Bermúdez', correo: 'persona@example.test', rol }
  else if (path === '/reportes/configuracion') {
    if (req.method() === 'PUT') empresa = req.postDataJSON()
    datos = empresa
  } else if (path === '/inventario/productos') datos = [
    { id: 'p1', nombre: 'Tinta', descripcion: 'Azul', precio: 41.15, existencia: 5, umbralStock: 5, activo: true },
    { id: 'p2', nombre: 'Papel', precio: 75.5, existencia: 8, umbralStock: 5, activo: true },
  ]
  else if (path === '/clientes') datos = [{ id: 'c1', nombre: 'Ana Ruiz', correo: 'ana@example.test' }]
  else if (path === '/auth/me') datos = { id: 'u1', nombre: 'Víctor Ortiz', correo: 'persona@example.test', rol: 'ADMIN', activo: true }
  else if (path === '/auth/usuarios') datos = [{ id: 'u1', nombre: 'Víctor Ortiz', correo: 'persona@example.test', rol: 'ADMIN', activo: true }]
  else if (path === '/reportes/ventas/agregado') datos = { periodo: url.searchParams.get('periodo'), desde: url.searchParams.get('desde'), hasta: url.searchParams.get('hasta'), zonaHoraria: 'America/Mexico_City', total: 672.25,
    puntos: [{ inicio: '2026-11-10', total: 476.75 }, { inicio: '2026-11-11', total: 0 }, { inicio: '2026-11-12', total: 195.5 }] }
  else if (path.startsWith('/notificaciones')) {
    if (rol !== 'ADMIN') { avisosVendedor++; return ruta.fulfill({ status: 403, contentType: 'application/problem+json', body: '{"detail":"Solo ADMIN"}' }) }
    if (falloAvisos) return ruta.fulfill({ status: 503, contentType: 'application/problem+json', body: '{"detail":"Consulta temporalmente no disponible"}' })
    if (req.method() === 'PUT') {
      avisos.find((n) => n.id === path.split('/')[2]).leida = true
      return ruta.fulfill({ status: 204, body: '' })
    }
    const filtro = url.searchParams.get('leida')
    datos = filtro === null ? avisos : avisos.filter((n) => String(n.leida) === filtro)
  } else throw new Error(`Ruta no prevista: ${path}`)
  await ruta.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(datos) })
})
async function entrar () {
  await pagina.getByLabel('Correo', { exact: true }).fill('persona@example.test')
  await pagina.getByLabel('Contraseña', { exact: true }).fill('clave-simulada')
  await pagina.getByRole('button', { name: 'Entrar', exact: true }).click()
  await pagina.waitForURL('**/caja')
}
async function captura (nombre) { await pagina.screenshot({ path: `${SALIDA}/${nombre}.png`, fullPage: true }) }
async function geometria () {
  return pagina.evaluate(() => {
    const rect = (s) => { const r = document.querySelector(s).getBoundingClientRect(); return { x: r.x, y: r.y, ancho: r.width, alto: r.height } }
    const css = getComputedStyle(document.documentElement)
    return { lateral: rect('.lateral'), cabecera: rect('header.barra'), viewport: innerWidth, documento: document.documentElement.scrollWidth,
      tema: document.documentElement.dataset.tema, tokens: Object.fromEntries(['fondo', 'borde', 'texto', 'texto-suave', 'verde-oscuro', 'proceso', 'proceso-fondo', 'radio', 'radio-tarjeta'].map((n) => [n, css.getPropertyValue('--mp-' + n).trim()])) }
  })
}
try {
  await pagina.goto(`${BASE}/login`)
  await pagina.getByRole('heading', { name: 'Iniciar sesión', exact: true }).waitFor()
  await pagina.evaluate(() => document.fonts.ready)
  assert.ok(await pagina.evaluate(() => document.fonts.check('600 16px Inter')))
  assert.equal(await pagina.locator('.lateral').count(), 0)
  await captura('01-login-claro')
  await entrar()
  await pagina.getByRole('button', { name: 'Notificaciones: 2 sin leer', exact: true }).waitFor()
  resultado.geometriaClara = await geometria()
  assert.equal(resultado.geometriaClara.lateral.ancho, 232)
  assert.equal(resultado.geometriaClara.cabecera.x, 232)
  assert.equal(resultado.geometriaClara.lateral.alto, 900)
  assert.equal(await pagina.getByRole('heading', { level: 1 }).count(), 1)
  assert.equal(await pagina.locator('nav a').count(), 6)
  assert.equal(await pagina.getByText('Proveedores', { exact: true }).count(), 0)
  assert.equal(await pagina.getByText('Historial de ventas', { exact: true }).count(), 0)
  await captura('02-caja-claro')
  await menuUsuario(pagina).click()
  await pagina.getByRole('region', { name: 'Cuenta de usuario', exact: true }).waitFor()
  await pagina.keyboard.press('Escape')
  assert.equal(await menuUsuario(pagina).getAttribute('aria-expanded'), 'false')
  assert.equal(await menuUsuario(pagina).evaluate((e) => e === document.activeElement), true)
  await pagina.getByRole('button', { name: 'Colapsar barra', exact: true }).click()
  assert.equal((await geometria()).lateral.ancho, 64)
  await pagina.getByRole('link', { name: 'Productos', exact: true }).click()
  await pagina.getByRole('heading', { name: 'Productos', exact: true }).waitFor()
  await captura('03-colapsada')
  await pagina.getByRole('button', { name: 'Expandir barra', exact: true }).click()
  for (const seccion of ['Productos', 'Clientes', 'Reportes', 'Notificaciones', 'Usuarios']) {
    await pagina.getByRole('link', { name: seccion, exact: true }).click()
    await pagina.getByRole('heading', { name: seccion, exact: true }).waitFor()
    await pagina.waitForFunction(() => document.querySelectorAll('.estado[aria-busy="true"]').length === 0)
    assert.equal(await pagina.getByRole('heading', { level: 1 }).count(), 1)
    await captura(`claro-${seccion.toLowerCase()}`)
  }
  await pagina.getByRole('button', { name: 'Cambiar a tema oscuro', exact: true }).click()
  resultado.geometriaOscura = await geometria()
  for (const seccion of ['Caja', 'Productos', 'Clientes', 'Reportes', 'Notificaciones', 'Usuarios']) {
    await pagina.getByRole('link', { name: seccion, exact: true }).click()
    await pagina.getByRole('heading', { name: seccion, exact: true }).waitFor()
    await pagina.waitForFunction(() => document.querySelectorAll('.estado[aria-busy="true"]').length === 0)
    await captura(`oscuro-${seccion.toLowerCase()}`)
  }
  await pagina.getByRole('link', { name: 'Notificaciones', exact: true }).click()
  await pagina.getByRole('button', { name: 'Marcar leída', exact: true }).first().click()
  await pagina.getByRole('button', { name: 'Notificaciones: 1 sin leer', exact: true }).waitFor()
  await pagina.getByRole('button', { name: 'Notificaciones: 1 sin leer', exact: true }).click()
  const panel = pagina.getByRole('region', { name: 'Avisos de existencia baja', exact: true })
  await panel.getByText('Papel', { exact: true }).waitFor()
  await captura('04-campana-oscuro')
  await pagina.keyboard.press('Escape')
  await pagina.getByRole('link', { name: 'Reportes', exact: true }).click()
  await pagina.getByRole('button', { name: 'Editar', exact: true }).click()
  const dialogo = pagina.getByRole('dialog', { name: 'Datos de la empresa', exact: true })
  await dialogo.getByLabel('Nombre de la empresa', { exact: true }).fill('Auto Tachi Refacciones')
  await dialogo.getByRole('button', { name: 'Guardar', exact: true }).click()
  await pagina.locator('header.barra .negocio').filter({ hasText: 'Auto Tachi Refacciones' }).waitFor()
  await pagina.setViewportSize({ width: 390, height: 844 })
  for (const nombre of ['Caja', 'Productos', 'Reportes']) {
    await pagina.getByRole('link', { name: nombre, exact: true }).click()
    await pagina.getByRole('heading', { name: nombre, exact: true }).waitFor()
    assert.ok(await pagina.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
    await captura(`movil-${nombre.toLowerCase()}`)
  }
  await menuUsuario(pagina).click()
  const cajaMenu = await pagina.getByRole('region', { name: 'Cuenta de usuario', exact: true }).boundingBox()
  assert.ok(cajaMenu.x >= 0 && cajaMenu.x + cajaMenu.width <= 390)
  await salirDesdeMenu(pagina)
  await pagina.waitForURL('**/login')
  await pagina.reload()
  assert.equal(await pagina.locator('html').getAttribute('data-tema'), 'oscuro')
  assert.deepEqual(await pagina.evaluate(() => Object.keys(localStorage)), ['monpoint.tema'])
  assert.equal(await pagina.locator('nav').count(), 0)
  await captura('05-login-oscuro')
  rol = 'VENDEDOR'
  await entrar()
  assert.equal(await pagina.locator('nav a').count(), 3)
  assert.equal(await pagina.getByRole('button', { name: /^Notificaciones:/ }).count(), 0)
  assert.equal(avisosVendedor, 0)
  assert.equal(await pagina.getByRole('link', { name: 'Usuarios', exact: true }).count(), 0)
  await captura('06-vendedor')
  resultado.casos = ['geometria 232/64 y cabecera interior', 'seis rutas, ambos temas, un h1', 'menu por teclado y cierre de sesion', 'badge actualizado tras marcar', 'nombre de empresa actualizado tras guardar', 'movil 390 px sin desbordamiento', 'tema persistido, token en memoria', 'VENDEDOR sin campana ni solicitudes de avisos']
  await salirDesdeMenu(pagina)
  await pagina.waitForURL('**/login')
  rol = 'ADMIN'; falloAvisos = true
  await entrar()
  await pagina.getByRole('button', { name: 'Notificaciones: consultar avisos', exact: true }).click()
  await panel.getByRole('alert').waitFor()
  assert.equal(await pagina.locator('.campana .badge').count(), 0)
  assert.equal(await panel.getByText('No hay alertas sin leer.', { exact: true }).count(), 0)
  falloAvisos = false
  await panel.getByRole('button', { name: 'Volver a intentar', exact: true }).click()
  await pagina.getByRole('button', { name: 'Notificaciones: 1 sin leer', exact: true }).waitFor()
  resultado.casos.push('fallo de avisos sin falso cero; reintento manual recupera contador')
  assert.deepEqual(resultado.errores, [])
  assert.deepEqual(resultado.externos, [])
  assert.ok(resultado.fuentes.length > 0 && resultado.fuentes.every((f) => f.estado === 200))
  // Sin eleccion guardada se sigue al sistema; si el navegador bloquea el
  // almacenamiento, el interruptor sigue funcionando durante esta pestaña.
  const aislado = await navegador.newContext({ colorScheme: 'dark' })
  await aislado.addInitScript(() => {
    Object.defineProperty(window, 'localStorage', { get () { throw new DOMException('Bloqueado', 'SecurityError') } })
  })
  const sinAlmacen = await aislado.newPage()
  await sinAlmacen.goto(`${BASE}/login`)
  await sinAlmacen.getByRole('button', { name: 'Cambiar a tema claro', exact: true }).waitFor()
  await sinAlmacen.emulateMedia({ colorScheme: 'light' })
  await sinAlmacen.getByRole('button', { name: 'Cambiar a tema oscuro', exact: true }).click()
  await sinAlmacen.emulateMedia({ colorScheme: 'dark' })
  await sinAlmacen.emulateMedia({ colorScheme: 'light' })
  assert.equal(await sinAlmacen.locator('html').getAttribute('data-tema'), 'oscuro')
  await aislado.close()
  resultado.casos.push('preferencia del sistema y almacenamiento bloqueado')
  resultado.resultado = 'PASS'
} catch (e) {
  resultado.resultado = 'FAIL'; resultado.fallo = e.message
  await captura('fallo').catch(() => {})
  process.exitCode = 1
} finally {
  resultado.fin = new Date().toISOString()
  await writeFile(`${SALIDA}/resultado.json`, JSON.stringify(resultado, null, 2))
  await navegador.close()
}
console.log('Concha:', resultado.resultado, resultado.casos)
if (resultado.fallo) console.error(resultado.fallo)

/** Regresiones de teclado: se comprueba el contenido de cada cobro, no solo su cuenta.
 * API simulada y aislada. No se consulta ni escribe en los servicios reales. */
import assert from 'node:assert/strict'
import { mkdir, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { menuUsuario } from './concha-fixture.mjs'
const modulo = await import(process.env.PLAYWRIGHT_MODULE ?? 'playwright')
const { chromium } = modulo.default ?? modulo
const BASE = process.env.BASE_URL ?? 'http://localhost:4173'
const API = process.env.GATEWAY_URL ?? 'http://localhost:8080'
const SALIDA = resolve(process.env.SALIDA ?? '/tmp/caja-atajos')
await mkdir(SALIDA, { recursive: true })
const navegador = await chromium.launch({
  ...(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {}),
  args: process.env.CHROMIUM_NO_SANDBOX === '1' ? ['--no-sandbox'] : [],
})
const pagina = await navegador.newPage({ viewport: { width: 1440, height: 900 } })
const productos = [
  { id: 'p1', nombre: 'Balatas cerámicas', descripcion: 'Juego delantero', precio: 489, existencia: 14, umbralStock: 5, activo: true },
  { id: 'p2', nombre: 'Aceite sintético', descripcion: 'Motor gasolina', precio: 215.5, existencia: 3, umbralStock: 5, activo: true },
  { id: 'p3', nombre: 'Filtro de aire', descripcion: 'Nissan', precio: 189, existencia: 0, umbralStock: 5, activo: true },
  { id: 'p4', nombre: 'Bujía iridio', descripcion: '', precio: 142.75, existencia: 5, umbralStock: 5, activo: true },
  { id: 'p5', nombre: 'Anticongelante', descripcion: '', precio: 325, existencia: 7, umbralStock: 5, activo: true },
]
const resultado = { inicio: new Date().toISOString(), casos: [], cobros: [], errores: [], inesperadas: [], teclas: [] }
let desenlace = 'PENDIENTE', errorCatalogo = false, liberarCatalogo = null, esperarCatalogo = false
pagina.on('pageerror', (e) => resultado.errores.push(e.message))
await pagina.route(`${API}/**`, async (ruta) => {
  const req = ruta.request(), path = new URL(req.url()).pathname
  let datos
  if (path === '/auth/login') datos = { token: 'simulado', correo: 'caja@example.test', nombre: 'Persona', rol: 'ADMIN', expiraEn: 3600000 }
  else if (path === '/reportes/configuracion') datos = { nombreEmpresa: 'Auto Tachi' }
  else if (path === '/notificaciones' || path === '/clientes') datos = []
  else if (path === '/inventario/productos') {
    if (esperarCatalogo) await new Promise((r) => { liberarCatalogo = r })
    if (errorCatalogo) return ruta.fulfill({ status: 503, contentType: 'application/problem+json', body: '{"detail":"Catalogo no disponible"}' })
    datos = productos
  } else if (path === '/ventas' && req.method() === 'POST') {
    resultado.cobros.push(req.postDataJSON())
    datos = { id: `v${resultado.cobros.length}`, folio: resultado.cobros.length, estado: 'PENDIENTE' }
    return ruta.fulfill({ status: 202, contentType: 'application/json', body: JSON.stringify(datos) })
  } else if (path.startsWith('/ventas/')) datos = { id: path.split('/')[2], folio: resultado.cobros.length, estado: desenlace,
    total: 215.5, motivo: 'Existencia insuficiente', lineas: [{ productoId: 'p2', nombreProducto: 'Aceite sintético', cantidad: 1, subtotal: 215.5 }] }
  else { resultado.inesperadas.push({ path, metodo: req.method() }); return ruta.abort() }
  return ruta.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(datos) })
})
const buscador = pagina.getByLabel('Buscar producto', { exact: true })
const actual = () => pagina.locator('.pasos [aria-current="step"]').innerText()
const tarjeta = (nombre) => pagina.getByRole('button', { name: new RegExp('^Agregar ' + nombre + ',') })
const pulso = (init) => buscador.evaluate((e, i) => e.dispatchEvent(new KeyboardEvent('keydown', { bubbles: true, cancelable: true, ...i })), init)
try {
  await pagina.goto(`${BASE}/login`)
  await pagina.getByLabel('Correo', { exact: true }).fill('caja@example.test')
  await pagina.getByLabel('Contraseña', { exact: true }).fill('simulada')
  await pagina.getByRole('button', { name: 'Entrar', exact: true }).click()
  await tarjeta('Aceite sintético').waitFor()
  await buscador.fill('gasolina')
  await buscador.press('Enter')
  assert.equal(await pagina.getByLabel('Cantidad', { exact: true }).inputValue(), '1')
  await buscador.fill('')
  await buscador.press('Control+Enter')
  await pagina.getByRole('heading', { name: 'Venta en proceso', exact: true }).waitFor()
  assert.deepEqual(resultado.cobros, [{ clienteId: null, lineas: [{ productoId: 'p2', cantidad: 1 }] }], 'Ctrl+Enter desde el buscador debe cobrar el carrito existente, sin agregar el primer resultado')
  assert.match(await actual(), /En proceso/)
  assert.equal(await tarjeta('Balatas cerámicas').isDisabled(), true)
  await buscador.press('Control+Enter')
  assert.equal(resultado.cobros.length, 1)
  desenlace = 'CONFIRMADA'
  await pagina.getByRole('heading', { name: 'Venta confirmada', exact: true }).waitFor()
  assert.match(await actual(), /Resultado/)
  await pagina.getByRole('button', { name: 'Nueva venta', exact: true }).click()
  await buscador.press('Control+Enter')
  assert.equal(await pagina.locator('.lineas .linea').count(), 0, 'Ctrl+Enter no agrega ni cobra si el carrito esta vacio')
  assert.equal(resultado.cobros.length, 1)
  resultado.casos.push('Ctrl+Enter no agrega productos; pendiente bloquea; resultado confirmado y nueva venta')

  await pulso({ key: 'Enter', isComposing: true })
  await pulso({ key: 'Enter', repeat: true })
  await buscador.press('Shift+Enter')
  await buscador.press('Alt+Enter')
  assert.equal(await pagina.locator('.lineas .linea').count(), 0, 'IME, repeticion y Enter con otros modificadores no agregan')
  await tarjeta('Aceite sintético').click()
  await menuUsuario(pagina).click()
  await pagina.getByRole('button', { name: 'Salir', exact: true }).focus()
  await pagina.keyboard.press('Control+Enter')
  assert.equal(resultado.cobros.length, 1, 'un menu abierto no dispara un cobro de fondo')
  await pagina.keyboard.press('Escape')
  await pagina.getByLabel('Cantidad', { exact: true }).fill('0')
  await pagina.keyboard.press('Control+Enter')
  assert.equal(resultado.cobros.length, 1)
  await pagina.getByLabel('Cantidad', { exact: true }).fill('1')
  await pulso({ key: 'Enter', ctrlKey: true, repeat: true })
  await pulso({ key: 'F12', repeat: true })
  assert.equal(resultado.cobros.length, 1, 'la repeticion del teclado no cobra')
  desenlace = 'CANCELADA'
  await pagina.evaluate(() => {
    window.__teclas = []
    window.addEventListener('keydown', (e) => { if (e.key === 'F12') window.__teclas.push({ cancelable: e.cancelable, prevenido: e.defaultPrevented, confiable: e.isTrusted }) })
  })
  await pagina.keyboard.press('F12')
  await pagina.getByRole('heading', { name: 'Venta cancelada', exact: true }).waitFor()
  assert.equal(resultado.cobros.length, 2)
  assert.deepEqual(resultado.cobros[1], { clienteId: null, lineas: [{ productoId: 'p2', cantidad: 1 }] })
  resultado.teclas = await pagina.evaluate(() => window.__teclas)
  assert.match(await actual(), /Resultado/)
  await pulso({ key: 'Enter', ctrlKey: true, repeat: true })
  assert.equal(resultado.cobros.length, 2, 'no se vuelve a cobrar una cancelada por repeticion de tecla')
  await pagina.getByRole('button', { name: 'Quitar Aceite sintético', exact: true }).click()
  resultado.casos.push('IME, modificadores, repeticion, foco en menu y cantidades invalidas; F12 recibido; cancelacion')

  esperarCatalogo = true
  await pagina.getByRole('button', { name: 'Actualizar catálogo', exact: true }).click()
  await pagina.getByText('Cargando productos…', { exact: true }).waitFor()
  await buscador.press('Enter')
  assert.equal(await pagina.locator('.lineas .linea').count(), 0, 'no se agrega un resultado oculto durante la recarga')
  errorCatalogo = true; esperarCatalogo = false; liberarCatalogo()
  await pagina.getByRole('alert').filter({ hasText: 'Catalogo no disponible' }).waitFor()
  await buscador.press('Enter')
  assert.equal(await pagina.locator('.lineas .linea').count(), 0, 'no se agrega un resultado oculto tras error')
  errorCatalogo = false
  await pagina.getByRole('button', { name: 'Actualizar catálogo', exact: true }).click()
  await tarjeta('Balatas cerámicas').waitFor()
  await buscador.press('Enter')
  assert.equal(await pagina.locator('.lineas .linea').count(), 1)
  resultado.casos.push('Enter solo agrega resultados visibles, despues de una carga correcta')
  await pagina.getByRole('link', { name: 'Productos', exact: true }).click()
  await pagina.getByRole('heading', { name: 'Productos', level: 1, exact: true }).waitFor()
  await pagina.keyboard.press('Control+Enter')
  assert.equal(resultado.cobros.length, 2, 'al navegar fuera de Caja no se cobra aunque tarde en desmontarse')
  await pagina.locator('.caja').waitFor({ state: 'detached' })
  await pagina.keyboard.press('Control+Enter')
  assert.equal(resultado.cobros.length, 2, 'al desmontar Caja se retira el atajo')
  assert.deepEqual(resultado.errores, [])
  assert.deepEqual(resultado.inesperadas, [])
  resultado.resultado = 'PASS'
} catch (e) {
  resultado.resultado = 'FAIL'; resultado.fallo = e.message; process.exitCode = 1
  await pagina.screenshot({ path: `${SALIDA}/fallo.png`, fullPage: true }).catch(() => {})
} finally {
  resultado.fin = new Date().toISOString()
  await writeFile(`${SALIDA}/resultado.json`, JSON.stringify(resultado, null, 2))
  await navegador.close()
}
console.log('Caja atajos:', resultado.resultado, resultado.casos)
if (resultado.fallo) console.error(resultado.fallo)

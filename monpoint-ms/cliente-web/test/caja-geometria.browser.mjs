/** Composicion de Caja con API simulada: geometria, ambos temas y anchos intermedios. */
import assert from 'node:assert/strict'
import { mkdir, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'
const modulo = await import(process.env.PLAYWRIGHT_MODULE ?? 'playwright')
const { chromium } = modulo.default ?? modulo
const BASE = process.env.BASE_URL ?? 'http://localhost:4173'
const API = process.env.GATEWAY_URL ?? 'http://localhost:8080'
const SALIDA = resolve(process.env.SALIDA ?? '/tmp/caja-geometria')
await mkdir(SALIDA, { recursive: true })
const navegador = await chromium.launch({
  ...(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {}),
  args: process.env.CHROMIUM_NO_SANDBOX === '1' ? ['--no-sandbox'] : [],
})
const contexto = await navegador.newContext({ viewport: { width: 1440, height: 900 }, colorScheme: 'light' })
const pagina = await contexto.newPage()
const productos = [
  ['Balatas delanteras cerámicas Tsuru', 'Juego delantero', 489, 14, 5],
  ['Aceite sintético 5W-30 1L', 'API SN Plus', 215.5, 3, 5],
  ['Filtro de aire motor', 'Nissan', 189, 0, 5],
  ['Bujía iridio', 'Motor gasolina', 142.75, 48, 5],
  ['Limpiaparabrisas 18 pulgadas', '', 98, 22, 5],
  ['Anticongelante verde 1 galón', '', 325, 7, 5],
  ['Banda de distribución', '', 860, 2, 5],
  ['Foco halógeno H4', '', 76.5, 35, 5],
].map(([nombre, descripcion, precio, existencia, umbralStock], i) => ({ id: 'p' + i, nombre, descripcion, precio, existencia, umbralStock, activo: true }))
const resultado = { inicio: new Date().toISOString(), anchos: [], fuentes: [], errores: [], externas: [] }
pagina.on('pageerror', (e) => resultado.errores.push(e.message))
pagina.on('response', (r) => { if (r.url().endsWith('.woff2')) resultado.fuentes.push({ ruta: new URL(r.url()).pathname, estado: r.status() }) })
await contexto.route(/^https?:\/\//, (ruta) => {
  const url = new URL(ruta.request().url())
  if (url.origin === new URL(BASE).origin || url.origin === new URL(API).origin) return ruta.continue()
  resultado.externas.push(ruta.request().url()); return ruta.abort()
})
await pagina.route(`${API}/**`, (ruta) => {
  const path = new URL(ruta.request().url()).pathname
  let body
  if (path === '/auth/login') body = { token: 'simulado', correo: 'persona@example.test', nombre: 'Víctor Ortiz', rol: 'ADMIN', expiraEn: 3600000 }
  else if (path === '/inventario/productos') body = productos
  else if (path === '/reportes/configuracion') body = { nombreEmpresa: 'Auto Tachi' }
  else if (path === '/clientes' || path === '/notificaciones') body = []
  else throw new Error(`Ruta no prevista: ${path}`)
  return ruta.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })
})
async function medir () {
  return pagina.evaluate(() => {
    const rect = (s) => { const e = document.querySelector(s), r = e.getBoundingClientRect(), css = getComputedStyle(e); return { x: r.x, y: r.y, ancho: r.width, alto: r.height, fuente: css.fontFamily, tamano: css.fontSize, peso: css.fontWeight, padding: css.padding, gap: css.gap } }
    const grid = document.querySelector('.rejilla'), g = grid.getBoundingClientRect()
    return { viewport: innerWidth, documento: document.documentElement.scrollWidth, columnas: getComputedStyle(grid).gridTemplateColumns,
      lateral: rect('.lateral'), main: rect('#contenido'), catalogo: rect('.catalogo'), cobro: rect('.panel-cobro'), tarjeta: rect('.tarjeta'), precio: rect('.precio'), tecla: rect('.tecla'),
      tarjetasDentro: [...grid.querySelectorAll('.tarjeta')].every((e) => { const r = e.getBoundingClientRect(); return r.x >= g.x - 1 && r.right <= g.right + 1 && e.scrollWidth <= e.clientWidth + 1 }),
    }
  })
}
try {
  await pagina.goto(`${BASE}/login`)
  await pagina.getByLabel('Correo', { exact: true }).fill('persona@example.test')
  await pagina.getByLabel('Contraseña', { exact: true }).fill('simulada')
  await pagina.getByRole('button', { name: 'Entrar', exact: true }).click()
  await pagina.locator('.rejilla .tarjeta').first().waitFor()
  await pagina.evaluate(() => document.fonts.ready)
  const inicial = await medir()
  assert.equal(inicial.main.ancho, 1208)
  assert.equal(inicial.catalogo.ancho, 816)
  assert.equal(inicial.cobro.ancho, 392)
  assert.equal(inicial.tarjeta.ancho, 185)
  assert.equal(inicial.columnas.split(' ').length, 4)
  resultado.escritorio = inicial
  await pagina.getByRole('button', { name: /^Agregar Balatas/ }).click()
  await pagina.getByRole('button', { name: /^Agregar Aceite/ }).click()
  await pagina.getByLabel('Cantidad', { exact: true }).nth(1).fill('2')
  assert.equal(await pagina.locator('.total').textContent(), '$920.00')
  await pagina.screenshot({ path: `${SALIDA}/01-claro-carrito.png`, fullPage: true })
  await pagina.getByRole('button', { name: 'Cambiar a tema oscuro', exact: true }).click()
  await pagina.screenshot({ path: `${SALIDA}/02-oscuro-carrito.png`, fullPage: true })
  for (const ancho of [1280, 1100, 960, 820, 720, 390, 320]) {
    await pagina.setViewportSize({ width: ancho, height: 900 })
    const medida = await medir()
    assert.ok(medida.documento <= ancho, `desbordamiento de documento a ${ancho}`)
    assert.ok(medida.tarjetasDentro, `tarjetas truncadas a ${ancho}`)
    resultado.anchos.push(medida)
    if ([820, 390, 320].includes(ancho)) await pagina.screenshot({ path: `${SALIDA}/ancho-${ancho}.png`, fullPage: true })
  }
  await pagina.setViewportSize({ width: 1440, height: 900 })
  await pagina.getByRole('button', { name: 'Colapsar barra', exact: true }).click()
  const colapsada = await medir()
  assert.equal(colapsada.lateral.ancho, 64)
  assert.equal(colapsada.cobro.ancho, 392)
  resultado.colapsada = colapsada
  await pagina.getByRole('link', { name: 'Productos', exact: true }).click()
  await pagina.locator('.caja').waitFor({ state: 'detached' })
  assert.equal(await pagina.locator('#contenido').evaluate((e) => getComputedStyle(e).padding), '24px 20px', 'Productos conserva el relleno de la concha')
  assert.deepEqual(resultado.errores, [])
  assert.deepEqual(resultado.externas, [])
  assert.ok(resultado.fuentes.some((f) => f.ruta.includes('ibm-plex-mono') && f.estado === 200))
  assert.ok(resultado.fuentes.some((f) => f.ruta.includes('inter-latin') && f.estado === 200))
  resultado.resultado = 'PASS'
} catch (e) {
  resultado.resultado = 'FAIL'; resultado.fallo = e.message; process.exitCode = 1
  await pagina.screenshot({ path: `${SALIDA}/fallo.png`, fullPage: true }).catch(() => {})
} finally {
  resultado.fin = new Date().toISOString()
  await writeFile(`${SALIDA}/resultado.json`, JSON.stringify(resultado, null, 2))
  await navegador.close()
}
console.log('Caja geometria:', resultado.resultado)
if (resultado.fallo) console.error(resultado.fallo)

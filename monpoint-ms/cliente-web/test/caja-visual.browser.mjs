/**
 * Recorrido de los anadidos visuales y de teclado de la Caja.
 *
 * `caja.browser.mjs` ya cubre la maquina de cobro contra sus seis estados.
 * Este recorrido cubre lo que la rejilla introduce y aquel no mira: las tres
 * variantes de la insignia de existencia, la busqueda sin acentos y sobre dos
 * campos, los atajos de teclado y el avance del indicador de pasos.
 *
 * Como los demas recorridos de navegador, las respuestas las fabrica esta
 * prueba: acredita el comportamiento del cliente, no el contrato del servidor.
 *
 * Uso: npm run build && npm run preview, y en otra consola
 *      node test/caja-visual.browser.mjs
 */
import assert from 'node:assert/strict'
import { simularConcha } from './concha-fixture.mjs'
const modulo = await import(process.env.PLAYWRIGHT_MODULE ?? 'playwright')
const { chromium } = modulo.default ?? modulo

const BASE = process.env.BASE_URL ?? 'http://localhost:4173'
const API = process.env.GATEWAY_URL ?? 'http://localhost:8080'
const SALIDA = process.env.SALIDA ?? '/tmp/caja-visual'

// Un producto por cada variante de insignia, mas uno con acento en el nombre y
// otro cuyo termino de busqueda solo aparece en la descripcion.
const PRODUCTOS = [
  { id: 'p1', nombre: 'Balatas cerámicas Tsuru', descripcion: 'Juego de 4 piezas',
    precio: 489, existencia: 14, umbralStock: 5, activo: true },
  { id: 'p2', nombre: 'Aceite sintético 5W-30', descripcion: 'API SN Plus',
    precio: 215.5, existencia: 3, umbralStock: 6, activo: true },
  { id: 'p3', nombre: 'Filtro de aire', descripcion: 'Compatible Nissan',
    precio: 189, existencia: 0, umbralStock: 4, activo: true },
  { id: 'p4', nombre: 'Producto retirado', descripcion: 'No debe aparecer',
    precio: 100, existencia: 9, umbralStock: 2, activo: false },
]

let estadoVenta = 'CONFIRMADA'
let cobrosEnviados = 0

const navegador = await chromium.launch({
  ...(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {}),
  args: process.env.CHROMIUM_NO_SANDBOX === '1' ? ['--no-sandbox'] : [],
})
const contexto = await navegador.newContext({ viewport: { width: 1440, height: 980 } })
const pagina = await contexto.newPage()
await simularConcha(pagina, API)
const erroresJs = []
pagina.on('pageerror', (e) => erroresJs.push(e.message))

await pagina.route(`${API}/auth/login`, (r) =>
  r.fulfill({ status: 200, contentType: 'application/json',
    body: JSON.stringify({ token: 't', tipo: 'Bearer', correo: 'a@b.test',
      nombre: 'Víctor Ortiz', rol: 'ADMIN', expiraEn: 3600000 }) }))

await pagina.route(`${API}/inventario/productos**`, (r) =>
  r.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(PRODUCTOS) }))
await pagina.route(`${API}/clientes**`, (r) =>
  r.fulfill({ status: 200, contentType: 'application/json', body: '[]' }))
await pagina.route(`${API}/notificaciones**`, (r) =>
  r.fulfill({ status: 200, contentType: 'application/json', body: '[]' }))

// El POST solo entrega folio: el desenlace llega por sondeo de GET /ventas/{id},
// que es como trabaja la maquina de cobro. Se atienden los dos metodos.
let folio = 6
await pagina.route(`${API}/ventas**`, (r) => {
  const venta = (estado) => ({
    id: `v${folio}`, folio, estado, total: 489, fecha: '2026-11-12T07:00:00Z',
    motivo: estado === 'CANCELADA' ? 'Existencia insuficiente' : null,
    productoIdRechazado: null,
    lineas: [{ nombreProducto: 'Balatas cerámicas Tsuru', cantidad: 1, subtotal: 489 }],
  })
  if (r.request().method() === 'POST') {
    cobrosEnviados++
    folio++
    return r.fulfill({ status: 202, contentType: 'application/json',
      body: JSON.stringify(venta('PENDIENTE')) })
  }
  return r.fulfill({ status: 200, contentType: 'application/json',
    body: JSON.stringify(venta(estadoVenta)) })
})

await pagina.goto(`${BASE}/login`)
await pagina.fill('#correo', 'a@b.test')
await pagina.fill('#contrasena', 'x')
await pagina.click('button[type="submit"]')
await pagina.waitForURL('**/caja')
await pagina.waitForSelector('.rejilla .tarjeta')

// --- Catalogo y sus insignias -------------------------------------------
// El producto inactivo no se ofrece: son tres tarjetas, no cuatro.
assert.equal(await pagina.locator('.rejilla .tarjeta').count(), 3,
  'el catalogo omite los productos dados de baja')

const variante = async (n) =>
  (await pagina.locator(`.rejilla li:nth-child(${n}) .existencia`).getAttribute('class'))
    .split(/\s+/)
    .find((c) => c.startsWith('mp-insignia--'))

assert.equal(await variante(1), 'mp-insignia--apagada', 'existencia holgada: insignia neutra')
assert.equal(await variante(2), 'mp-insignia--aviso', '3 <= umbral 6: insignia de aviso')
assert.equal(await variante(3), 'mp-insignia--error', 'existencia 0: insignia de error')

// Existencia cero NO deshabilita: quien decide si alcanza es el servidor al
// reservar, y el cliente no debe adelantar una negativa que no ha dado.
assert.equal(await pagina.locator('.rejilla li:nth-child(3) .tarjeta').isDisabled(), false,
  'la tarjeta sin existencia sigue siendo pulsable')
await pagina.screenshot({ path: `${SALIDA}-01-rejilla.png` })

// --- Busqueda -------------------------------------------------------------
// Sin acentos: quien escribe "ceramicas" espera encontrar "cerámicas".
await pagina.fill('#buscar', 'ceramicas')
await pagina.waitForFunction(() => document.querySelectorAll('.rejilla .tarjeta').length === 1)
assert.ok((await pagina.locator('.rejilla .nombre').innerText()).includes('cerámicas'))

// Tambien busca en la descripcion, no solo en el nombre: "nissan" solo aparece
// alli. El prototipo rotula este campo "Nombre o codigo", pero el catalogo no
// tiene codigo; la busqueda cubre los dos campos de texto que si existen.
await pagina.fill('#buscar', 'nissan')
await pagina.waitForFunction(() => document.querySelectorAll('.rejilla .tarjeta').length === 1)
assert.ok((await pagina.locator('.rejilla .nombre').innerText()).includes('Filtro'))

// --- Teclado --------------------------------------------------------------
// Enter agrega el primer resultado, que es lo que anuncia la pista.
await pagina.press('#buscar', 'Enter')
await pagina.waitForSelector('.lineas .linea')
assert.equal(await pagina.locator('.lineas .linea').count(), 1)
assert.ok((await pagina.locator('.lineas .linea strong').innerText()).includes('Filtro'))

// Enter no debe enviar el formulario ni recargar: la sesion vive en memoria y
// una recarga cerraria la sesion. Si seguimos dentro, no hubo navegacion.
assert.equal(new URL(pagina.url()).pathname, '/caja')

// F2 devuelve el foco a la busqueda desde cualquier parte de la pantalla.
await pagina.locator('.cobrar').focus()
await pagina.keyboard.press('F2')
assert.equal(await pagina.evaluate(() => document.activeElement?.id), 'buscar',
  'F2 enfoca la busqueda')

// Ctrl+Enter cobra. caja-atajos.browser.mjs comprueba tambien F12 recibido por
// Chromium y el cuerpo de las peticiones; no acredita los atajos de Windows.
await pagina.fill('#buscar', '')
assert.equal(cobrosEnviados, 0)
await pagina.keyboard.press('Control+Enter')
await pagina.waitForSelector('.resultado.confirmada')
assert.equal(cobrosEnviados, 1, 'Ctrl+Enter envio un unico cobro')

// --- Indicador de pasos ---------------------------------------------------
const alcanzados = () => pagina.locator('.pasos .alcanzado').count()
assert.equal(await alcanzados(), 3, 'una venta confirmada deja los tres pasos recorridos')
assert.equal(
  (await pagina.locator('.pasos li[aria-current="step"]').innerText()).replace(/\s+/g, ' ').trim(),
  '3 Resultado',
  'el paso actual es el resultado',
)
await pagina.screenshot({ path: `${SALIDA}-02-confirmada.png` })

// Con el carrito vacio solo esta recorrido el primero.
await pagina.getByRole('button', { name: 'Nueva venta', exact: true }).click()
await pagina.waitForSelector('.resultado.confirmada', { state: 'detached' })
assert.equal(await alcanzados(), 1)

// Ctrl+Enter con el carrito vacio no cobra: el atajo comparte la condicion del
// boton y no la esquiva.
await pagina.keyboard.press('Control+Enter')
await pagina.waitForTimeout(150)
assert.equal(cobrosEnviados, 1, 'el atajo no cobra un carrito vacio')
assert.equal(await pagina.locator('.cobrar .tecla').count(), 0,
  'el rotulo del atajo se retira cuando el atajo no esta disponible')

// Una venta cancelada tambien es un resultado: paso 3, no paso 1, aunque el
// carrito vuelva a ser editable.
estadoVenta = 'CANCELADA'
await pagina.locator('.rejilla li:nth-child(1) .tarjeta').click()
await pagina.waitForSelector('.cobrar .tecla')
await pagina.keyboard.press('Control+Enter')
await pagina.waitForSelector('.resultado.cancelada')
assert.equal(await alcanzados(), 3, 'una venta cancelada deja recorridos los tres pasos')
await pagina.screenshot({ path: `${SALIDA}-03-cancelada.png` })

assert.deepEqual(erroresJs, [], 'sin errores JavaScript en el recorrido')
await navegador.close()
console.log('Recorrido completado. Capturas en', `${SALIDA}-*.png`)

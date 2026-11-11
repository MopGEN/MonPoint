/**
 * Recorrido de la pantalla de notificaciones con el Gateway simulado.
 *
 * No sustituye a la comprobacion contra el conjunto real: aqui las respuestas
 * las fabrica esta prueba, asi que acredita el comportamiento del cliente, no
 * el contrato del servidor. Sirve para lo que si puede acreditar: que la
 * pantalla pinta, filtra, marca como leida y trata el 403 sin ofrecer un
 * reintento inutil.
 *
 * Uso: npm run build && npm run preview, y en otra consola
 *      node test/notificaciones.browser.mjs
 */
import assert from 'node:assert/strict'
// Igual que los demas recorridos: la ruta del modulo se puede sustituir para
// usar una instalacion global de Playwright.
// Playwright es CommonJS: importado por ruta llega bajo `default`, importado
// por nombre trae ademas los nombrados. Se aceptan las dos formas.
const modulo = await import(process.env.PLAYWRIGHT_MODULE ?? 'playwright')
const { chromium } = modulo.default ?? modulo

const BASE = process.env.BASE_URL ?? 'http://localhost:4173'
const SALIDA = process.env.SALIDA ?? '/tmp/notificaciones'
// Origen del Gateway. Las intercepciones se atan a el y no a `**`, porque
// `page.route` captura tambien el documento HTML y las piezas de la propia
// aplicacion: un patron amplio haria que pedir /notificaciones por URL
// devolviera JSON en lugar de cargar el cliente.
const API = process.env.GATEWAY_URL ?? 'http://localhost:8080'

const NOTIFICACIONES = [
  {
    id: 'n1',
    tipo: 'STOCK_BAJO',
    productoId: 'p1',
    productoNombre: 'Semilla 3.1 - Tinta',
    existenciaResultante: 5,
    mensaje: 'Existencia baja de Semilla 3.1 - Tinta: 5',
    activa: true,
    leida: false,
    fechaCreacion: '2026-11-11T07:16:19.454Z',
    fechaLectura: null,
  },
  {
    id: 'n2',
    tipo: 'STOCK_BAJO',
    productoId: 'p2',
    productoNombre: 'Semilla 3.1 - Papel',
    existenciaResultante: 3,
    mensaje: 'Existencia baja de Semilla 3.1 - Papel: 3',
    activa: false,
    leida: true,
    fechaCreacion: '2026-11-10T18:02:00.000Z',
    fechaLectura: '2026-11-10T18:40:00.000Z',
  },
]

const navegador = await chromium.launch()
const contexto = await navegador.newContext({ viewport: { width: 1280, height: 800 } })
const pagina = await contexto.newPage()

let rolActual = 'ADMIN'
let marcadas = 0

await pagina.route(`${API}/auth/login`, (ruta) =>
  ruta.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({
      token: 'simulado',
      tipo: 'Bearer',
      correo: 'admin@monpoint.com',
      nombre: 'Administrador',
      rol: rolActual,
      expiraEn: 3600000,
    }),
  }),
)

// `**` y no `*`: el patron tiene que cubrir tanto `/notificaciones?leida=…`
// como `/notificaciones/<id>/leida`, y un solo asterisco no cruza barras.
await pagina.route(`${API}/notificaciones**`, (ruta) => {
  const url = new URL(ruta.request().url())
  if (ruta.request().method() === 'PUT') {
    marcadas++
    return ruta.fulfill({ status: 204, body: '' })
  }
  if (rolActual !== 'ADMIN') {
    return ruta.fulfill({
      status: 403,
      contentType: 'application/problem+json',
      body: JSON.stringify({
        title: 'Acceso denegado',
        detail: 'Solo un ADMIN puede consultar las notificaciones',
      }),
    })
  }
  const leida = url.searchParams.get('leida')
  const lista =
    leida === null
      ? NOTIFICACIONES
      : NOTIFICACIONES.filter((n) => String(n.leida) === leida && !(marcadas > 0 && !n.leida))
  return ruta.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(lista),
  })
})

// Las rutas del catalogo las pide Caja al entrar; se responden vacias para que
// el refugio no muestre un error que nada tiene que ver con esta prueba.
await pagina.route(`${API}/inventario/productos**`, (r) =>
  r.fulfill({ status: 200, contentType: 'application/json', body: '[]' }),
)
await pagina.route(`${API}/clientes**`, (r) =>
  r.fulfill({ status: 200, contentType: 'application/json', body: '[]' }),
)

async function entrar () {
  await pagina.goto(`${BASE}/login`)
  await pagina.fill('#correo', 'admin@monpoint.com')
  await pagina.fill('#contrasena', 'Admin123!')
  await pagina.click('button[type="submit"]')
  await pagina.waitForURL('**/caja')
}

await entrar()

// La barra lateral solo existe con sesion abierta y solo ofrece lo que el rol
// alcanza.
const secciones = await pagina.locator('nav[aria-label="Secciones"] a').allInnerTexts()
assert.ok(secciones.some((s) => s.includes('Caja')), 'la barra ofrece Caja')
assert.ok(secciones.some((s) => s.includes('Notificaciones')), 'la barra ofrece Notificaciones a ADMIN')

await pagina.click('nav[aria-label="Secciones"] a:has-text("Notificaciones")')
await pagina.waitForURL('**/notificaciones')
await pagina.waitForSelector('table.mp-tabla')

// Filtro por omision: sin leer.
let filas = await pagina.locator('table.mp-tabla tbody tr').count()
assert.equal(filas, 1, 'el filtro inicial muestra solo las no leidas')
await pagina.screenshot({ path: `${SALIDA}-01-sin-leer.png` })

await pagina.click('button:has-text("Todas")')
await pagina.waitForFunction(() => document.querySelectorAll('table.mp-tabla tbody tr').length === 2)
await pagina.screenshot({ path: `${SALIDA}-02-todas.png` })

// Marcar como leida: desaparece del filtro de no leidas.
await pagina.click('button:has-text("Sin leer")')
await pagina.waitForSelector('button:has-text("Marcar leída")')
await pagina.click('button:has-text("Marcar leída")')
await pagina.waitForSelector('table.mp-tabla', { state: 'detached' })
assert.equal(marcadas, 1, 'se envio un unico PUT')
await pagina.screenshot({ path: `${SALIDA}-03-vacia-tras-marcar.png` })

// Un VENDEDOR no ve la seccion en la barra, porque la barra y la guarda
// consultan la misma funcion de rol.
rolActual = 'VENDEDOR'
await pagina.click('button:has-text("Salir")')
await pagina.waitForURL('**/login')
await entrar()
const seccionesVendedor = await pagina.locator('nav[aria-label="Secciones"] a').allInnerTexts()
assert.ok(
  !seccionesVendedor.some((s) => s.includes('Notificaciones')),
  'la barra no ofrece Notificaciones a VENDEDOR',
)
await pagina.screenshot({ path: `${SALIDA}-04-vendedor-sin-seccion.png` })

// Un enlace profundo sin sesion termina en el inicio de sesion, con el destino
// guardado para volver despues.
//
// Aqui `goto` es correcto justamente porque recarga: el token vive solo en
// memoria (H-080), asi que una navegacion completa equivale a entrar sin
// sesion. Por el mismo motivo `goto` NO sirve para comprobar el rechazo por
// rol: borraria la sesion antes de que la guarda pudiera evaluar el rol, y la
// prueba acreditaria otra cosa de la que dice acreditar.
await pagina.goto(`${BASE}/notificaciones`)
// Se sondea la ubicacion en vez de esperar una navegacion: la guarda redirige
// durante el montaje de la aplicacion, en el mismo documento, y ese cambio
// puede ocurrir antes de que `waitForURL` llegue a escuchar. Esperar un evento
// que ya paso agota el plazo aunque el comportamiento sea el correcto.
await pagina.waitForFunction(() => window.location.pathname === '/login')
assert.ok(
  new URL(pagina.url()).searchParams.get('regresarA')?.includes('/notificaciones'),
  'el destino denegado se conserva para volver tras iniciar sesion',
)

await navegador.close()
console.log('Recorrido completado. Capturas en', `${SALIDA}-*.png`)

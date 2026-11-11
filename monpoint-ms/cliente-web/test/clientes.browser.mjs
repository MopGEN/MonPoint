/**
 * Recorrido de la pantalla de clientes con el Gateway simulado.
 *
 * El doble se limita a lo que el servicio hace de verdad: no inventa
 * unicidad de nombre ni de correo, porque ms-clientes no la impone.
 *
 * Uso: npm run build && npm run preview, y en otra consola
 *      node test/clientes.browser.mjs
 */
import assert from 'node:assert/strict'
const modulo = await import(process.env.PLAYWRIGHT_MODULE ?? 'playwright')
const { chromium } = modulo.default ?? modulo

const BASE = process.env.BASE_URL ?? 'http://localhost:4173'
const API = process.env.GATEWAY_URL ?? 'http://localhost:8080'
const SALIDA = process.env.SALIDA ?? '/tmp/clientes'

let rol = 'ADMIN'
let lento = null // cuando es una promesa, el POST espera a que se resuelva
let clientes = [
  { id: 'c1', nombre: 'Ana Ruiz', correo: 'ana@example.test', telefono: '3312345678', direccion: 'Av. Juárez 10' },
  { id: 'c2', nombre: 'Luis Pérez', correo: null, telefono: null, direccion: null },
]
const recibidos = []

const navegador = await chromium.launch()
const pagina = await (await navegador.newContext({ viewport: { width: 1280, height: 860 } })).newPage()

await pagina.route(`${API}/auth/login`, (r) =>
  r.fulfill({
    status: 200, contentType: 'application/json',
    body: JSON.stringify({ token: 't', tipo: 'Bearer', correo: 'a@b.test', nombre: 'Persona', rol, expiraEn: 3600000 }),
  }),
)

await pagina.route(`${API}/clientes**`, async (ruta) => {
  const p = ruta.request()
  const metodo = p.method()

  if (metodo === 'GET') {
    return ruta.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(clientes) })
  }

  const cuerpo = metodo === 'DELETE' ? null : p.postDataJSON()
  recibidos.push({ metodo, cuerpo })

  if (metodo === 'POST') {
    if (lento) await lento
    const nuevo = { id: `c${clientes.length + 1}`, ...cuerpo }
    clientes.push(nuevo)
    return ruta.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify(nuevo) })
  }
  if (metodo === 'PUT') {
    const id = new URL(p.url()).pathname.split('/').pop()
    Object.assign(clientes.find((c) => c.id === id), cuerpo)
    return ruta.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(cuerpo) })
  }
  if (metodo === 'DELETE') {
    if (rol !== 'ADMIN') {
      return ruta.fulfill({
        status: 403, contentType: 'application/problem+json',
        body: JSON.stringify({ title: 'Acceso denegado', detail: 'Solo un ADMIN puede dar de baja clientes' }),
      })
    }
    const id = new URL(p.url()).pathname.split('/').pop()
    clientes = clientes.filter((c) => c.id !== id) // borrado FISICO, como el servicio
    return ruta.fulfill({ status: 204, body: '' })
  }
  return ruta.fulfill({ status: 405, body: '' })
})

for (const ruta of ['inventario/productos', 'notificaciones']) {
  await pagina.route(`${API}/${ruta}**`, (r) =>
    r.fulfill({ status: 200, contentType: 'application/json', body: '[]' }))
}

async function entrar () {
  await pagina.goto(`${BASE}/login`)
  await pagina.fill('#correo', 'a@b.test')
  await pagina.fill('#contrasena', 'x')
  await pagina.click('button[type="submit"]')
  await pagina.waitForURL('**/caja')
  await pagina.click('nav[aria-label="Secciones"] a:has-text("Clientes")')
  await pagina.waitForSelector('table.mp-tabla')
}

await entrar()

assert.equal(await pagina.locator('table.mp-tabla tbody tr').count(), 2)
// Los opcionales ausentes se muestran con raya, no en blanco.
assert.equal(await pagina.locator('table.mp-tabla tbody tr:nth-child(2) td:nth-child(2)').innerText(), '—')
await pagina.screenshot({ path: `${SALIDA}-01-listado.png` })

// La busqueda mira el telefono y no la direccion.
await pagina.fill('#buscar', '3312345678')
await pagina.waitForFunction(() => document.querySelectorAll('table.mp-tabla tbody tr').length === 1)
await pagina.fill('#buscar', 'juarez')
await pagina.waitForFunction(() => document.querySelectorAll('table.mp-tabla tbody tr').length === 0)
await pagina.fill('#buscar', '')
await pagina.waitForFunction(() => document.querySelectorAll('table.mp-tabla tbody tr').length === 2)

// Validacion del cliente: un correo imposible no gasta peticion.
const antes = recibidos.length
await pagina.click('button:has-text("Nuevo cliente")')
await pagina.waitForSelector('dialog[open]')
await pagina.fill('#nombre', 'Mario')
await pagina.fill('#correo', 'sinarroba')
await pagina.click('button[form="forma-cliente"]')
await pagina.waitForSelector('#correo-error')
assert.equal(recibidos.length, antes, 'no se envio nada')
await pagina.screenshot({ path: `${SALIDA}-02-correo-invalido.png` })

// Doble envio antes de que la interfaz se repinte: debe salir una sola
// peticion. Es el motivo por el que existe useAccion.
let liberar
lento = new Promise((res) => { liberar = res })
await pagina.fill('#correo', '')
await pagina.evaluate(() => {
  const boton = document.querySelector('button[form="forma-cliente"]')
  boton.click()
  boton.click()
})
await pagina.waitForFunction(() => document.querySelector('button[form="forma-cliente"]').disabled)
liberar()
await pagina.waitForSelector('dialog[open]', { state: 'detached' })
// El dialogo se cierra en cuanto el servidor confirma y la recarga llega
// despues, asi que hay que esperar por contenido y no por una cuenta: contar
// filas aqui las contaria a medio refrescar.
await pagina.waitForSelector('table.mp-tabla >> text=Mario')
const altas = recibidos.filter((r) => r.metodo === 'POST')
assert.equal(altas.length, 1, 'dos clics produjeron una sola alta')
assert.deepEqual(altas[0].cuerpo, { nombre: 'Mario', correo: null, telefono: null, direccion: null })
lento = null

// Edicion: los nulos del servidor llegan como campos vacios y vuelven nulos.
await pagina.click('table.mp-tabla tbody tr:nth-child(2) button:has-text("Editar")')
await pagina.waitForSelector('dialog[open]')
assert.equal(await pagina.inputValue('#correo'), '', 'un correo nulo no llega como "null"')
await pagina.fill('#telefono', '5544332211')
await pagina.click('button[form="forma-cliente"]')
await pagina.waitForSelector('dialog[open]', { state: 'detached' })
assert.equal(recibidos.at(-1).cuerpo.correo, null)
assert.equal(recibidos.at(-1).cuerpo.telefono, '5544332211')

// Borrado fisico: el dialogo lo dice y la fila desaparece.
await pagina.click('table.mp-tabla tbody tr:has-text("Ana Ruiz") button:has-text("Eliminar")')
await pagina.waitForSelector('dialog[open]')
assert.ok(
  (await pagina.locator('dialog[open]').innerText()).includes('No es una baja lógica'),
  'la advertencia nombra que el borrado es permanente',
)
await pagina.screenshot({ path: `${SALIDA}-03-eliminar.png` })
await pagina.click('dialog[open] button.peligro')
// Se espera a que desaparezca esa fila concreta: una condicion absoluta, no
// una diferencia respecto a una cuenta tomada en un instante cualquiera.
await pagina.waitForSelector('table.mp-tabla >> text=Ana Ruiz', { state: 'detached' })
assert.equal(recibidos.at(-1).metodo, 'DELETE')

// VENDEDOR: puede dar de alta y editar, pero no eliminar.
rol = 'VENDEDOR'
await pagina.click('button:has-text("Salir")')
await pagina.waitForURL('**/login')
await entrar()
assert.ok(await pagina.locator('button:has-text("Nuevo cliente")').count(), 'puede dar de alta')
assert.ok(await pagina.locator('button:has-text("Editar")').count(), 'puede editar')
assert.equal(await pagina.locator('button:has-text("Eliminar")').count(), 0, 'no puede eliminar')
await pagina.screenshot({ path: `${SALIDA}-04-vendedor.png` })

await navegador.close()
console.log('Recorrido completado. Capturas en', `${SALIDA}-*.png`)

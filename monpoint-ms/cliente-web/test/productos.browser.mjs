/**
 * Recorrido de la pantalla de productos con el Gateway simulado.
 *
 * Las respuestas las fabrica esta prueba, asi que acredita el comportamiento
 * del cliente y no el contrato del servidor. Comprueba lo que si puede: que el
 * rol cambia lo que se ofrece, que la insignia de umbral aparece donde debe,
 * que la busqueda filtra, que un error de validacion del servidor se pinta en
 * su campo y que la baja pide confirmacion antes de ocurrir.
 *
 * Uso: npm run build && npm run preview, y en otra consola
 *      node test/productos.browser.mjs
 */
import assert from 'node:assert/strict'
const modulo = await import(process.env.PLAYWRIGHT_MODULE ?? 'playwright')
const { chromium } = modulo.default ?? modulo

const BASE = process.env.BASE_URL ?? 'http://localhost:4173'
const API = process.env.GATEWAY_URL ?? 'http://localhost:8080'
const SALIDA = process.env.SALIDA ?? '/tmp/productos'

let rol = 'ADMIN'
let catalogo = [
  { id: 'p1', nombre: 'Tinta para sello', descripcion: 'Azul', precio: 41.15, existencia: 5, umbralStock: 5, activo: true },
  { id: 'p2', nombre: 'Papel bond', descripcion: 'Carta', precio: 75.5, existencia: 8, umbralStock: 5, activo: true },
  { id: 'p3', nombre: 'Sello automático', descripcion: null, precio: 120, existencia: 9, umbralStock: 5, activo: true },
]
const recibidos = []

const navegador = await chromium.launch()
const pagina = await (await navegador.newContext({ viewport: { width: 1280, height: 860 } })).newPage()

await pagina.route(`${API}/auth/login`, (r) =>
  r.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({
      token: 'simulado', tipo: 'Bearer', correo: 'a@b.test',
      nombre: 'Administrador', rol, expiraEn: 3600000,
    }),
  }),
)

await pagina.route(`${API}/inventario/productos**`, async (ruta) => {
  const peticion = ruta.request()
  const metodo = peticion.method()

  if (metodo === 'GET') {
    return ruta.fulfill({
      status: 200, contentType: 'application/json',
      body: JSON.stringify(catalogo.filter((p) => p.activo)),
    })
  }

  const cuerpo = metodo === 'DELETE' ? null : peticion.postDataJSON()
  recibidos.push({ metodo, url: peticion.url(), cuerpo })

  // Un alta con nombre repetido se rechaza como lo hace el servicio: 400 con
  // el mapa `campos`, que es lo que la pantalla debe saber pintar.
  if (metodo === 'POST' && catalogo.some((p) => p.activo && p.nombre === cuerpo.nombre)) {
    return ruta.fulfill({
      status: 400,
      contentType: 'application/problem+json',
      body: JSON.stringify({
        title: 'Datos inválidos',
        detail: 'Uno o más campos no cumplen las validaciones',
        campos: { nombre: 'Ya existe un producto activo con ese nombre' },
      }),
    })
  }

  if (metodo === 'POST') {
    catalogo.push({ id: `p${catalogo.length + 1}`, activo: true, descripcion: null, ...cuerpo })
    return ruta.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify(cuerpo) })
  }
  if (metodo === 'PUT') {
    const id = new URL(peticion.url()).pathname.split('/').pop()
    Object.assign(catalogo.find((p) => p.id === id), cuerpo)
    return ruta.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(cuerpo) })
  }
  if (metodo === 'DELETE') {
    const id = new URL(peticion.url()).pathname.split('/').pop()
    catalogo.find((p) => p.id === id).activo = false
    return ruta.fulfill({ status: 204, body: '' })
  }
  return ruta.fulfill({ status: 405, body: '' })
})

await pagina.route(`${API}/clientes**`, (r) =>
  r.fulfill({ status: 200, contentType: 'application/json', body: '[]' }),
)
await pagina.route(`${API}/notificaciones**`, (r) =>
  r.fulfill({ status: 200, contentType: 'application/json', body: '[]' }),
)

async function entrar () {
  await pagina.goto(`${BASE}/login`)
  await pagina.fill('#correo', 'a@b.test')
  await pagina.fill('#contrasena', 'x')
  await pagina.click('button[type="submit"]')
  await pagina.waitForURL('**/caja')
  await pagina.click('nav[aria-label="Secciones"] a:has-text("Productos")')
  await pagina.waitForSelector('table.mp-tabla')
}

await entrar()

// Tres productos, y solo el que esta en su umbral lleva insignia.
assert.equal(await pagina.locator('table.mp-tabla tbody tr').count(), 3)
assert.equal(await pagina.locator('.mp-insignia--aviso').count(), 1, 'solo Tinta esta en el umbral')
await pagina.screenshot({ path: `${SALIDA}-01-catalogo.png` })

// La busqueda ignora acentos.
await pagina.fill('#buscar', 'automatico')
await pagina.waitForFunction(() => document.querySelectorAll('table.mp-tabla tbody tr').length === 1)
await pagina.fill('#buscar', '')
await pagina.waitForFunction(() => document.querySelectorAll('table.mp-tabla tbody tr').length === 3)

// Validacion del cliente: no se envia nada al servidor.
const antes = recibidos.length
await pagina.click('button:has-text("Nuevo producto")')
await pagina.waitForSelector('dialog[open]')
await pagina.click('button[form="forma-producto"]')
await pagina.waitForSelector('#nombre-error')
assert.equal(recibidos.length, antes, 'un formulario invalido no gasta una peticion')
await pagina.screenshot({ path: `${SALIDA}-02-validacion-cliente.png` })

// Validacion del servidor: el mensaje aterriza en su campo.
await pagina.fill('#nombre', 'Papel bond')
await pagina.fill('#precio', '10.00')
await pagina.fill('#existencia', '3')
await pagina.click('button[form="forma-producto"]')
await pagina.waitForSelector('text=Ya existe un producto activo con ese nombre')
assert.ok(
  await pagina.locator('#nombre[aria-invalid="true"]').count(),
  'el campo queda marcado como invalido para un lector de pantalla',
)
await pagina.screenshot({ path: `${SALIDA}-03-validacion-servidor.png` })

// Alta correcta: el dialogo se cierra y la fila aparece.
await pagina.fill('#nombre', 'Cinta adhesiva')
await pagina.click('button[form="forma-producto"]')
await pagina.waitForFunction(() => document.querySelectorAll('table.mp-tabla tbody tr').length === 4)
assert.equal(await pagina.locator('dialog[open]').count(), 0, 'el dialogo se cierra al guardar')
const alta = recibidos.at(-1)
assert.equal(alta.metodo, 'POST')
assert.equal(alta.cuerpo.precio, 10, 'el precio viaja como numero')
assert.equal(alta.cuerpo.umbralStock, null, 'sin umbral viaja null para que el servidor ponga el suyo')

// Edicion: el umbral vacio es un error, porque el servidor conservaria el viejo.
await pagina.click('table.mp-tabla tbody tr:first-child button:has-text("Editar")')
await pagina.waitForSelector('dialog[open]')
assert.equal(await pagina.inputValue('#umbralStock'), '5', 'el formulario llega relleno')
await pagina.fill('#umbralStock', '')
await pagina.click('button[form="forma-producto"]')
await pagina.waitForSelector('#umbralStock-error')
await pagina.fill('#umbralStock', '7')
await pagina.click('button[form="forma-producto"]')
await pagina.waitForSelector('dialog[open]', { state: 'detached' })
assert.equal(recibidos.at(-1).metodo, 'PUT')
assert.equal(recibidos.at(-1).cuerpo.umbralStock, 7)

// Baja logica: pide confirmacion y la fila desaparece del listado.
await pagina.click('table.mp-tabla tbody tr:first-child button:has-text("Dar de baja")')
await pagina.waitForSelector('dialog[open]')
await pagina.screenshot({ path: `${SALIDA}-04-confirmar-baja.png` })
await pagina.click('dialog[open] button:has-text("Dar de baja")')
await pagina.waitForFunction(() => document.querySelectorAll('table.mp-tabla tbody tr').length === 3)
assert.equal(recibidos.at(-1).metodo, 'DELETE')

// VENDEDOR: misma pantalla, sin ninguna accion de escritura.
rol = 'VENDEDOR'
await pagina.click('button:has-text("Salir")')
await pagina.waitForURL('**/login')
await entrar()
assert.equal(await pagina.locator('button:has-text("Nuevo producto")').count(), 0)
assert.equal(await pagina.locator('button:has-text("Editar")').count(), 0)
assert.equal(await pagina.locator('button:has-text("Dar de baja")').count(), 0)
assert.ok(await pagina.locator('text=Solo consulta.').count(), 'la pantalla dice que es de consulta')
await pagina.screenshot({ path: `${SALIDA}-05-vendedor-solo-consulta.png` })

await navegador.close()
console.log('Recorrido completado. Capturas en', `${SALIDA}-*.png`)

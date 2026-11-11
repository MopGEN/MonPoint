/**
 * Recorrido de la pantalla de reportes con el Gateway simulado.
 *
 * Uso: npm run build && npm run preview, y en otra consola
 *      node test/reportes.browser.mjs
 */
import assert from 'node:assert/strict'
const modulo = await import(process.env.PLAYWRIGHT_MODULE ?? 'playwright')
const { chromium } = modulo.default ?? modulo

const BASE = process.env.BASE_URL ?? 'http://localhost:4173'
const API = process.env.GATEWAY_URL ?? 'http://localhost:8080'
const SALIDA = process.env.SALIDA ?? '/tmp/reportes'

let rol = 'ADMIN'
let configuracion = {
  nombreEmpresa: 'Papelería MonPoint', rfc: 'MON260101AB1',
  direccion: 'Av. Juárez 10', telefono: '3312345678',
}
let rechazarConfiguracion = false
const peticiones = []

const navegador = await chromium.launch({
  ...(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {}),
  args: process.env.CHROMIUM_NO_SANDBOX === '1' ? ['--no-sandbox'] : [],
})
const contexto = await navegador.newContext({
  viewport: { width: 1280, height: 980 }, acceptDownloads: true,
})
const pagina = await contexto.newPage()
const erroresJs = []
pagina.on('pageerror', (e) => erroresJs.push(e.message))

await pagina.route(`${API}/auth/login`, (r) =>
  r.fulfill({
    status: 200, contentType: 'application/json',
    body: JSON.stringify({ token: 't', tipo: 'Bearer', correo: 'a@b.test', nombre: 'Admin', rol, expiraEn: 3600000 }),
  }),
)

// Serie con un hueco en medio: el servidor rellena con cero los periodos sin
// ventas y la grafica debe conservar ese hueco.
await pagina.route(`${API}/reportes/ventas/agregado**`, (ruta) => {
  const u = new URL(ruta.request().url())
  peticiones.push({ tipo: 'agregado', url: u.pathname + u.search })
  return ruta.fulfill({
    status: 200, contentType: 'application/json',
    body: JSON.stringify({
      periodo: u.searchParams.get('periodo'),
      desde: u.searchParams.get('desde'),
      hasta: u.searchParams.get('hasta'),
      zonaHoraria: 'America/Mexico_City',
      total: 672.25,
      puntos: [
        { inicio: '2026-11-09', total: 476.75 },
        { inicio: '2026-11-10', total: 0 },
        { inicio: '2026-11-11', total: 195.5 },
      ],
    }),
  })
})

// Cuerpo de prueba con cabecera PDF: prueba transporte, no validez del PDF.
const PDF = '%PDF-1.4\n1 0 obj<</Type/Catalog>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF'
for (const ruta of ['ventas/historial', 'productos/listado', 'clientes/listado']) {
  await pagina.route(`${API}/reportes/${ruta}**`, (r) => {
    peticiones.push({ tipo: 'pdf', ruta, auth: r.request().headers().authorization ?? null })
    return r.fulfill({
      status: 200, contentType: 'application/pdf',
      headers: { 'content-disposition': 'inline; filename="del-servidor.pdf"' },
      body: PDF,
    })
  })
}

await pagina.route(`${API}/reportes/configuracion`, async (ruta) => {
  if (ruta.request().method() === 'GET') {
    if (configuracion === null) return ruta.fulfill({
      status: 404, contentType: 'application/problem+json',
      body: JSON.stringify({ detail: 'La configuración de la empresa todavía no se ha registrado' }),
    })
    return ruta.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(configuracion) })
  }
  const cuerpo = ruta.request().postDataJSON()
  peticiones.push({ tipo: 'configuracion', cuerpo })
  if (rechazarConfiguracion) {
    return ruta.fulfill({
      status: 400, contentType: 'application/problem+json',
      body: JSON.stringify({
        title: 'Datos inválidos', detail: 'Uno o más campos no cumplen las validaciones',
        campos: { nombreEmpresa: 'El nombre de la empresa admite hasta 120 caracteres' },
      }),
    })
  }
  configuracion = { ...configuracion, ...cuerpo }
  return ruta.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(configuracion) })
})

for (const x of ['inventario/productos', 'clientes', 'notificaciones']) {
  await pagina.route(`${API}/${x}**`, (r) =>
    r.fulfill({ status: 200, contentType: 'application/json', body: '[]' }))
}

async function entrar (seccion = 'Reportes') {
  if (new URL(pagina.url()).pathname !== '/login') await pagina.goto(`${BASE}/login`)
  await pagina.fill('#correo', 'a@b.test')
  await pagina.fill('#contrasena', 'x')
  await pagina.click('button[type="submit"]')
  await pagina.waitForURL('**/caja')
  if (seccion) {
    await pagina.click(`nav[aria-label="Secciones"] a:has-text("${seccion}")`)
  }
}

await entrar()
await pagina.waitForSelector('svg[role="img"]')

// Tres puntos, y el del medio en cero conserva su hueco sin barra visible.
assert.equal(await pagina.locator('svg[role="img"] rect').count(), 3)
assert.equal(await pagina.locator('svg[role="img"] rect.vacia').count(), 1)
assert.ok((await pagina.locator('.total').innerText()).includes('672.25'))
assert.ok((await pagina.locator('.zona').innerText()).includes('America/Mexico_City'))
await pagina.screenshot({ path: `${SALIDA}-01-grafica.png` })
const descripcionInicial = await pagina.locator('svg[role="img"]').getAttribute('aria-label')

// La serie tambien existe como tabla para lectores de pantalla.
assert.equal(await pagina.locator('figcaption table tbody tr').count(), 3)

// Rango invertido: no se consulta al servidor.
const antes = peticiones.filter((p) => p.tipo === 'agregado').length
await pagina.fill('#desde', '2026-11-12')
await pagina.fill('#hasta', '2026-11-11')
assert.equal(await pagina.locator('svg[role="img"]').getAttribute('aria-label'), descripcionInicial,
  'editar el borrador no cambia el rango atribuido a los datos anteriores')
await pagina.click('button:has-text("Consultar")')
await pagina.waitForSelector('#hasta-error')
assert.equal(peticiones.filter((p) => p.tipo === 'agregado').length, antes, 'no se consulto')

// Rango demasiado largo por dia: el aviso nombra la cuenta y el tope.
await pagina.fill('#desde', '2020-01-01')
await pagina.fill('#hasta', '2026-11-11')
await pagina.click('button:has-text("Consultar")')
await pagina.waitForSelector('#hasta-error')
const aviso = await pagina.locator('#hasta-error').innerText()
assert.ok(/\d{4}/.test(aviso) && aviso.includes('370'), aviso)
await pagina.screenshot({ path: `${SALIDA}-02-rango-excesivo.png` })

// El mismo rango con granularidad mayor si pasa.
await pagina.selectOption('#periodo', 'ANIO')
await pagina.click('button:has-text("Consultar")')
await pagina.waitForFunction((n) => !document.querySelector('#hasta-error'), null)
assert.equal(peticiones.filter((p) => p.tipo === 'agregado').length, antes + 1)
assert.ok(peticiones.at(-1).url.includes('periodo=ANIO'))
assert.equal(await pagina.locator('.documento:has-text("Historial de ventas") button').isDisabled(), true)
assert.equal(peticiones.filter((p) => p.tipo === 'pdf').length, 0, 'no se pide un historial de mas de 366 dias')

// Documento: se pide con el token y se guarda con el nombre que pone el
// cliente, no con el que manda el servidor, que CORS no deja leer.
const descarga = pagina.waitForEvent('download')
await pagina.click('.documento:has-text("Listado de productos") button')
const archivo = await descarga
assert.equal(archivo.suggestedFilename(), 'listado-productos.pdf')
const pdf = peticiones.filter((p) => p.tipo === 'pdf').at(-1)
assert.equal(pdf.ruta, 'productos/listado')
assert.ok(pdf.auth?.startsWith('Bearer '), 'la descarga lleva el token')

// Configuracion: un 400 con campos aterriza en su campo, sin aviso generico.
rechazarConfiguracion = true
await pagina.click('button:has-text("Editar")')
await pagina.waitForSelector('dialog[open]')
assert.equal(await pagina.inputValue('#nombreEmpresa'), 'Papelería MonPoint')
await pagina.fill('#nombreEmpresa', 'Otro nombre')
await pagina.click('button[form="forma-config"]')
await pagina.waitForSelector('#nombreEmpresa-error')
assert.equal(await pagina.locator('dialog[open] .mp-aviso--error').count(), 0, 'sin aviso duplicado')
await pagina.screenshot({ path: `${SALIDA}-03-configuracion.png` })

// RFC vacio lo detiene el cliente, sin gastar peticion.
const envios = peticiones.filter((p) => p.tipo === 'configuracion').length
await pagina.fill('#rfc', '   ')
await pagina.click('button[form="forma-config"]')
await pagina.waitForSelector('#rfc-error')
assert.equal(peticiones.filter((p) => p.tipo === 'configuracion').length, envios)

rechazarConfiguracion = false
await pagina.fill('#rfc', 'MON260101AB1')
await pagina.click('button[form="forma-config"]')
await pagina.waitForSelector('dialog[open]', { state: 'detached' })
await pagina.waitForSelector('text=Otro nombre')

// Sin configuracion aun: un 404 debe permitir registrar el primer documento.
await pagina.click('nav[aria-label="Secciones"] a:has-text("Caja")')
await pagina.waitForURL('**/caja')
configuracion = null
await pagina.click('nav[aria-label="Secciones"] a:has-text("Reportes")')
await pagina.getByRole('button', { name: 'Registrar datos de empresa', exact: true }).waitFor()
await pagina.getByRole('button', { name: 'Registrar datos de empresa', exact: true }).click()
await pagina.getByRole('dialog', { name: 'Datos de la empresa', exact: true }).waitFor()
assert.equal(await pagina.inputValue('#nombreEmpresa'), '')
await pagina.getByRole('button', { name: 'Cancelar', exact: true }).click()
await pagina.click('nav[aria-label="Secciones"] a:has-text("Caja")')
await pagina.waitForURL('**/caja')

// VENDEDOR: la seccion no se ofrece y la guarda rechaza volver por historial.
rol = 'VENDEDOR'
await pagina.click('button:has-text("Salir")')
await pagina.waitForURL('**/login')
await entrar(null)
const secciones = await pagina.locator('nav[aria-label="Secciones"] a').allInnerTexts()
assert.ok(!secciones.some((s) => s.includes('Reportes')), 'la barra no ofrece Reportes a VENDEDOR')
await pagina.screenshot({ path: `${SALIDA}-04-vendedor.png` })

// Volver por historial conserva el token en memoria; goto no prueba la guarda.
const lecturas = peticiones.filter((p) => p.tipo === 'agregado').length
assert.equal(await pagina.evaluate(() => history.state.back), '/reportes')
await pagina.evaluate(() => window.addEventListener('popstate', (evento) => {
  // El estado del evento identifica la entrada recorrida aunque el router
  // ya haya cambiado location al redirigir a Caja.
  window.rutaIntentadaEnPrueba = evento.state?.current
}, { once: true }))
await pagina.goBack()
await pagina.waitForFunction(() => window.location.pathname === '/caja')
assert.equal(await pagina.evaluate(() => window.rutaIntentadaEnPrueba), '/reportes')
assert.equal(await pagina.getByRole('button', { name: 'Salir', exact: true }).count(), 1)
assert.equal(peticiones.filter((p) => p.tipo === 'agregado').length, lecturas)

assert.deepEqual(erroresJs, [])
await navegador.close()
console.log('Recorrido completado. Capturas en', `${SALIDA}-*.png`)

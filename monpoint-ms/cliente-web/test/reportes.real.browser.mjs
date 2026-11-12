/**
 * Reportes contra el Gateway real, sin interceptar peticiones.
 * Solo entornos de prueba: crea producto, cliente, vendedor y dos ventas.
 * Guarda una configuracion de prueba; restaura la anterior si existia.
 * Si no existia, permanece la nueva (la API no ofrece DELETE).
 * No forma parte de npm test. Requiere Vite, Chromium y servicios vivos.
 */
import assert from 'node:assert/strict'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { execFileSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import { resolve } from 'node:path'
const modulo = await import(process.env.PLAYWRIGHT_MODULE ?? 'playwright')
const { chromium } = modulo.default ?? modulo
const BASE = process.env.BASE_URL ?? 'http://localhost:5173'
const API = process.env.GATEWAY_URL ?? 'http://localhost:8080'
const SALIDA = resolve(process.env.SALIDA ?? '/tmp/reportes-real')
await mkdir(SALIDA, { recursive: true })
const resultado = { inicio: new Date().toISOString(), cliente: BASE, gateway: API, casos: [], errores: [] }
let token
let anterior
let configuracionModificada = false
async function api (ruta, { metodo = 'GET', cuerpo, credencial = token } = {}) {
  const r = await fetch(`${API}${ruta}`, {
    method: metodo,
    headers: { Authorization: `Bearer ${credencial}`, ...(cuerpo ? { 'Content-Type': 'application/json' } : {}) },
    ...(cuerpo ? { body: JSON.stringify(cuerpo) } : {}),
    signal: AbortSignal.timeout(10000),
  })
  const tipo = r.headers.get('content-type') ?? ''
  return { estado: r.status, datos: r.status === 204 ? null : tipo.includes('json') ? await r.json() : await r.text() }
}
async function esperar (leer, cumple, descripcion) {
  for (let i = 0; i < 40; i++) {
    const dato = await leer()
    if (cumple(dato)) return dato
    await new Promise((r) => setTimeout(r, 500))
  }
  throw new Error(`No llego a observarse: ${descripcion}`)
}
const navegador = await chromium.launch({
  ...(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {}),
  args: process.env.CHROMIUM_NO_SANDBOX === '1' ? ['--no-sandbox'] : [],
})
const pagina = await navegador.newPage({ viewport: { width: 1280, height: 980 }, acceptDownloads: true })
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
async function consultar (periodo, desde, hasta) {
  await pagina.getByLabel('Granularidad', { exact: true }).selectOption(periodo)
  await pagina.getByLabel('Desde', { exact: true }).fill(desde)
  await pagina.getByLabel('Hasta', { exact: true }).fill(hasta)
  const respuesta = pagina.waitForResponse((r) => r.url().startsWith(`${API}/reportes/ventas/agregado?`))
  await pagina.getByRole('button', { name: 'Consultar', exact: true }).click()
  const r = await respuesta
  assert.equal(r.status(), 200)
  const datos = await r.json()
  await pagina.getByRole('img', { name: `Ventas confirmadas entre ${desde} y ${hasta}`, exact: true }).waitFor()
  return datos
}
try {
  await pagina.goto(`${BASE}/login`)
  token = await entrar('admin@monpoint.com', process.env.ADMIN_DEFAULT_PASSWORD || 'Admin123!')
  const previa = await api('/reportes/configuracion')
  assert.ok([200, 404].includes(previa.estado))
  anterior = previa.estado === 200 ? previa.datos : null
  resultado.configuracionInicial = { estado: previa.estado }
  await pagina.getByRole('link', { name: 'Reportes', exact: false }).click()
  await pagina.waitForURL('**/reportes')
  await pagina.getByRole('img').waitFor()
  const botonConfig = pagina.getByRole('button', { name: anterior ? 'Editar' : 'Registrar datos de empresa', exact: true })
  await botonConfig.click()
  const modal = pagina.getByRole('dialog', { name: 'Datos de la empresa', exact: true })
  const etiqueta = Date.now().toString()
  const empresa = `Revision Reportes ${etiqueta}`
  await modal.getByLabel('Nombre de la empresa', { exact: true }).fill(empresa)
  await modal.getByLabel('RFC', { exact: true }).fill('SIN-FORMATO')
  const rechazada = pagina.waitForResponse((r) => r.url() === `${API}/reportes/configuracion` && r.request().method() === 'PUT')
  await modal.getByRole('button', { name: 'Guardar', exact: true }).click()
  const mala = await rechazada
  assert.equal(mala.status(), 400)
  resultado.rfcRechazado = await mala.json()
  assert.ok(resultado.rfcRechazado.campos.rfc)
  await modal.locator('#rfc-error').waitFor()
  assert.equal(await modal.locator('#rfc').getAttribute('aria-invalid'), 'true')
  assert.equal(await modal.locator('[role="alert"]').count(), 0)
  await pagina.screenshot({ path: `${SALIDA}/01-rfc-invalido.png`, fullPage: true })
  await modal.getByLabel('RFC', { exact: true }).fill('xaxx010101000')
  await modal.getByLabel('Dirección (opcional)', { exact: true }).fill('Calle de prueba 1')
  await modal.getByLabel('Teléfono (opcional)', { exact: true }).fill('5512345678')
  const guardada = pagina.waitForResponse((r) => r.url() === `${API}/reportes/configuracion` && r.request().method() === 'PUT')
  await modal.getByRole('button', { name: 'Guardar', exact: true }).click()
  const buena = await guardada
  assert.equal(buena.status(), 200)
  configuracionModificada = true
  resultado.configuracionGuardada = await buena.json()
  assert.equal(resultado.configuracionGuardada.rfc, 'XAXX010101000')
  await modal.waitFor({ state: 'hidden' })
  await pagina.getByText(empresa, { exact: true }).waitFor()
  resultado.casos.push(`Configuracion: GET ${previa.estado}, RFC invalido 400 junto al campo y PUT 200 con RFC normalizado`)

  // Datos nuevos, posteriores al arranque de ms-reportes: no se presupone
  // que existan proyecciones de eventos emitidos antes de declarar sus colas.
  const producto = await api('/inventario/productos', { metodo: 'POST', cuerpo: {
    nombre: `Producto reportes ${etiqueta}`, precio: 41.15, existencia: 10, umbralStock: 5,
  } })
  assert.equal(producto.estado, 201)
  const cliente = await api('/clientes', { metodo: 'POST', cuerpo: { nombre: `Cliente reportes ${etiqueta}` } })
  assert.equal(cliente.estado, 201)
  resultado.fixtures = { producto: producto.datos, cliente: cliente.datos }
  const partes = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Mexico_City', year: 'numeric', month: '2-digit', day: '2-digit',
  }).formatToParts(new Date())
  const parte = (tipo) => partes.find((p) => p.type === tipo).value
  const hoy = `${parte('year')}-${parte('month')}-${parte('day')}`
  const ruta = `/reportes/ventas/agregado?periodo=DIA&desde=${hoy}&hasta=${hoy}`
  const base = await api(ruta)
  assert.equal(base.estado, 200)
  resultado.antes = base.datos
  async function vender (cantidad, estado) {
    const alta = await esperar(() => api('/ventas', { metodo: 'POST', cuerpo: {
      clienteId: cliente.datos.id, lineas: [{ productoId: producto.datos.id, cantidad }],
    } }).then((r) => {
      if (r.estado === 202) return r
      const pendiente = r.estado === 400 && [
        `El producto no está disponible o está inactivo: ${producto.datos.id}`,
        'el cliente no está disponible; si acaba de darlo de alta, inténtelo de nuevo en unos segundos',
      ].includes(r.datos.detail)
      // Solo esos dos rechazos previos a emitir folio permiten reintento.
      assert.ok(pendiente, `Alta de venta inesperada: HTTP ${r.estado}`)
      return r
    }), (r) => r.estado === 202, 'replicas disponibles en ms-ventas')
    const fin = await esperar(() => api(`/ventas/${alta.datos.id}`),
      (r) => r.estado === 200 && r.datos.estado === estado, `venta ${estado}`)
    return { POST: alta.estado, venta: fin.datos }
  }
  resultado.confirmada = await vender(2, 'CONFIRMADA')
  const esperada = Math.round(Number(base.datos.total) * 100) + 8230
  resultado.despues = (await esperar(() => api(ruta),
    (r) => r.estado === 200 && Math.round(Number(r.datos.total) * 100) === esperada,
    'proyeccion de la confirmada por 82.30')).datos
  resultado.cancelada = await vender(99, 'CANCELADA')
  // Su ticket 409 confirma que el cierre CANCELADA ya llego a reportes.
  resultado.ticketCancelada = await esperar(() => api(`/reportes/ventas/${resultado.cancelada.venta.id}/ticket`),
    (r) => r.estado === 409, 'proyeccion de la cancelada')
  resultado.trasCancelada = (await api(ruta)).datos
  assert.equal(Math.round(Number(resultado.trasCancelada.total) * 100), esperada)
  resultado.casos.push('Venta confirmada 82.30 incrementa el agregado; cancelada 4073.85 llega a reportes y no suma')

  resultado.consultaUI = await consultar('DIA', hoy, hoy)
  assert.deepEqual(resultado.consultaUI, resultado.trasCancelada)
  assert.equal(await pagina.locator('figcaption tbody tr').count(), 1)
  assert.match(await pagina.locator('.total').innerText(), /\$/)
  await pagina.screenshot({ path: `${SALIDA}/02-agregado-empresa.png`, fullPage: true })
  const descripcion = await pagina.getByRole('img').getAttribute('aria-label')
  await pagina.getByLabel('Desde', { exact: true }).fill('2000-01-01')
  assert.equal(await pagina.getByRole('img').getAttribute('aria-label'), descripcion)
  resultado.ceros = await consultar('DIA', '2000-01-01', '2000-01-03')
  assert.equal(resultado.ceros.total, 0)
  assert.equal(resultado.ceros.puntos.length, 3)
  assert.equal(await pagina.locator('svg rect.vacia').count(), 3)
  assert.equal(await pagina.locator('figcaption tbody tr').count(), 3)
  resultado.casos.push('Grafica/tabla con tres periodos cero; cambiar el borrador no cambia la descripcion del resultado')
  await consultar('DIA', hoy, hoy)

  resultado.documentos = []
  for (const [titulo, archivo, contenido] of [
    ['Historial de ventas', `historial-ventas-${hoy}_${hoy}.pdf`, String(resultado.confirmada.venta.folio)],
    ['Listado de productos', 'listado-productos.pdf', producto.datos.nombre],
    ['Listado de clientes', 'listado-clientes.pdf', cliente.datos.nombre],
  ]) {
    const descarga = pagina.waitForEvent('download')
    await pagina.locator('li.documento').filter({ hasText: titulo }).getByRole('button', { name: 'Descargar PDF', exact: true }).click()
    const pdf = await descarga
    assert.equal(pdf.suggestedFilename(), archivo)
    assert.equal(await pdf.failure(), null)
    const destino = resolve(SALIDA, archivo)
    await pdf.saveAs(destino)
    const bytes = await readFile(destino)
    assert.equal(bytes.subarray(0, 5).toString(), '%PDF-')
    const texto = execFileSync(process.env.PDFTOTEXT_PATH ?? 'pdftotext', [destino, '-'], { encoding: 'utf8' })
    const normalizar = (valor) => valor.replace(/\s+/g, ' ').trim()
    assert.ok(normalizar(texto).includes(empresa), 'El PDF debe incluir la empresa guardada')
    assert.ok(normalizar(texto).includes(normalizar(contenido)), 'El PDF debe incluir el registro de prueba')
    if (titulo === 'Historial de ventas') {
      assert.ok(texto.includes('82.30'))
      assert.ok(!texto.includes('4073.85'))
    }
    resultado.documentos.push({ archivo, bytes: bytes.length, sha256: createHash('sha256').update(bytes).digest('hex'), textoEmpresaYRegistro: true })
  }
  const cabeceras = await fetch(`${API}/reportes/productos/listado`, { headers: { Authorization: `Bearer ${token}` } })
  resultado.cors = {
    servidor: cabeceras.headers.get('content-disposition'),
    navegador: await pagina.evaluate(async ({ API, token }) => {
      const r = await fetch(`${API}/reportes/productos/listado`, { headers: { Authorization: `Bearer ${token}` } })
      if (r.status !== 200) throw new Error(`PDF HTTP ${r.status}`)
      await r.blob()
      return r.headers.get('content-disposition')
    }, { API, token }),
  }
  await cabeceras.arrayBuffer()
  assert.ok(resultado.cors.servidor)
  assert.equal(resultado.cors.navegador, null)
  resultado.casos.push('Tres descargas UI: PDF real legible con empresa y registro; Content-Disposition solo visible fuera del navegador')

  await consultar('ANIO', '2020-01-01', hoy)
  const historial = pagina.locator('li.documento').filter({ hasText: 'Historial de ventas' })
  assert.equal(await historial.getByRole('button').isDisabled(), true)
  assert.match(await historial.innerText(), /366 días/)
  resultado.historialExcesivo = await api('/reportes/ventas/historial?desde=2020-01-01&hasta=' + hoy)
  assert.equal(resultado.historialExcesivo.estado, 400)
  resultado.agregadoExcesivo = await api('/reportes/ventas/agregado?periodo=DIA&desde=2020-01-01&hasta=' + hoy)
  assert.equal(resultado.agregadoExcesivo.estado, 400)
  resultado.casos.push('Rango anual valido para grafica, bloqueado para historial; limites reales 366 dias y 1000 puntos devuelven 400')
  await consultar('DIA', hoy, hoy)
  await pagina.setViewportSize({ width: 390, height: 844 })
  assert.ok(await pagina.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth))
  await pagina.screenshot({ path: `${SALIDA}/03-movil.png`, fullPage: true })

  const correo = `reportes.${etiqueta}@monpoint.test`
  const clave = process.env.PRUEBA_VENDEDOR_PASSWORD || 'Vendedor123!'
  const vendedor = await api('/auth/register', { metodo: 'POST', cuerpo: {
    nombre: 'Vendedor prueba reportes', correo, password: clave, rol: 'VENDEDOR',
  } })
  assert.equal(vendedor.estado, 201)
  await pagina.getByRole('button', { name: 'Salir', exact: true }).click()
  await pagina.waitForURL('**/login')
  const tokenVendedor = await entrar(correo, clave)
  assert.equal(await pagina.getByRole('link', { name: 'Reportes', exact: false }).count(), 0)
  resultado.vendedor = {}
  for (const entrada of [ruta, '/reportes/productos/listado', '/reportes/clientes/listado', `/reportes/ventas/historial?desde=${hoy}&hasta=${hoy}`]) {
    const r = await api(entrada, { credencial: tokenVendedor })
    assert.equal(r.estado, 403)
    resultado.vendedor[entrada] = r
  }
  const lectura = await api('/reportes/configuracion', { credencial: tokenVendedor })
  assert.equal(lectura.estado, 200)
  const escritura = await api('/reportes/configuracion', { metodo: 'PUT', credencial: tokenVendedor,
    cuerpo: { nombreEmpresa: empresa, rfc: 'XAXX010101000' } })
  assert.equal(escritura.estado, 403)
  resultado.vendedor.configuracion = { GET: lectura.estado, PUT: escritura }
  resultado.casos.push('VENDEDOR sin enlace; agregado y tres PDF 403 reales; configuracion GET 200 y PUT 403')
  assert.deepEqual(resultado.errores, [])
  resultado.resultado = 'PASS'
} catch (e) {
  resultado.resultado = 'FAIL'
  resultado.fallo = e.message
  await pagina.screenshot({ path: `${SALIDA}/fallo.png`, fullPage: true }).catch(() => {})
  process.exitCode = 1
} finally {
  if (configuracionModificada && anterior) {
    const restaurada = await api('/reportes/configuracion', { metodo: 'PUT', cuerpo: {
      nombreEmpresa: anterior.nombreEmpresa, rfc: anterior.rfc, direccion: anterior.direccion, telefono: anterior.telefono,
    } }).catch(() => ({ estado: 0 }))
    resultado.restauracionConfiguracion = restaurada.estado
    if (restaurada.estado !== 200) { resultado.resultado = 'FAIL'; process.exitCode = 1 }
  } else resultado.configuracionNuevaConservada = configuracionModificada
  resultado.fin = new Date().toISOString()
  await writeFile(`${SALIDA}/resultado.json`, JSON.stringify(resultado, null, 2))
  await navegador.close()
}
console.log('Reportes reales:', resultado.resultado, resultado.casos, resultado.fallo ?? '')

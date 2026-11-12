import { salirDesdeMenu } from './concha-fixture.mjs'
/** Usuarios y ticket con servicios vivos, sin intercepciones.
 * Solo prueba: crea dos cuentas y un producto, cobra dos ventas y desactiva
 * unicamente sus cuentas al terminar. No es idempotente ni parte de npm test. */
import assert from 'node:assert/strict'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { execFileSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import { resolve } from 'node:path'
const modulo = await import(process.env.PLAYWRIGHT_MODULE ?? 'playwright')
const { chromium } = modulo.default ?? modulo
const BASE = process.env.BASE_URL ?? 'http://localhost:5173'
const API = process.env.GATEWAY_URL ?? 'http://localhost:8080'
const SALIDA = resolve(process.env.SALIDA ?? '/tmp/usuarios-ticket-real')
await mkdir(SALIDA, { recursive: true })
const resultado = { inicio: new Date().toISOString(), cliente: BASE, gateway: API, casos: [], errores: [] }
let bootstrap, token, actor, otro
async function api (ruta, { metodo = 'GET', cuerpo, credencial = token } = {}) {
  const r = await fetch(`${API}${ruta}`, { method: metodo,
    headers: { ...(credencial ? { Authorization: `Bearer ${credencial}` } : {}), ...(cuerpo ? { 'Content-Type': 'application/json' } : {}) },
    ...(cuerpo ? { body: JSON.stringify(cuerpo) } : {}), signal: AbortSignal.timeout(10000),
  })
  return { estado: r.status, datos: r.status === 204 ? null : await r.json() }
}
async function loginApi (correo, password) {
  return api('/auth/login', { metodo: 'POST', credencial: null, cuerpo: { correo, password } })
}
const navegador = await chromium.launch({
  ...(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {}),
  args: process.env.CHROMIUM_NO_SANDBOX === '1' ? ['--no-sandbox'] : [],
})
const pagina = await navegador.newPage({ viewport: { width: 1280, height: 980 }, acceptDownloads: true })
pagina.on('pageerror', (e) => resultado.errores.push(e.message))
const archivos = []
pagina.on('download', (d) => archivos.push(d))
let cobros = 0
pagina.on('request', (r) => { if (r.url() === `${API}/ventas` && r.method() === 'POST') cobros++ })
async function entrar (correo, password) {
  const respuesta = pagina.waitForResponse((r) => r.url() === `${API}/auth/login` && r.request().method() === 'POST')
  await pagina.getByLabel('Correo', { exact: true }).fill(correo)
  await pagina.getByLabel('Contraseña', { exact: true }).fill(password)
  await pagina.getByRole('button', { name: 'Entrar', exact: true }).click()
  const r = await respuesta
  assert.equal(r.status(), 200)
  await pagina.waitForURL('**/caja')
  return (await r.json()).token
}
async function salir () {
  await salirDesdeMenu(pagina)
  await pagina.waitForURL('**/login')
}
async function usuarios () {
  await pagina.getByRole('link', { name: 'Usuarios', exact: false }).click()
  await pagina.waitForURL('**/usuarios')
  await pagina.locator('.estado[aria-busy="false"]').waitFor()
}
async function guardar (modal, metodo, ruta, estado) {
  const respuesta = pagina.waitForResponse((r) => r.url() === `${API}${ruta}` && r.request().method() === metodo)
  await modal.getByRole('button', { name: 'Guardar', exact: true }).click()
  const r = await respuesta
  assert.equal(r.status(), estado)
  const datos = await r.json()
  if (estado < 300) await modal.waitFor({ state: 'hidden' })
  return datos
}
async function esperar (leer, cumple) {
  for (let i = 0; i < 40; i++) {
    const v = await leer()
    if (cumple(v)) return v
    await new Promise((r) => setTimeout(r, 500))
  }
  throw new Error('Se agoto la espera de la respuesta esperada')
}
try {
  const entrada = await loginApi('admin@monpoint.com', process.env.ADMIN_DEFAULT_PASSWORD || 'Admin123!')
  assert.equal(entrada.estado, 200)
  bootstrap = entrada.datos.token
  const etiqueta = Date.now().toString()
  const clave = process.env.PRUEBA_VENDEDOR_PASSWORD || 'Vendedor123!'
  const claveNueva = process.env.PRUEBA_PASSWORD_NUEVA || 'Nueva123!Clave'
  const correoActor = `admin.usuarios.${etiqueta}@monpoint.test`
  const correoNuevo = `perfil.usuarios.${etiqueta}@monpoint.test`
  const correoOtro = `cajero.usuarios.${etiqueta}@monpoint.test`
  const nombreActor = `Admin usuarios ${etiqueta}`
  const nombreNuevo = `Perfil usuarios ${etiqueta}`
  const nombreOtro = `Cajero usuarios ${etiqueta}`
  const creada = await api('/auth/register', { metodo: 'POST', credencial: bootstrap,
    cuerpo: { nombre: nombreActor, correo: correoActor, password: clave, rol: 'ADMIN' } })
  assert.equal(creada.estado, 201)
  actor = creada.datos
  resultado.actorInicial = actor
  await pagina.goto(`${BASE}/login`)
  token = await entrar(correoActor, clave)
  await usuarios()
  await pagina.getByRole('button', { name: 'Nuevo usuario', exact: true }).click()
  const alta = pagina.getByRole('dialog', { name: 'Nuevo usuario', exact: true })
  await alta.getByLabel('Nombre', { exact: true }).fill(nombreOtro)
  await alta.getByLabel('Correo', { exact: true }).fill(correoOtro)
  await alta.getByLabel('Contraseña', { exact: true }).fill('corta')
  resultado.politica = await guardar(alta, 'POST', '/auth/register', 400)
  assert.ok(resultado.politica.errores.length >= 3)
  await alta.locator('#password-error').waitFor()
  for (const regla of resultado.politica.errores) assert.ok((await alta.locator('#password-error').innerText()).includes(regla))
  assert.equal(await alta.locator('[role="alert"]').count(), 0)
  await pagina.screenshot({ path: `${SALIDA}/01-politica.png`, fullPage: true })
  await alta.getByLabel('Contraseña', { exact: true }).fill(clave)
  await alta.getByLabel('Correo', { exact: true }).fill('admin@monpoint.com')
  resultado.duplicado = await guardar(alta, 'POST', '/auth/register', 409)
  await alta.locator('#correo-error').waitFor()
  assert.equal(await alta.locator('[role="alert"]').count(), 0)
  await alta.getByLabel('Correo', { exact: true }).fill(correoOtro)
  otro = await guardar(alta, 'POST', '/auth/register', 201)
  resultado.altaUI = otro
  await pagina.getByText(correoOtro, { exact: true }).waitFor()
  assert.equal(otro.rol, 'VENDEDOR')
  resultado.casos.push('Alta UI: politica 400 como lista en el campo; correo duplicado 409; alta VENDEDOR 201')

  const filaOtro = pagina.locator('tbody tr').filter({ hasText: correoOtro })
  await filaOtro.getByRole('button', { name: 'Editar', exact: true }).click()
  const edicion = pagina.getByRole('dialog', { name: 'Editar usuario', exact: true })
  assert.equal(await edicion.getByLabel('Contraseña nueva (opcional)', { exact: true }).inputValue(), '')
  await edicion.getByLabel('Rol', { exact: true }).selectOption('ADMIN')
  resultado.cambioRolAdmin = await guardar(edicion, 'PUT', `/auth/usuarios/${otro.id}`, 200)
  const loginAdmin = await loginApi(correoOtro, clave)
  assert.equal(loginAdmin.estado, 200)
  assert.equal(loginAdmin.datos.rol, 'ADMIN')
  const tokenAnteriorAdmin = loginAdmin.datos.token
  await filaOtro.getByRole('button', { name: 'Editar', exact: true }).click()
  await edicion.getByLabel('Contraseña nueva (opcional)', { exact: true }).fill(claveNueva)
  resultado.cambioPassword = await guardar(edicion, 'PUT', `/auth/usuarios/${otro.id}`, 200)
  resultado.passwordAnterior = (await loginApi(correoOtro, clave)).estado
  assert.equal(resultado.passwordAnterior, 401)
  const nueva = await loginApi(correoOtro, claveNueva)
  assert.equal(nueva.estado, 200)
  resultado.passwordNueva = nueva.estado
  await filaOtro.getByRole('button', { name: 'Editar', exact: true }).click()
  await edicion.getByLabel('Rol', { exact: true }).selectOption('VENDEDOR')
  resultado.cambioRolVendedor = await guardar(edicion, 'PUT', `/auth/usuarios/${otro.id}`, 200)
  const rolViejo = await api('/auth/usuarios', { credencial: tokenAnteriorAdmin })
  assert.equal(rolViejo.estado, 200)
  resultado.tokenPrevioAlCambioDeRol = { GET_usuarios: rolViejo.estado }
  resultado.casos.push('PUT UI cambia rol y password; login anterior 401 y nuevo 200; el JWT previo conserva sus permisos')

  const correoExterno = `externo.usuarios.${etiqueta}@monpoint.test`
  resultado.cambioCorreoPorOtroAdmin = await api(`/auth/usuarios/${actor.id}`, { metodo: 'PUT', credencial: bootstrap,
    cuerpo: { nombre: nombreActor, correo: correoExterno } })
  assert.equal(resultado.cambioCorreoPorOtroAdmin.estado, 200)
  await pagina.getByRole('link', { name: 'Caja', exact: false }).click()
  await pagina.waitForURL('**/caja')
  await usuarios()
  const filaPropia = pagina.locator('tbody tr').filter({ hasText: correoExterno })
  await filaPropia.getByText('usted', { exact: true }).waitFor()
  await filaPropia.getByRole('button', { name: 'Editar', exact: true }).click()
  assert.equal(await edicion.getByLabel('Rol', { exact: true }).isDisabled(), true)
  await edicion.getByLabel('Nombre', { exact: true }).fill(nombreNuevo)
  await edicion.getByLabel('Correo', { exact: true }).fill(correoNuevo)
  resultado.perfilPropio = await guardar(edicion, 'PUT', `/auth/usuarios/${actor.id}`, 200)
  const propiaNueva = pagina.locator('tbody tr').filter({ hasText: correoNuevo })
  await propiaNueva.getByText('usted', { exact: true }).waitFor()
  assert.equal(await propiaNueva.getByRole('button', { name: 'Desactivar', exact: true }).count(), 0)
  assert.ok((await pagina.locator('header.barra .usuario').innerText()).includes(nombreNuevo))
  await propiaNueva.getByRole('button', { name: 'Editar', exact: true }).click()
  assert.equal(await edicion.getByLabel('Rol', { exact: true }).isDisabled(), true)
  assert.equal(await edicion.getByLabel('Contraseña nueva (opcional)', { exact: true }).inputValue(), '')
  await edicion.getByRole('button', { name: 'Cancelar', exact: true }).click()
  resultado.rolPropioProhibido = await api(`/auth/usuarios/${actor.id}`, { metodo: 'PUT',
    cuerpo: { nombre: nombreNuevo, correo: correoNuevo, rol: 'VENDEDOR' } })
  assert.equal(resultado.rolPropioProhibido.estado, 400)
  resultado.desactivacionPropia = await api(`/auth/usuarios/${actor.id}/desactivar`, { metodo: 'PATCH' })
  assert.equal(resultado.desactivacionPropia.estado, 400)
  await pagina.getByLabel('Buscar por nombre, correo o rol', { exact: true }).fill(correoNuevo)
  await pagina.screenshot({ path: `${SALIDA}/02-perfil-propio.png`, fullPage: true })
  resultado.casos.push('Correo propio actualizado: cabecera y fila propia sincronizadas; rol bloqueado; API rechaza cambio de rol/desactivacion con 400')

  const altaProducto = await api('/inventario/productos', { metodo: 'POST', credencial: bootstrap,
    cuerpo: { nombre: `Ticket usuarios ${etiqueta}`, precio: 41.15, existencia: 10, umbralStock: 2 } })
  assert.equal(altaProducto.estado, 201)
  resultado.producto = altaProducto.datos
  resultado.tickets = []
  async function cobrarYDescargar (rol) {
    await pagina.getByLabel('Buscar producto', { exact: true }).fill(resultado.producto.nombre)
    await pagina.getByRole('button', { name: 'Actualizar catálogo', exact: true }).click()
    await pagina.getByRole('button').filter({ has: pagina.locator('.nombre', { hasText: resultado.producto.nombre }) }).click()
    const intentos = []
    let venta
    for (let i = 0; i < 20; i++) {
      const respuesta = pagina.waitForResponse((r) => r.url() === `${API}/ventas` && r.request().method() === 'POST')
      // El buscador conserva el foco: Ctrl+Enter debe cobrar la cantidad ya
      // elegida, sin volver a agregar el resultado que sigue visible.
      await pagina.getByLabel('Buscar producto', { exact: true }).press('Control+Enter')
      const r = await respuesta
      intentos.push(r.status())
      const datos = await r.json()
      if (r.status() === 202) { venta = datos; break }
      assert.equal(r.status(), 400)
      assert.equal(datos.detail, `El producto no está disponible o está inactivo: ${resultado.producto.id}`)
      await new Promise((r) => setTimeout(r, 500))
    }
    assert.ok(venta)
    await pagina.getByRole('heading', { name: 'Venta confirmada', exact: true }).waitFor({ timeout: 20000 })
    const detalle = await api(`/ventas/${venta.id}`)
    assert.equal(detalle.estado, 200)
    assert.equal(detalle.datos.estado, 'CONFIRMADA')
    assert.equal(detalle.datos.total, 41.15)
    const antes = cobros
    const pedidos = []
    const inicioArchivos = archivos.length
    for (let i = 0; i < 20; i++) {
      const respuesta = pagina.waitForResponse((r) => r.url() === `${API}/reportes/ventas/${venta.id}/ticket`)
      await pagina.getByRole('button', { name: `Descargar ticket del folio ${venta.folio} (PDF)`, exact: true }).click()
      const r = await respuesta
      pedidos.push(r.status())
      if (r.status() === 200) break
      assert.equal(r.status(), 404)
      assert.equal((await r.json()).codigo, 'VENTA_NO_DISPONIBLE')
      await new Promise((r) => setTimeout(r, 500))
    }
    assert.equal(pedidos.at(-1), 200)
    const pdf = await esperar(() => archivos[inicioArchivos], Boolean)
    const nombre = `ticket-folio-${venta.folio}.pdf`
    assert.equal(pdf.suggestedFilename(), nombre)
    assert.equal(await pdf.failure(), null)
    const rutaPdf = resolve(SALIDA, nombre)
    await pdf.saveAs(rutaPdf)
    const bytes = await readFile(rutaPdf)
    assert.equal(bytes.subarray(0, 5).toString(), '%PDF-')
    const texto = execFileSync(process.env.PDFTOTEXT_PATH ?? 'pdftotext', [rutaPdf, '-'], { encoding: 'utf8' }).replace(/\s+/g, ' ')
    assert.ok(texto.includes('Comprobante de venta'))
    assert.ok(texto.includes(`Folio: ${venta.folio}`))
    assert.ok(texto.includes(resultado.producto.nombre))
    assert.ok(texto.includes('$41.15'))
    assert.equal(cobros, antes, 'descargar no vuelve a cobrar')
    resultado.tickets.push({ rol, POST_ventas: intentos, venta: detalle.datos, GET_ticket: pedidos,
      archivo: nombre, bytes: bytes.length, sha256: createHash('sha256').update(bytes).digest('hex'), textoContrastado: true })
    await pagina.setViewportSize({ width: 390, height: 844 })
    assert.ok(await pagina.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
    await pagina.screenshot({ path: `${SALIDA}/ticket-${rol}.png`, fullPage: true })
    await pagina.setViewportSize({ width: 1280, height: 980 })
  }
  await salir()
  token = await entrar(correoOtro, claveNueva)
  assert.equal(await pagina.getByRole('link', { name: 'Usuarios', exact: false }).count(), 0)
  resultado.vendedor = {}
  for (const [metodo, ruta, cuerpo] of [
    ['GET', '/auth/usuarios'],
    ['POST', '/auth/register', { nombre: 'No debe crearse', correo: `denegado.${etiqueta}@monpoint.test`, password: clave, rol: 'VENDEDOR' }],
    ['PATCH', `/auth/usuarios/${actor.id}/desactivar`],
    ['PUT', `/auth/usuarios/${actor.id}`, { nombre: nombreNuevo, correo: correoNuevo }],
  ]) {
    const r = await api(ruta, { metodo, cuerpo })
    assert.equal(r.estado, 403)
    resultado.vendedor[`${metodo} ${ruta}`] = r
  }
  await cobrarYDescargar('VENDEDOR')
  resultado.casos.push('VENDEDOR: sin enlace Usuarios, cuatro operaciones ajenas 403 y ticket propio 200 desde Caja')
  await salir()
  token = await entrar(correoNuevo, clave)
  await cobrarYDescargar('ADMIN')
  resultado.casos.push('ADMIN: ticket de otra venta confirmada 200 desde Caja; ambos PDF contienen su folio/producto/total y ningun GET repite el POST')

  await usuarios()
  await pagina.getByLabel('Buscar por nombre, correo o rol', { exact: true }).fill(correoOtro)
  const filaBaja = pagina.locator('tbody tr').filter({ hasText: correoOtro })
  await filaBaja.getByRole('button', { name: 'Desactivar', exact: true }).click()
  const baja = pagina.getByRole('dialog', { name: 'Desactivar usuario', exact: true })
  await baja.getByRole('button', { name: 'Cancelar', exact: true }).click()
  assert.ok((await api('/auth/usuarios')).datos.some((u) => u.id === otro.id))
  await filaBaja.getByRole('button', { name: 'Desactivar', exact: true }).click()
  assert.ok((await baja.innerText()).includes('Una sesión ya abierta puede seguir activa'))
  await pagina.screenshot({ path: `${SALIDA}/03-desactivar.png`, fullPage: true })
  const respuesta = pagina.waitForResponse((r) => r.url() === `${API}/auth/usuarios/${otro.id}/desactivar`)
  const recarga = pagina.waitForResponse((r) => r.url() === `${API}/auth/usuarios` && r.request().method() === 'GET')
  await baja.getByRole('button', { name: 'Desactivar', exact: true }).click()
  assert.equal((await respuesta).status(), 204)
  assert.ok(!(await (await recarga).json()).some((u) => u.id === otro.id))
  await pagina.getByText('Ninguna cuenta activa coincide con la búsqueda.', { exact: true }).waitFor()
  resultado.desactivacion = { PATCH: 204, ausenteDelListado: true, loginPosterior: (await loginApi(correoOtro, claveNueva)).estado }
  assert.equal(resultado.desactivacion.loginPosterior, 401)
  resultado.casos.push('Desactivacion UI 204 tras cancelar una vez; ausencia tras recarga y nuevo login 401')
  assert.deepEqual(resultado.errores, [])
  resultado.resultado = 'PASS'
} catch (e) {
  resultado.resultado = 'FAIL'
  resultado.fallo = e.message
  await pagina.screenshot({ path: `${SALIDA}/fallo.png`, fullPage: true }).catch(() => {})
  process.exitCode = 1
} finally {
  resultado.limpieza = []
  for (const u of [otro, actor].filter(Boolean)) {
    const r = await api(`/auth/usuarios/${u.id}/desactivar`, { metodo: 'PATCH', credencial: bootstrap }).catch(() => ({ estado: 0 }))
    resultado.limpieza.push({ id: u.id, estado: r.estado })
    if (r.estado !== 204) { resultado.resultado = 'FAIL'; process.exitCode = 1 }
  }
  resultado.fin = new Date().toISOString()
  await writeFile(`${SALIDA}/resultado.json`, JSON.stringify(resultado, null, 2))
  await navegador.close()
}
console.log('Usuarios y ticket reales:', resultado.resultado, resultado.casos, resultado.fallo ?? '')

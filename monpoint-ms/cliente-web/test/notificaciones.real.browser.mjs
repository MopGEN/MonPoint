/**
 * Recorrido contra el Gateway real, sin interceptar peticiones.
 * Crea un producto, una venta de cinco unidades y un vendedor de prueba.
 * No borra ni modifica datos ajenos. NO ejecutar contra produccion.
 * Requiere Vite en localhost:5173 y auth/inventario/clientes/ventas/notificaciones.
 * Ver README para dependencias y variables; no forma parte de npm test.
 */
import assert from 'node:assert/strict'
import { mkdir, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'

const modulo = await import(process.env.PLAYWRIGHT_MODULE ?? 'playwright')
const { chromium } = modulo.default ?? modulo
const BASE = process.env.BASE_URL ?? 'http://localhost:5173'
const API = process.env.GATEWAY_URL ?? 'http://localhost:8080'
const SALIDA = resolve(process.env.SALIDA ?? '/tmp/concha-notificaciones-real')
await mkdir(SALIDA, { recursive: true })
const datos = { inicio: new Date().toISOString(), cliente: BASE, gateway: API, casos: [], errores: [] }
let token
async function api (ruta, { metodo = 'GET', cuerpo, credencial = token } = {}) {
  const r = await fetch(`${API}${ruta}`, {
    method: metodo,
    headers: {
      ...(credencial ? { Authorization: `Bearer ${credencial}` } : {}),
      ...(cuerpo ? { 'Content-Type': 'application/json' } : {}),
    },
    ...(cuerpo ? { body: JSON.stringify(cuerpo) } : {}),
    signal: AbortSignal.timeout(10000),
  })
  return { estado: r.status, datos: r.status === 204 ? null : await r.json() }
}
async function esperar (comprobar) {
  const limite = Date.now() + 30000
  while (Date.now() < limite) {
    const r = await comprobar()
    if (r) return r
    await new Promise((r) => setTimeout(r, 300))
  }
  throw new Error('Agotado el plazo de disponibilidad de replica/saga/alerta')
}
const navegador = await chromium.launch({
  ...(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {}),
  args: process.env.CHROMIUM_NO_SANDBOX === '1' ? ['--no-sandbox'] : [],
})
const pagina = await navegador.newPage({ viewport: { width: 1280, height: 900 } })
pagina.on('pageerror', (e) => datos.errores.push(e.message))
async function entrar (correo, password) {
  await pagina.getByLabel('Correo', { exact: true }).fill(correo)
  await pagina.getByLabel('Contrasena', { exact: true }).fill(password)
  await pagina.getByRole('button', { name: 'Entrar', exact: true }).click()
  await pagina.waitForURL('**/caja')
}
try {
  await pagina.goto(`${BASE}/login`)
  const respuestaLogin = pagina.waitForResponse((r) => r.url() === `${API}/auth/login` && r.request().method() === 'POST')
  await entrar('admin@monpoint.com', process.env.ADMIN_DEFAULT_PASSWORD || 'Admin123!')
  const login = await respuestaLogin
  assert.equal(login.status(), 200)
  token = (await login.json()).token
  assert.ok(token)

  const etiqueta = Date.now().toString()
  const producto = await api('/inventario/productos', { metodo: 'POST', cuerpo: {
    nombre: `Revision concha ${etiqueta}`, descripcion: 'Recorrido real del cliente',
    precio: 41.15, existencia: 10, umbralStock: 5,
  } })
  assert.equal(producto.estado, 201)
  datos.producto = producto.datos
  // Solo estos 400 permiten repetir el POST: ocurren antes de crear la venta.
  const creada = await esperar(async () => {
    const r = await api('/ventas', { metodo: 'POST', cuerpo: {
      clienteId: null, lineas: [{ productoId: producto.datos.id, cantidad: 5 }],
    } })
    if (r.estado === 400 && r.datos.detail === `El producto no está disponible o está inactivo: ${producto.datos.id}`) return null
    assert.equal(r.estado, 202)
    assert.ok(r.datos.id)
    return r.datos
  })
  const venta = await esperar(async () => {
    const r = await api(`/ventas/${creada.id}`)
    assert.equal(r.estado, 200)
    if (r.datos.estado === 'PENDIENTE') return null
    assert.equal(r.datos.estado, 'CONFIRMADA')
    return r.datos
  })
  assert.equal(Number(venta.total), 205.75)
  datos.venta = venta
  const aviso = await esperar(async () => {
    const r = await api('/notificaciones?leida=false')
    assert.equal(r.estado, 200)
    return r.datos.find((n) => n.productoId === producto.datos.id)
  })
  assert.equal(aviso.existenciaResultante, 5)
  datos.antes = aviso

  await pagina.getByRole('link', { name: 'Notificaciones', exact: false }).click()
  await pagina.waitForURL('**/notificaciones')
  const fila = pagina.locator('tbody tr').filter({ hasText: producto.datos.nombre })
  await fila.waitFor()
  assert.ok((await fila.innerText()).includes('Sin leer'))
  await pagina.screenshot({ path: `${SALIDA}/01-alerta-real.png`, fullPage: true })
  const respuestaPut = pagina.waitForResponse((r) => r.url() === `${API}/notificaciones/${aviso.id}/leida` && r.request().method() === 'PUT')
  await fila.getByRole('button', { name: 'Marcar leída', exact: true }).click()
  assert.equal((await respuestaPut).status(), 204)
  await fila.waitFor({ state: 'detached' })
  await pagina.getByRole('button', { name: 'Leídas', exact: true }).click()
  await fila.waitFor()
  assert.equal(await fila.getByRole('button').count(), 0)
  const leidas = await api('/notificaciones?leida=true')
  datos.despues = leidas.datos.find((n) => n.id === aviso.id)
  assert.equal(datos.despues.leida, true)
  assert.equal(datos.despues.activa, false)
  assert.ok(datos.despues.fechaLectura)
  datos.casos.push('ADMIN: lista real, PUT 204, desaparece de Sin leer y aparece en Leidas')
  await pagina.setViewportSize({ width: 390, height: 844 })
  assert.ok(await pagina.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth))
  await pagina.screenshot({ path: `${SALIDA}/02-leida-movil.png`, fullPage: true })

  const correo = `concha.${etiqueta}@monpoint.test`
  const clave = process.env.PRUEBA_VENDEDOR_PASSWORD || 'Vendedor123!'
  const vendedor = await api('/auth/register', { metodo: 'POST', cuerpo: {
    nombre: 'Vendedor prueba concha', correo, password: clave, rol: 'VENDEDOR',
  } })
  assert.equal(vendedor.estado, 201)
  await pagina.getByRole('button', { name: 'Salir', exact: true }).click()
  await pagina.waitForURL('**/login')
  const loginVendedor = pagina.waitForResponse((r) => r.url() === `${API}/auth/login` && r.request().method() === 'POST')
  await entrar(correo, clave)
  const acceso = await loginVendedor
  assert.equal(acceso.status(), 200)
  const tokenVendedor = (await acceso.json()).token
  assert.equal(await pagina.getByRole('link', { name: 'Notificaciones', exact: false }).count(), 0)
  const prohibida = await api('/notificaciones?leida=false', { credencial: tokenVendedor })
  assert.equal(prohibida.estado, 403)
  datos.rechazoVendedor = prohibida
  datos.casos.push('VENDEDOR real: enlace oculto y API 403 con ProblemDetail')
  await pagina.screenshot({ path: `${SALIDA}/03-vendedor.png`, fullPage: true })
  assert.deepEqual(datos.errores, [])
  datos.resultado = 'PASS'
} catch (e) {
  datos.resultado = 'FAIL'
  datos.fallo = e.message
  await pagina.screenshot({ path: `${SALIDA}/fallo.png`, fullPage: true }).catch(() => {})
  throw e
} finally {
  datos.fin = new Date().toISOString()
  await writeFile(`${SALIDA}/resultado.json`, JSON.stringify(datos, null, 2))
  await navegador.close()
}
console.log('Recorrido real:', datos.resultado, datos.casos)

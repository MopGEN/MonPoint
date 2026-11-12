/** Ticket en Caja con Gateway simulado. El cuerpo PDF prueba transporte;
 * la validez del documento se contrasta en usuarios-ticket.real.browser.mjs. */
import assert from 'node:assert/strict'
import { mkdir, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'
const modulo = await import(process.env.PLAYWRIGHT_MODULE ?? 'playwright')
const { chromium } = modulo.default ?? modulo
const BASE = process.env.BASE_URL ?? 'http://localhost:4173'
const API = process.env.GATEWAY_URL ?? 'http://localhost:8080'
const SALIDA = resolve(process.env.SALIDA ?? '/tmp/ticket-mock')
await mkdir(SALIDA, { recursive: true })
const navegador = await chromium.launch({
  ...(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {}),
  args: process.env.CHROMIUM_NO_SANDBOX === '1' ? ['--no-sandbox'] : [],
})
const resultado = { inicio: new Date().toISOString(), casos: [], errores: [] }
try {
  for (const [rol, caso] of [['ADMIN', 'descarga'], ['VENDEDOR', 'descarga'], ['ADMIN', 'cancelada'],
    ['VENDEDOR', 'seguimiento'], ['ADMIN', 'desmontaje'], ['ADMIN', 'sesion401']]) {
    const pagina = await navegador.newPage({ viewport: { width: 1280, height: 900 }, acceptDownloads: true })
    pagina.on('pageerror', (e) => resultado.errores.push(e.message))
    const archivos = []
    pagina.on('download', (d) => archivos.push(d.suggestedFilename()))
    let posts = 0, tickets = 0, confirmar = false, liberar = null
    const boton = pagina.getByRole('button', { name: 'Descargar ticket del folio 1042 (PDF)', exact: true })
    await pagina.route(`${API}/**`, async (ruta) => {
      const req = ruta.request(), path = new URL(req.url()).pathname
      let datos, estado = 200
      if (path === '/auth/login') datos = { token: 'token-simulado', expiraEn: 3600000, correo: 'caja@example.test', nombre: 'Cajero', rol }
      else if (path === '/inventario/productos') datos = [{ id: 'p1', nombre: 'Tinta', precio: 41.15, existencia: 10, activo: true }]
      else if (path === '/clientes') datos = []
      else if (path === '/ventas') {
        assert.equal(req.method(), 'POST')
        posts++
        estado = 202
        datos = { id: 'v1', folio: 1042, estado: 'PENDIENTE' }
      } else if (path === '/ventas/v1') datos = {
        id: 'v1', folio: 1042, estado: !confirmar ? 'PENDIENTE' : caso === 'cancelada' ? 'CANCELADA' : 'CONFIRMADA',
        total: 41.15, motivo: 'Existencia insuficiente', lineas: [{ nombreProducto: 'Tinta', cantidad: 1, subtotal: 41.15 }],
      }
      else if (path === '/reportes/ventas/v1/ticket') {
        tickets++
        assert.equal(req.method(), 'GET')
        assert.equal(req.headers().authorization, 'Bearer token-simulado')
        if (caso === 'sesion401') { estado = 401; datos = { detail: 'No autenticado' } }
        else if (caso === 'descarga' && tickets === 1) {
          estado = 404
          datos = { detail: 'La venta todavía no está disponible para reportes.', codigo: 'VENTA_NO_DISPONIBLE' }
        } else {
          if (caso === 'desmontaje' || (caso === 'descarga' && tickets === 2)) {
            await new Promise((r) => { liberar = r })
          }
          await ruta.fulfill({ status: 200, contentType: 'application/pdf', body: '%PDF-fixture-transporte' }).catch(() => {})
          return
        }
      } else throw new Error(`Ruta no prevista: ${path}`)
      await ruta.fulfill({ status: estado, contentType: 'application/json', body: JSON.stringify(datos) })
    })
    await pagina.goto(`${BASE}/login`)
    await pagina.getByLabel('Correo', { exact: true }).fill('caja@example.test')
    await pagina.getByLabel('Contrasena', { exact: true }).fill('simulada')
    await pagina.getByRole('button', { name: 'Entrar', exact: true }).click()
    await pagina.getByRole('button', { name: 'Agregar Tinta', exact: true }).click()
    await pagina.clock.install()
    await pagina.getByRole('button', { name: 'Cobrar', exact: true }).click()
    await pagina.getByRole('heading', { name: 'Venta en proceso', exact: true }).waitFor()
    assert.equal(await boton.count(), 0, 'una PENDIENTE no ofrece ticket')
    if (caso === 'seguimiento') {
      await pagina.clock.fastForward(10001)
      await pagina.getByRole('heading', { name: 'Seguimiento de operaciones', exact: true }).waitFor()
      assert.equal(await boton.count(), 0)
      confirmar = true
      await pagina.getByRole('button', { name: 'Consultar de nuevo', exact: true }).click()
      await pagina.getByText('✓ Venta confirmada · Total cobrado: $41.15', { exact: true }).waitFor()
    } else {
      confirmar = true
      await pagina.clock.runFor(801)
      await pagina.getByRole('heading', { name: caso === 'cancelada' ? 'Venta cancelada' : 'Venta confirmada', exact: true }).waitFor()
    }
    if (caso === 'cancelada') {
      assert.equal(await boton.count(), 0)
      assert.equal(tickets, 0)
    } else if (caso === 'sesion401') {
      await boton.click()
      await pagina.waitForURL('**/login?expirada=1')
      assert.equal(archivos.length, 0)
      assert.equal(tickets, 1)
    } else if (caso === 'desmontaje') {
      const aborto = pagina.waitForEvent('requestfailed', { predicate: (r) => r.url().endsWith('/ticket') })
      await boton.click()
      await pagina.getByRole('button', { name: 'Nueva venta', exact: true }).click()
      await aborto
      assert.ok(liberar)
      liberar()
      assert.equal(await boton.count(), 0)
      assert.equal(archivos.length, 0)
    } else {
      if (caso === 'descarga') {
        await boton.click()
        await pagina.getByText('La venta todavía no está disponible para reportes.', { exact: false }).waitFor()
        assert.equal(archivos.length, 0)
        assert.equal(tickets, 1, 'no se reintenta automaticamente')
        await pagina.screenshot({ path: `${SALIDA}/${rol}-404.png`, fullPage: true })
      }
      const descargado = pagina.waitForEvent('download')
      await boton.evaluate((b) => { b.click(); b.click() })
      if (caso === 'descarga') {
        await pagina.waitForFunction(() => document.querySelector('.ticket button')?.disabled === true)
        assert.ok(liberar)
        assert.equal(tickets, 2, 'doble clic, una sola nueva descarga')
        liberar()
      }
      assert.equal((await descargado).suggestedFilename(), 'ticket-folio-1042.pdf')
      assert.deepEqual(archivos, ['ticket-folio-1042.pdf'])
      await pagina.setViewportSize({ width: 390, height: 844 })
      assert.ok(await pagina.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
      await pagina.screenshot({ path: `${SALIDA}/${rol}-${caso}.png`, fullPage: true })
    }
    assert.equal(posts, 1, 'pedir ticket no repite el cobro')
    resultado.casos.push({ rol, caso, POST_ventas: posts, GET_ticket: tickets, archivos })
    await pagina.close()
  }
  assert.deepEqual(resultado.errores, [])
  resultado.resultado = 'PASS'
} finally {
  resultado.fin = new Date().toISOString()
  await writeFile(`${SALIDA}/resultado.json`, JSON.stringify(resultado, null, 2))
  await navegador.close()
}
console.log('Ticket simulado:', resultado.resultado, resultado.casos)

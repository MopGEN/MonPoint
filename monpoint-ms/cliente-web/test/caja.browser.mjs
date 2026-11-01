// Ejecutar con Vite activo. Ver README para instalar Playwright fuera del checkout.
import assert from 'node:assert/strict'
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ?? 'playwright')
const browser = await chromium.launch({
  executablePath: process.env.CHROMIUM_PATH ?? '/usr/bin/chromium',
  headless: true,
  args: ['--no-sandbox'],
})
try {
  for (const escenario of ['CONFIRMADA', 'CANCELADA', 'SIN_RESPUESTA', 'CANCELADA_HISTORICA']) {
    const page = await browser.newPage({ viewport: { width: 1280, height: 900 } })
    const errores = []
    page.on('pageerror', (e) => errores.push(e.message))
    let posts = 0,
      resolver = false
    await page.route('http://localhost:8080/**', async (route) => {
      const req = route.request(),
        ruta = new URL(req.url()).pathname
      let body,
        status = 200
      if (ruta === '/auth/login')
        body = {
          token: 'prueba-local',
          expiraEn: 3600000,
          correo: 'admin@example.test',
          nombre: 'Cajero',
          rol: 'ADMIN',
        }
      else if (ruta === '/inventario/productos')
        body = [
          { id: 'p1', nombre: 'Tinta para sello', precio: 41.15, existencia: 2, activo: true },
        ]
      else if (ruta === '/clientes') body = [{ id: 'c1', nombre: 'Cliente de prueba' }]
      else if (ruta === '/ventas' && req.method() === 'POST') {
        posts++
        status = 202
        assert.deepEqual(req.postDataJSON(), {
          clienteId: 'c1',
          lineas: [{ productoId: 'p1', cantidad: 3 }],
        })
        body = { id: 'v1', folio: 1042, estado: 'PENDIENTE' }
      } else if (ruta === '/ventas/v1') {
        body = {
          id: 'v1',
          folio: 1042,
          estado: escenario.startsWith('CANCELADA')
            ? 'CANCELADA'
            : escenario === 'SIN_RESPUESTA' && !resolver
              ? 'PENDIENTE'
              : 'CONFIRMADA',
          total: '123.45',
          motivo: 'existencia insuficiente de Tinta para sello, solicitado 3',
          productoIdRechazado: escenario === 'CANCELADA' ? 'p1' : null,
          lineas: [
            {
              productoId: 'p1',
              nombreProducto: 'Tinta para sello',
              cantidad: 3,
              subtotal: '123.45',
            },
          ],
        }
      } else throw new Error(`Ruta inesperada: ${ruta}`)
      await route.fulfill({
        status,
        contentType: 'application/json',
        headers: { 'Access-Control-Allow-Origin': '*' },
        body: JSON.stringify(body),
      })
    })
    await page.goto('http://127.0.0.1:5173/login')
    await page.getByLabel('Correo', { exact: true }).fill('admin@example.test')
    await page.getByLabel('Contrasena', { exact: true }).fill('prueba')
    await page.getByRole('button', { name: 'Entrar', exact: true }).click()
    await page.getByRole('button', { name: 'Agregar Tinta para sello' }).click()
    await page.getByLabel('Cantidad', { exact: true }).fill('3')
    await page.getByLabel('Cliente (opcional)').selectOption('c1')
    assert.equal(await page.locator('.total').textContent(), '$123.45')
    if (escenario === 'SIN_RESPUESTA') await page.clock.install()
    // Dos eventos antes de que Vue actualice disabled.
    await page.getByRole('button', { name: 'Cobrar', exact: true }).evaluate((b) => {
      b.click()
      b.click()
    })
    if (escenario === 'SIN_RESPUESTA') {
      await page.getByRole('heading', { name: 'Venta en proceso' }).waitFor()
      await page.clock.fastForward(10001)
      await page.getByRole('heading', { name: 'Seguimiento de operaciones' }).waitFor()
      assert.equal(await page.locator('.lineas .linea').count(), 0)
      resolver = true
      await page.getByRole('button', { name: 'Consultar de nuevo' }).click()
      await page.getByText('✓ Venta confirmada · Total cobrado: $123.45', { exact: true }).waitFor()
    } else if (escenario.startsWith('CANCELADA')) {
      await page.getByRole('heading', { name: 'Venta cancelada' }).waitFor()
      assert.equal(await page.getByLabel('Cantidad', { exact: true }).inputValue(), '3')
      assert.equal(await page.getByLabel('Cantidad', { exact: true }).isEnabled(), true)
      assert.equal(await page.locator('.rechazada').count(), escenario === 'CANCELADA' ? 1 : 0)
      await page.getByLabel('Cantidad', { exact: true }).fill('1')
    } else {
      await page.getByRole('heading', { name: 'Venta confirmada' }).waitFor()
      assert.equal(await page.locator('.definitivo').textContent(), '$123.45')
      assert.equal(await page.locator('.lineas .linea').count(), 0)
      await page.getByRole('button', { name: 'Nueva venta' }).click()
    }
    assert.equal(posts, 1)
    assert.deepEqual(errores, [])
    await page.setViewportSize({ width: 390, height: 844 })
    assert.equal(
      await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
      true,
    )
    console.log(`${escenario}: OK (navegador con API simulada, sin errores JS, sin desborde móvil)`)
    await page.close()
  }
} finally {
  await browser.close()
}

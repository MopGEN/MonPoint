// Requiere nginx y el conjunto real. No intercepta ni simula respuestas.
import assert from 'node:assert/strict'
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ?? 'playwright')
const origen = process.env.CLIENTE_ORIGEN ?? 'http://localhost:3000'
const api = process.env.GATEWAY_URL ?? 'http://localhost:8080'
const browser = await chromium.launch({
  executablePath: process.env.CHROMIUM_PATH ?? '/usr/bin/chromium',
  headless: true,
  args: ['--no-sandbox'],
})
const page = await browser.newPage()
page.setDefaultTimeout(20000)
const errores = [], peticiones = []
page.on('pageerror', e => errores.push(e.message))
page.on('request', r => peticiones.push(r.url()))
try {
  for (const ruta of ['/', '/login', '/caja']) {
    const r = await page.request.get(origen + ruta)
    assert.equal(r.status(), 200, ruta)
    assert.match(r.headers()['content-type'], /text\/html/)
    assert.match(r.headers()['cache-control'], /no-cache/)
    assert.match(await r.text(), /\/assets\/.*\.js/)
  }
  assert.equal((await page.request.get(origen + '/assets/no-existe.js')).status(), 404)
  const headers = {
    Origin: origen,
    'Access-Control-Request-Method': 'POST',
    'Access-Control-Request-Headers': 'content-type,authorization',
  }
  const preflight = await page.request.fetch(api + '/ventas', { method: 'OPTIONS', headers })
  assert.equal(preflight.status(), 200)
  assert.equal(preflight.headers()['access-control-allow-origin'], origen)
  const rechazado = await page.request.fetch(api + '/ventas', {
    method: 'OPTIONS', headers: { ...headers, Origin: 'http://origen-no-autorizado.invalid' },
  })
  assert.equal(rechazado.status(), 403)
  assert.equal(rechazado.headers()['access-control-allow-origin'], undefined)

  // Entrada directa en subruta: nginx sirve HTML y Vue pide autenticación.
  assert.equal((await page.goto(origen + '/caja')).status(), 200)
  await page.waitForURL(u => u.pathname === '/login' && u.searchParams.get('regresarA') === '/caja')
  await page.getByLabel('Correo', { exact: true }).fill(process.env.ADMIN_CORREO ?? 'admin@monpoint.com')
  await page.getByLabel('Contrasena', { exact: true }).fill(process.env.ADMIN_DEFAULT_PASSWORD ?? 'Admin123!')
  const login = page.waitForResponse(r => r.url() === api + '/auth/login')
  const productos = page.waitForResponse(r => r.url() === api + '/inventario/productos')
  const clientes = page.waitForResponse(r => r.url() === api + '/clientes')
  await page.getByRole('button', { name: 'Entrar', exact: true }).click()
  for (const pendiente of [login, productos, clientes]) {
    const r = await pendiente
    assert.equal(r.status(), 200, r.url())
    assert.equal((await r.allHeaders())['access-control-allow-origin'], origen)
  }
  await page.getByRole('heading', { name: 'Caja', exact: true }).waitFor()
  // La sesión vive en memoria: recargar es un 200 de nginx y vuelta al login.
  assert.equal((await page.reload()).status(), 200)
  await page.getByRole('button', { name: 'Entrar', exact: true }).waitFor()
  assert.match(page.url(), /\/login/)
  assert.ok(peticiones.some(u => u.startsWith(origen + '/assets/') && u.endsWith('.js')))
  assert.ok(!peticiones.some(u => u.startsWith(origen + '/@vite/') || u.startsWith(origen + '/src/')))
  assert.deepEqual(errores, [])
  console.log(JSON.stringify({
    resultado: 'PASS', origen, api, rutas: ['/', '/login', '/caja'],
    loginReal: true, catalogoReal: true, clientesReales: true,
    recargaSubruta: true, corsPermitido: true, corsAjenoRechazado: true,
    assetsCompilados: true, assetInexistente: 404, errores,
  }, null, 2))
} finally {
  await browser.close()
}

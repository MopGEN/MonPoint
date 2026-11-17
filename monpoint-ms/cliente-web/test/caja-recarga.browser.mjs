/** Regresiones con API simulada: historial del navegador y lecturas posteriores
 * al cobro. Se retienen respuestas para provocar las carreras sin esperas fijas.
 * Ejecutar contra npm run preview, con las mismas variables que concha.browser.mjs.
 */
import assert from 'node:assert/strict'
import { mkdir, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { NavigationFailureType } from 'vue-router'
import { salirDesdeMenu } from './concha-fixture.mjs'
const modulo = await import(process.env.PLAYWRIGHT_MODULE ?? 'playwright')
const { chromium } = modulo.default ?? modulo
const BASE = process.env.BASE_URL ?? 'http://localhost:4173'
const API = process.env.GATEWAY_URL ?? 'http://localhost:8080'
const SALIDA = resolve(process.env.SALIDA ?? '/tmp/caja-recarga')
await mkdir(SALIDA, { recursive: true })
const navegador = await chromium.launch({
  ...(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {}),
  args: process.env.CHROMIUM_NO_SANDBOX === '1' ? ['--no-sandbox'] : [],
})
const producto = (existencia) => [{ id: 'p1', nombre: 'Sello', descripcion: '', precio: 120, existencia, umbralStock: 5, activo: true }]
const resultado = { inicio: new Date().toISOString(), casos: [] }
function diferida () {
  let resolver
  const promesa = new Promise((r) => { resolver = r })
  return { promesa, resolver }
}
async function esperar (promesa, descripcion) {
  let limite
  try {
    return await Promise.race([promesa, new Promise((_, rechazar) => {
      limite = setTimeout(() => rechazar(new Error(descripcion)), 5000)
    })])
  } finally { clearTimeout(limite) }
}
async function escenario (nombre, comprobar) {
  const contexto = await navegador.newContext()
  const pagina = await contexto.newPage()
  pagina.setDefaultTimeout(5000)
  const errores = [], inesperadas = [], respuestasPendientes = []
  const estado = { rol: 'ADMIN', productos: 0, clientes: 0, directorio: [], errorClientes: false, reportes: 0, cobros: [], desenlace: 'CONFIRMADA', lecturas: [] }
  pagina.on('pageerror', (e) => errores.push(e.message))
  await pagina.route(`${API}/**`, async (ruta) => {
    const req = ruta.request(), url = new URL(req.url()), path = url.pathname
    let datos, status = 200
    if (path === '/auth/login') datos = { token: 'simulado', nombre: 'Persona', correo: 'persona@example.test', rol: estado.rol, expiraEn: 3600000 }
    else if (path === '/reportes/configuracion') datos = { nombreEmpresa: 'Empresa de prueba' }
    else if (path === '/notificaciones') datos = []
    else if (path === '/clientes') {
      estado.clientes++
      datos = estado.errorClientes ? { detail: 'Directorio temporalmente no disponible' } : estado.directorio
      if (estado.errorClientes) status = 503
    }
    else if (path === '/inventario/productos') {
      estado.productos++
      const respuesta = estado.lecturas.shift()
      respuesta?.iniciada.resolver()
      if (respuesta) await respuesta.liberada.promesa
      datos = respuesta?.datos ?? producto(8)
      status = respuesta?.status ?? 200
    } else if (path === '/reportes/ventas/agregado') {
      estado.reportes++
      datos = { periodo: 'DIA', desde: url.searchParams.get('desde'), hasta: url.searchParams.get('hasta'), zonaHoraria: 'America/Mexico_City', total: 0, puntos: [] }
    } else if (path === '/ventas' && req.method() === 'POST') {
      estado.cobros.push(req.postDataJSON()); status = 202
      datos = { id: 'v1', folio: 7, estado: 'PENDIENTE' }
    } else if (path === '/ventas/v1') datos = {
      id: 'v1', folio: 7, estado: estado.desenlace, total: 120,
      motivo: 'Existencia insuficiente', productoIdRechazado: 'p1',
      lineas: [{ productoId: 'p1', nombreProducto: 'Sello', cantidad: 1, subtotal: 120 }],
    }
    else { inesperadas.push(`${req.method()} ${path}`); return ruta.abort() }
    await ruta.fulfill({ status, contentType: 'application/json', body: JSON.stringify(datos) })
  })
  const buscador = pagina.getByLabel('Buscar producto', { exact: true })
  const tarjeta = pagina.getByRole('button', { name: /^Agregar Sello,/ })
  const aviso = pagina.getByRole('status').filter({ hasText: 'No se pudo actualizar el catálogo.' })
  function retener (datos, status = 200) {
    const respuesta = { datos, status, iniciada: diferida(), liberada: diferida() }
    estado.lecturas.push(respuesta)
    respuestasPendientes.push(respuesta)
    return { empezar: () => esperar(respuesta.iniciada.promesa, 'No se solicito la recarga del catalogo'), liberar: () => respuesta.liberada.resolver() }
  }
  async function entrar () {
    await pagina.getByLabel('Correo', { exact: true }).fill('persona@example.test')
    await pagina.getByLabel('Contraseña', { exact: true }).fill('simulada')
    await pagina.getByRole('button', { name: 'Entrar', exact: true }).click()
    await pagina.waitForURL('**/caja')
  }
  async function cobrar () {
    await tarjeta.click()
    // Mantener el foco en el buscador cubre tambien la regresion del doble Enter.
    await buscador.focus()
    await buscador.press('Control+Enter')
    await pagina.getByRole('heading', { name: estado.desenlace === 'CONFIRMADA' ? 'Venta confirmada' : 'Venta cancelada', exact: true }).waitFor()
    assert.deepEqual(estado.cobros, [{ clienteId: null, lineas: [{ productoId: 'p1', cantidad: 1 }] }])
  }
  async function existencia (n) {
    await tarjeta.filter({ hasText: `Existencia ${n}` }).waitFor()
    await pagina.getByRole('button', { name: 'Actualizar catálogo', exact: true }).waitFor()
  }
  try {
    await pagina.goto(`${BASE}/login`)
    await comprobar({ pagina, estado, entrar, tarjeta, buscador, aviso, retener, cobrar, existencia })
    assert.deepEqual(errores, [])
    assert.deepEqual(inesperadas, [])
    resultado.casos.push({ nombre, resultado: 'PASS', productos: estado.productos, clientes: estado.clientes })
  } catch (e) {
    process.exitCode = 1
    resultado.casos.push({ nombre, resultado: 'FAIL', fallo: e.message })
    await pagina.screenshot({ path: `${SALIDA}/${resultado.casos.length}-fallo.png`, fullPage: true }).catch(() => {})
  } finally {
    for (const r of respuestasPendientes) r.liberada.resolver()
    await contexto.close()
  }
}
try {
  await escenario('ADMIN Reportes, salir, VENDEDOR y Atras conserva titulo y ruta de Caja', async ({ pagina, estado, entrar }) => {
    await entrar()
    await pagina.getByRole('link', { name: 'Reportes', exact: true }).click()
    await pagina.getByRole('heading', { level: 1, name: 'Reportes', exact: true }).waitFor()
    assert.equal(await pagina.title(), 'MonPoint · Reportes')
    await pagina.getByRole('link', { name: 'Caja', exact: true }).click()
    await pagina.getByRole('heading', { level: 1, name: 'Caja', exact: true }).waitFor()
    await salirDesdeMenu(pagina)
    await pagina.waitForURL('**/login')
    assert.equal(await pagina.title(), 'MonPoint · Iniciar sesion')
    estado.rol = 'VENDEDOR'
    await entrar()
    const reportesAntes = estado.reportes
    await pagina.goBack()
    await pagina.waitForFunction(() => location.pathname === '/caja')
    await pagina.getByRole('heading', { level: 1, name: 'Caja', exact: true }).waitFor()
    assert.equal(await pagina.title(), 'MonPoint · Caja')
    assert.equal(estado.reportes, reportesAntes, 'el historial no consulta Reportes como vendedor')
  })
  await escenario('Una navegacion cancelada a Reportes no sobrescribe el titulo de Caja', async ({ pagina, entrar }) => {
    await entrar()
    const navegacion = await pagina.evaluate(async () => {
      const router = document.querySelector('#app').__vue_app__.config.globalProperties.$router
      let liberar, iniciar
      const iniciada = new Promise((r) => { iniciar = r })
      // Retener una navegacion real hace determinista su cancelacion por la
      // siguiente, sin depender del tiempo que tarde en cargar el chunk.
      const retirar = router.beforeResolve((destino) => {
        if (destino.name === 'reportes') {
          iniciar()
          return new Promise((r) => { liberar = r })
        }
      })
      try {
        const reportes = router.push('/reportes')
        await iniciada
        await router.push('/caja')
        liberar()
        const fallo = await reportes
        return { tipo: fallo?.type, ruta: router.currentRoute.value.path, titulo: document.title }
      } finally { retirar() }
    })
    assert.equal(navegacion.tipo, NavigationFailureType.cancelled, 'Vue Router debe informar una navegacion cancelada')
    assert.equal(navegacion.ruta, '/caja')
    assert.equal(navegacion.titulo, 'MonPoint · Caja')
    await pagina.getByRole('heading', { level: 1, name: 'Caja', exact: true }).waitFor()
  })
  await escenario('Confirmacion recarga sin ocultar rejilla ni consultar clientes', async ({ pagina, estado, entrar, tarjeta, retener, cobrar, existencia }) => {
    await entrar(); await existencia(8)
    const clientesAntes = estado.clientes
    await pagina.evaluate(() => {
      window.cambiosCatalogo = []
      new MutationObserver(() => {
        const catalogo = document.querySelector('.catalogo')
        if (!catalogo.querySelector('.rejilla') || catalogo.textContent.includes('Cargando productos…')) window.cambiosCatalogo.push(catalogo.textContent)
      }).observe(document.querySelector('.catalogo'), { subtree: true, childList: true, characterData: true })
    })
    const recarga = retener(producto(7))
    await cobrar(); await recarga.empezar()
    assert.match(await tarjeta.innerText(), /Existencia 8/)
    assert.equal(await pagina.getByText('Cargando productos…', { exact: true }).count(), 0)
    recarga.liberar(); await existencia(7)
    assert.equal(estado.productos, 2)
    assert.equal(estado.clientes, clientesAntes)
    assert.deepEqual(await pagina.evaluate(() => window.cambiosCatalogo), [])
    assert.match(await pagina.locator('.resultado.confirmada').innerText(), /\$120\.00/)
    await pagina.getByRole('button', { name: 'Nueva venta', exact: true }).click()
    assert.match(await tarjeta.innerText(), /Existencia 7/)
  })
  await escenario('Cancelacion tambien refresca y conserva las lineas rechazadas', async ({ pagina, estado, entrar, retener, cobrar, existencia }) => {
    await entrar(); await existencia(8)
    estado.desenlace = 'CANCELADA'
    const recarga = retener(producto(0))
    await cobrar(); await recarga.empezar(); recarga.liberar()
    await pagina.getByRole('button', { name: /^Agregar Sello,.*Sin existencia/ }).waitFor()
    assert.equal(estado.productos, 2)
    assert.equal(estado.clientes, 1)
    assert.equal(await pagina.getByLabel('Cantidad', { exact: true }).inputValue(), '1')
    assert.equal(await pagina.locator('.lineas .rechazada').count(), 1)
    await pagina.getByRole('heading', { name: 'Venta cancelada', exact: true }).waitFor()
  })
  await escenario('Una recarga manual pendiente no descarta la posterior al cobro', async ({ pagina, estado, entrar, retener, cobrar, existencia }) => {
    await entrar(); await existencia(8)
    const manual = retener(producto(8))
    await pagina.getByRole('button', { name: 'Actualizar catálogo', exact: true }).click()
    await manual.empezar()
    const automatica = retener(producto(7))
    await cobrar(); await automatica.empezar()
    automatica.liberar(); await existencia(7)
    manual.liberar()
    assert.equal(estado.productos, 3)
    assert.equal(estado.clientes, 2, 'solo la recarga manual consulta clientes')
    assert.match(await pagina.locator('.existencia').innerText(), /Existencia 7/)
    assert.equal(await pagina.getByRole('alert').count(), 0)
  })
  await escenario('Error o respuesta invalida conserva catalogo y venta; reintento recupera', async ({ pagina, estado, entrar, tarjeta, buscador, aviso, retener, cobrar, existencia }) => {
    await entrar(); await existencia(8)
    const recarga = retener({ detail: 'Inventario temporalmente no disponible' }, 503)
    await cobrar(); await recarga.empezar(); recarga.liberar()
    await aviso.waitFor()
    assert.match(await tarjeta.innerText(), /Existencia 8/)
    assert.match(await pagina.locator('.resultado.confirmada').innerText(), /\$120\.00/)
    assert.equal(await pagina.getByRole('alert').count(), 0)
    assert.equal(estado.clientes, 1)
    await pagina.getByRole('button', { name: 'Nueva venta', exact: true }).click()
    await buscador.press('Enter')
    assert.equal(await pagina.getByLabel('Cantidad', { exact: true }).inputValue(), '1')
    const invalida = retener({ contenido: 'no es una lista' })
    await pagina.getByRole('button', { name: 'Actualizar catálogo', exact: true }).click()
    await invalida.empezar(); invalida.liberar()
    await aviso.waitFor()
    assert.match(await tarjeta.innerText(), /Existencia 8/)
    const recuperada = retener(producto(7))
    await pagina.getByRole('button', { name: 'Actualizar catálogo', exact: true }).click()
    await recuperada.empezar(); recuperada.liberar(); await existencia(7)
    assert.equal(await aviso.count(), 0)
    assert.equal(await pagina.getByLabel('Cantidad', { exact: true }).inputValue(), '1', 'recargar no modifica el carrito')
  })
  await escenario('Fallo inicial, lista vacia valida y desmontaje durante una recarga', async ({ pagina, entrar, retener, aviso, buscador }) => {
    const inicial = retener({ detail: 'Sin conexion con inventario' }, 503)
    await entrar(); await inicial.empezar()
    await pagina.getByText('Cargando productos…', { exact: true }).waitFor()
    await buscador.press('Enter')
    assert.equal(await pagina.locator('.lineas .linea').count(), 0, 'Enter no agrega durante una primera carga sin datos')
    inicial.liberar()
    await pagina.getByRole('alert').filter({ hasText: 'Sin conexion con inventario' }).waitFor()
    assert.equal(await pagina.locator('.rejilla').count(), 0)
    await buscador.press('Enter')
    assert.equal(await pagina.locator('.lineas .linea').count(), 0, 'Enter no agrega tras fallar la primera carga')
    const vacia = retener([])
    await pagina.getByRole('button', { name: 'Actualizar catálogo', exact: true }).click()
    await vacia.empezar(); vacia.liberar()
    await pagina.getByText('No hay productos activos en el catálogo.', { exact: true }).waitFor()
    await buscador.fill('inexistente')
    await pagina.getByText('No hay productos para esta búsqueda.', { exact: true }).waitFor()
    await buscador.fill('')
    const fallo = retener({ detail: 'Error temporal' }, 503)
    await pagina.getByRole('button', { name: 'Actualizar catálogo', exact: true }).click()
    await fallo.empezar(); fallo.liberar(); await aviso.waitFor()
    assert.equal(await pagina.getByRole('alert').count(), 0, 'una lista vacia previa tambien es una lectura valida')
    const tardia = retener(producto(99))
    await pagina.getByRole('button', { name: 'Actualizar catálogo', exact: true }).click()
    await tardia.empezar()
    await pagina.getByRole('link', { name: 'Clientes', exact: true }).click()
    await pagina.getByRole('heading', { level: 1, name: 'Clientes', exact: true }).waitFor()
    tardia.liberar()
    await pagina.getByRole('link', { name: 'Caja', exact: true }).click()
    await pagina.getByRole('button', { name: /^Agregar Sello,.*Existencia 8/ }).waitFor()
    assert.equal(await pagina.getByRole('alert').count(), 0)
  })
  for (const directorio of [[], [{ id: 'c1', nombre: 'Ana Ruiz' }]]) {
    await escenario(`Error al actualizar clientes conserva la lectura ${directorio.length ? 'con opciones' : 'vacia'} y permite reintentar`, async ({ pagina, estado, entrar, existencia }) => {
      estado.directorio = directorio
      await entrar(); await existencia(8)
      const selector = pagina.getByLabel('Cliente (opcional)', { exact: true })
      assert.equal(await selector.locator('option').count(), directorio.length + 1)
      if (directorio.length) await selector.selectOption('c1')
      estado.errorClientes = true
      await pagina.getByRole('button', { name: 'Actualizar catálogo', exact: true }).click()
      const aviso = pagina.getByRole('status').filter({ hasText: 'No se pudo actualizar la lista de clientes.' })
      await aviso.waitFor()
      assert.equal(await pagina.getByRole('alert').count(), 0)
      assert.equal(await selector.locator('option').count(), directorio.length + 1)
      assert.equal(await selector.inputValue(), directorio.length ? 'c1' : '')
      assert.equal(await pagina.getByText(/Puede registrar una venta sin cliente/).count(), 0)
      estado.errorClientes = false
      estado.directorio = [...directorio, { id: 'c2', nombre: 'Luis Pérez' }]
      await pagina.getByRole('button', { name: 'Actualizar catálogo', exact: true }).click()
      await selector.getByRole('option', { name: 'Luis Pérez', exact: true }).waitFor({ state: 'attached' })
      assert.equal(await aviso.count(), 0)
      assert.equal(await selector.inputValue(), directorio.length ? 'c1' : '')
    })
  }
  await escenario('Sin lectura previa de clientes se muestra un error y el reintento los carga', async ({ pagina, estado, entrar, existencia }) => {
    estado.errorClientes = true
    await entrar(); await existencia(8)
    await pagina.getByRole('alert').filter({ hasText: 'Puede registrar una venta sin cliente.' }).waitFor()
    assert.equal(await pagina.getByRole('status').filter({ hasText: 'Se conserva la última lectura.' }).count(), 0)
    const selector = pagina.getByLabel('Cliente (opcional)', { exact: true })
    assert.equal(await selector.locator('option').count(), 1)
    estado.errorClientes = false
    estado.directorio = [{ id: 'c1', nombre: 'Ana Ruiz' }]
    await pagina.getByRole('button', { name: 'Actualizar catálogo', exact: true }).click()
    await selector.getByRole('option', { name: 'Ana Ruiz', exact: true }).waitFor({ state: 'attached' })
    assert.equal(await pagina.getByRole('alert').count(), 0)
  })
} finally {
  resultado.fin = new Date().toISOString()
  await writeFile(`${SALIDA}/resultado.json`, JSON.stringify(resultado, null, 2))
  await navegador.close()
}
console.log(JSON.stringify(resultado, null, 2))

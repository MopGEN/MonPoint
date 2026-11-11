/**
 * Recorrido de la pantalla de usuarios con el Gateway simulado.
 *
 * El doble reproduce las tres formas en que ms-auth informa de un fallo: el
 * mapa `campos` de Bean Validation, la lista `errores` de la politica de
 * contrasena y el 409 sin campos del correo repetido.
 *
 * Uso: npm run build && npm run preview, y en otra consola
 *      node test/usuarios.browser.mjs
 */
import assert from 'node:assert/strict'
const modulo = await import(process.env.PLAYWRIGHT_MODULE ?? 'playwright')
const { chromium } = modulo.default ?? modulo

const BASE = process.env.BASE_URL ?? 'http://localhost:4173'
const API = process.env.GATEWAY_URL ?? 'http://localhost:8080'
const SALIDA = process.env.SALIDA ?? '/tmp/usuarios'

const YO = 'admin@monpoint.com'
let rol = 'ADMIN'
let usuarios = [
  { id: 'u1', nombre: 'Administrador', correo: YO, rol: 'ADMIN', activo: true },
  { id: 'u2', nombre: 'Victor Salas', correo: 'victor@monpoint.test', rol: 'ADMIN', activo: true },
  { id: 'u3', nombre: 'Hugo Peña', correo: 'hugo@monpoint.test', rol: 'VENDEDOR', activo: true },
]
let fallo = null // 'politica' | 'duplicado' | null
const peticiones = []

const navegador = await chromium.launch()
const pagina = await (await navegador.newContext({ viewport: { width: 1280, height: 900 } })).newPage()

await pagina.route(`${API}/auth/login`, (r) =>
  r.fulfill({
    status: 200, contentType: 'application/json',
    body: JSON.stringify({
      token: 't', tipo: 'Bearer',
      correo: rol === 'ADMIN' ? YO : 'hugo@monpoint.test',
      nombre: rol === 'ADMIN' ? 'Administrador' : 'Hugo Peña',
      rol, expiraEn: 3600000,
    }),
  }),
)

const problema = (status, extra) => ({
  status, contentType: 'application/problem+json',
  body: JSON.stringify({ title: 'Error', detail: 'Uno o más campos no cumplen las validaciones', ...extra }),
})

await pagina.route(`${API}/auth/register`, (ruta) => {
  const cuerpo = ruta.request().postDataJSON()
  peticiones.push({ tipo: 'alta', cuerpo })
  if (fallo === 'politica') {
    return ruta.fulfill(problema(400, {
      detail: 'La contraseña no cumple la política',
      errores: [
        'La contraseña debe tener al menos 8 caracteres',
        'La contraseña debe contener al menos un carácter especial (@$!%*?&)',
      ],
    }))
  }
  if (fallo === 'duplicado') {
    return ruta.fulfill(problema(409, { detail: `Ya existe un usuario con el correo ${cuerpo.correo}` }))
  }
  usuarios.push({ id: `u${usuarios.length + 1}`, activo: true, ...cuerpo })
  return ruta.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify(cuerpo) })
})

await pagina.route(`${API}/auth/usuarios**`, (ruta) => {
  const p = ruta.request()
  const metodo = p.method()
  if (metodo === 'GET') {
    // El servicio lista SOLO activos.
    return ruta.fulfill({
      status: 200, contentType: 'application/json',
      body: JSON.stringify(usuarios.filter((u) => u.activo)),
    })
  }
  const id = new URL(p.url()).pathname.split('/').filter(Boolean).at(metodo === 'PATCH' ? -2 : -1)
  if (metodo === 'PUT') {
    const cuerpo = p.postDataJSON()
    peticiones.push({ tipo: 'edicion', id, cuerpo })
    const u = usuarios.find((x) => x.id === id)
    Object.assign(u, { nombre: cuerpo.nombre, correo: cuerpo.correo })
    if (cuerpo.rol) u.rol = cuerpo.rol
    return ruta.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(u) })
  }
  if (metodo === 'PATCH') {
    peticiones.push({ tipo: 'baja', id })
    usuarios.find((x) => x.id === id).activo = false
    return ruta.fulfill({ status: 204, body: '' })
  }
  return ruta.fulfill({ status: 405, body: '' })
})

for (const x of ['inventario/productos', 'clientes', 'notificaciones']) {
  await pagina.route(`${API}/${x}**`, (r) =>
    r.fulfill({ status: 200, contentType: 'application/json', body: '[]' }))
}

async function entrar (seccion = 'Usuarios') {
  await pagina.goto(`${BASE}/login`)
  await pagina.fill('#correo', 'x@y.test')
  await pagina.fill('#contrasena', 'x')
  await pagina.click('button[type="submit"]')
  await pagina.waitForURL('**/caja')
  if (seccion) await pagina.click(`nav[aria-label="Secciones"] a:has-text("${seccion}")`)
}

await entrar()
await pagina.waitForSelector('table.mp-tabla')

assert.equal(await pagina.locator('table.mp-tabla tbody tr').count(), 3)
// La cuenta propia se marca y no ofrece desactivacion, porque el servicio la
// rechazaria.
const propia = pagina.locator('table.mp-tabla tbody tr', { hasText: 'Administrador' }).first()
assert.ok(await propia.locator('text=usted').count())
assert.equal(await propia.locator('button:has-text("Desactivar")').count(), 0)
assert.ok(await propia.locator('text=Su propia cuenta').count())
// Se cuenta dentro de la tabla: el dialogo de confirmacion tiene su propio
// boton "Desactivar" y, cerrado, sigue en el DOM. Aqui eso es correcto —la
// pantalla entera es de ADMIN— asi que lo que debe acotarse es la busqueda.
assert.equal(
  await pagina.locator('table.mp-tabla button:has-text("Desactivar")').count(),
  2,
  'las otras dos filas si lo ofrecen',
)
await pagina.screenshot({ path: `${SALIDA}-01-listado.png` })

// Validacion del cliente: no se gasta peticion.
await pagina.click('button:has-text("Nuevo usuario")')
await pagina.waitForSelector('dialog[open]')
await pagina.click('button[form="forma-usuario"]')
await pagina.waitForSelector('#nombre-error')
assert.equal(peticiones.length, 0)

// La politica de contrasena llega como lista y se junta bajo su campo.
fallo = 'politica'
await pagina.fill('#nombre', 'Mario Lopez')
await pagina.fill('#correo', 'mario@monpoint.test')
await pagina.fill('#password', 'corta')
await pagina.click('button[form="forma-usuario"]')
await pagina.waitForSelector('#password-error')
const reglas = await pagina.locator('#password-error').innerText()
assert.ok(reglas.includes('8 caracteres') && reglas.includes('@$!%*?&'), reglas)
await pagina.screenshot({ path: `${SALIDA}-02-politica.png` })

// El 409 del correo repetido se atribuye al correo, no al aviso general.
fallo = 'duplicado'
await pagina.fill('#password', 'Abc123!x')
await pagina.click('button[form="forma-usuario"]')
await pagina.waitForSelector('#correo-error')
assert.ok((await pagina.locator('#correo-error').innerText()).includes('Ya existe'))
assert.equal(await pagina.locator('dialog[open] .mp-aviso--error').count(), 0, 'sin aviso duplicado')

// Alta correcta.
fallo = null
await pagina.selectOption('#rol', 'VENDEDOR')
await pagina.click('button[form="forma-usuario"]')
await pagina.waitForSelector('dialog[open]', { state: 'detached' })
await pagina.waitForSelector('table.mp-tabla >> text=Mario Lopez')
assert.equal(peticiones.at(-1).cuerpo.rol, 'VENDEDOR')

// Editarse a uno mismo: el selector de rol queda bloqueado.
await pagina.click('table.mp-tabla tbody tr:has-text("usted") button:has-text("Editar")')
await pagina.waitForSelector('dialog[open]')
assert.equal(await pagina.inputValue('#password'), '', 'la contrasena nunca se precarga')
assert.equal(await pagina.isDisabled('#rol'), true)
assert.ok(await pagina.locator('text=Nadie puede cambiar su propio rol').count())
await pagina.screenshot({ path: `${SALIDA}-03-propio-perfil.png` })

// Sin contrasena nueva, viaja null y el rol no cambia.
await pagina.fill('#nombre', 'Administrador General')
await pagina.click('button[form="forma-usuario"]')
await pagina.waitForSelector('dialog[open]', { state: 'detached' })
const edicion = peticiones.at(-1)
assert.equal(edicion.cuerpo.password, null)
assert.equal(edicion.cuerpo.rol, null)

// Desactivar: el dialogo advierte que no se deshace y cuenta los ADMIN que
// quedarian. La fila desaparece porque el listado solo trae activos.
await pagina.click('table.mp-tabla tbody tr:has-text("Victor Salas") button:has-text("Desactivar")')
await pagina.waitForSelector('dialog[open]')
const texto = await pagina.locator('dialog[open]').innerText()
assert.ok(texto.includes('No se puede deshacer'), texto)
assert.ok(texto.includes('Quedaría') || texto.includes('Quedarían'), texto)
await pagina.screenshot({ path: `${SALIDA}-04-desactivar.png` })
await pagina.click('dialog[open] button.peligro')
await pagina.waitForSelector('table.mp-tabla >> text=Victor Salas', { state: 'detached' })
assert.equal(peticiones.at(-1).tipo, 'baja')

// VENDEDOR: la seccion no se ofrece.
rol = 'VENDEDOR'
await pagina.click('button:has-text("Salir")')
await pagina.waitForURL('**/login')
await entrar(null)
const secciones = await pagina.locator('nav[aria-label="Secciones"] a').allInnerTexts()
assert.ok(!secciones.some((s) => s.includes('Usuarios')))
await pagina.screenshot({ path: `${SALIDA}-05-vendedor.png` })

await navegador.close()
console.log('Recorrido completado. Capturas en', `${SALIDA}-*.png`)

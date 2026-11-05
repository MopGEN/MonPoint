#!/usr/bin/env node
import { pathToFileURL } from 'node:url'

// Las identidades son estables entre ejecuciones. No usar UUID ni fecha en los nombres.
export const vendedor = { nombre: 'Semilla 3.1 - Vendedor', correo: 'vendedor.semilla@monpoint.test', rol: 'VENDEDOR' }
export const clientes = [
  { nombre: 'Semilla 3.1 - Ana', correo: 'ana.semilla@example.test', telefono: '5550100101', direccion: 'Domicilio de prueba 1' },
  { nombre: 'Semilla 3.1 - Luis', correo: 'luis.semilla@example.test', telefono: '5550100102', direccion: 'Domicilio de prueba 2' },
]
export const productos = [
  { nombre: 'Semilla 3.1 - Tinta', descripcion: 'Producto de prueba', precio: 41.15, existencia: 10, umbralStock: 5 },
  { nombre: 'Semilla 3.1 - Papel', descripcion: 'Producto de prueba', precio: 75.50, existencia: 10, umbralStock: 5 },
  { nombre: 'Semilla 3.1 - Sello', descripcion: 'Producto de prueba', precio: 120, existencia: 10, umbralStock: 5 },
]

function exigir(condicion, mensaje) {
  if (!condicion) throw new Error(mensaje)
}

// Comparar el valor decimal, no su representación JSON. No redondear ni pasar
// cadenas por Number: perdería diferencias como "41.150000000000000001".
function decimalCanonico(valor) {
  if (typeof valor !== 'string' && typeof valor !== 'number') return null
  const m = /^(\d+)(?:\.(\d+))?(?:[eE]([+-]?\d+))?$/.exec(String(valor))
  if (!m) return null
  const fraccion = m[2] ?? ''
  const cifras = (m[1] + fraccion).replace(/^0+/, '')
  if (!cifras) return '0'
  const coeficiente = cifras.replace(/0+$/, '')
  const exponente = BigInt(m[3] ?? '0') - BigInt(fraccion.length) + BigInt(cifras.length - coeficiente.length)
  return `${coeficiente}e${exponente}`
}

function coincidencia(lista, clave, esperado, campos) {
  exigir(Array.isArray(lista), 'La API no devolvió un listado válido.')
  const encontrados = lista.filter(item => item[clave] === esperado[clave])
  exigir(encontrados.length <= 1, `Identidad ambigua: ${esperado[clave]}. Corregir los duplicados antes de continuar.`)
  const actual = encontrados[0]
  if (actual) {
    exigir(typeof actual.id === 'string' && actual.id.length > 0, `Falta id de ${esperado[clave]}.`)
    for (const campo of campos) {
      const coincide = campo === 'precio'
        ? decimalCanonico(actual[campo]) !== null && decimalCanonico(actual[campo]) === decimalCanonico(esperado[campo])
        : actual[campo] === esperado[campo]
      exigir(coincide, `Conflicto en ${esperado[clave]}: ${campo} difiere. No se sobrescribió.`)
    }
  }
  return actual
}

function validarPassword(valor) {
  exigir(valor.length >= 8 && Buffer.byteLength(valor, 'utf8') <= 72
    && /\p{Lu}/u.test(valor) && /\p{Ll}/u.test(valor) && /\p{Nd}/u.test(valor)
    && /[@$!%*?&]/.test(valor), 'SEMILLA_VENDEDOR_PASSWORD no cumple la política de contraseñas de ms-auth.')
}

export async function sembrar({ env = process.env, fetchImpl = fetch, timeoutMs = 10000 } = {}) {
  const resumen = { fase: 1, estado: 'EN_PROCESO', usuarios: [], clientes: [], productos: [], ventasCreadas: 0 }
  let operacion = 'configuración'
  try {
    const gateway = new URL(env.SEMILLA_GATEWAY_URL ?? 'http://localhost:8080')
    exigir(['http:', 'https:'].includes(gateway.protocol) && !gateway.username && !gateway.password
      && gateway.pathname === '/' && !gateway.search && !gateway.hash, 'SEMILLA_GATEWAY_URL debe ser un origen HTTP(S) sin credenciales ni ruta.')
    const adminPassword = env.ADMIN_DEFAULT_PASSWORD || 'Admin123!'
    const vendedorPassword = env.SEMILLA_VENDEDOR_PASSWORD ?? 'Vendedor123!'
    validarPassword(vendedorPassword)
    let token

    // Sin reintentos automáticos: un POST sin respuesta pudo haberse confirmado.
    async function api(metodo, ruta, cuerpo, esperado, autorizacion = token) {
      operacion = `${metodo} ${ruta}`
      const mutacion = metodo === 'POST' && ruta !== '/auth/login'
      const incertidumbre = mutacion ? ' El resultado del alta es incierto; no se reintentó. Verificar el listado antes de volver a ejecutar.' : ''
      let respuesta
      let datos
      try {
        respuesta = await fetchImpl(new URL(ruta, gateway), {
          method: metodo,
          headers: {
            Accept: 'application/json',
            ...(cuerpo ? { 'Content-Type': 'application/json' } : {}),
            ...(autorizacion ? { Authorization: `Bearer ${autorizacion}` } : {}),
          },
          ...(cuerpo ? { body: JSON.stringify(cuerpo) } : {}),
          signal: AbortSignal.timeout(timeoutMs),
          redirect: 'error',
        })
        if (respuesta.status !== esperado) {
          // No volcar respuestas: pueden contener información sensible o valores enviados.
          await respuesta.body?.cancel()
          const autenticacion = ruta === '/auth/login' || [401, 403].includes(respuesta.status)
          throw new Error(`${operacion}: HTTP ${respuesta.status}.${autenticacion ? ' Autenticación o permisos inválidos; revisar la contraseña de la cuenta existente y su rol.' : ''}${mutacion && respuesta.status >= 500 ? incertidumbre : ''}`)
        }
        datos = await respuesta.json()
      } catch (error) {
        if (error.message.startsWith(`${operacion}: HTTP `)) throw error
        throw new Error(`${operacion}: no se recibió una respuesta JSON completa a tiempo o falló la conexión.${incertidumbre}`)
      }
      return datos
    }

    async function login(correo, password, rol) {
      const acceso = await api('POST', '/auth/login', { correo, password }, 200, null)
      exigir(acceso.rol === rol && typeof acceso.token === 'string' && acceso.token.length > 0,
        `Autenticación inválida: se requiere una cuenta ${rol}.`)
      return acceso.token
    }

    token = await login('admin@monpoint.com', adminPassword, 'ADMIN')
    const admin = await api('GET', '/auth/me', null, 200)
    exigir(admin.id && admin.activo === true && admin.rol === 'ADMIN' && admin.correo === 'admin@monpoint.com',
      'El administrador inicial no está activo o su identidad no coincide.')
    resumen.usuarios.push({ id: admin.id, correo: admin.correo, rol: admin.rol, accion: 'REUTILIZADO' })

    // Leer y comprobar TODO antes de la primera alta. No hay transacción distribuida.
    const listaUsuarios = await api('GET', '/auth/usuarios', null, 200)
    const listaClientes = await api('GET', '/clientes', null, 200)
    const listaProductos = await api('GET', '/inventario/productos', null, 200)
    const usuarioExistente = coincidencia(listaUsuarios, 'correo', { ...vendedor, activo: true }, ['nombre', 'rol', 'activo'])
    const clientesExistentes = clientes.map(c => coincidencia(listaClientes, 'nombre', c, ['correo', 'telefono', 'direccion']))
    const productosExistentes = productos.map(p => coincidencia(listaProductos, 'nombre', { ...p, activo: true },
      ['descripcion', 'precio', 'umbralStock', 'activo']))
    // Una clave de vendedor distinta tampoco debe dejar altas a medias.
    if (usuarioExistente) await login(vendedor.correo, vendedorPassword, 'VENDEDOR')

    const usuario = usuarioExistente ?? await api('POST', '/auth/register', { ...vendedor, password: vendedorPassword }, 201)
    exigir(usuario.id && usuario.rol === 'VENDEDOR' && usuario.activo === true, 'Alta de vendedor con respuesta incompleta; revisar /auth/usuarios.')
    resumen.usuarios.push({ id: usuario.id, correo: usuario.correo, rol: usuario.rol, accion: usuarioExistente ? 'REUTILIZADO' : 'CREADO' })
    if (!usuarioExistente) await login(vendedor.correo, vendedorPassword, 'VENDEDOR')

    for (const [i, cliente] of clientes.entries()) {
      const existente = clientesExistentes[i]
      const actual = existente ?? await api('POST', '/clientes', cliente, 201)
      exigir(actual.id, `Alta de ${cliente.nombre} sin id; revisar /clientes.`)
      resumen.clientes.push({ id: actual.id, nombre: actual.nombre, accion: existente ? 'REUTILIZADO' : 'CREADO' })
    }
    for (const [i, producto] of productos.entries()) {
      const existente = productosExistentes[i]
      const actual = existente ?? await api('POST', '/inventario/productos', producto, 201)
      exigir(actual.id, `Alta de ${producto.nombre} sin id; revisar /inventario/productos.`)
      resumen.productos.push({ id: actual.id, nombre: actual.nombre, precio: actual.precio,
        existencia: actual.existencia, umbralStock: actual.umbralStock, accion: existente ? 'REUTILIZADO' : 'CREADO' })
    }
    resumen.estado = 'COMPLETADA'
    return resumen
  } catch (error) {
    resumen.estado = 'INTERRUMPIDA'
    resumen.operacion = operacion
    error.resumen = resumen
    throw error
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    exigir(process.argv.length === 2, 'Este guion solo implementa la fase 1 y no acepta argumentos. Ver scripts/README.md.')
    console.log(JSON.stringify(await sembrar(), null, 2))
  } catch (error) {
    console.error(JSON.stringify({ error: error.message, ...(error.resumen ? { resumen: error.resumen } : {}) }, null, 2))
    process.exitCode = 1
  }
}

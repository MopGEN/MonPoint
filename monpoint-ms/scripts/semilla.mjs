#!/usr/bin/env node
import { pathToFileURL } from 'node:url'

import { vendedor, clientes, productos, exigir, coincidencia, origenGateway } from './semilla-comun.mjs'
import { sembrarVentas } from './semilla-ventas.mjs'
export { vendedor, clientes, productos, sembrarVentas }

function validarPassword(valor) {
  exigir(valor.length >= 8 && Buffer.byteLength(valor, 'utf8') <= 72
    && /\p{Lu}/u.test(valor) && /\p{Ll}/u.test(valor) && /\p{Nd}/u.test(valor)
    && /[@$!%*?&]/.test(valor), 'SEMILLA_VENDEDOR_PASSWORD no cumple la política de contraseñas de ms-auth.')
}

export async function sembrar({ env = process.env, fetchImpl = fetch, timeoutMs = 10000 } = {}) {
  const resumen = { fase: 1, estado: 'EN_PROCESO', usuarios: [], clientes: [], productos: [], ventasCreadas: 0 }
  let operacion = 'configuración'
  try {
    const gateway = origenGateway(env)
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
    const argumentos = process.argv.slice(2)
    exigir(argumentos.length === 0 || (argumentos.length === 1 && argumentos[0] === '--ventas'),
      'Uso: node scripts/semilla.mjs [--ventas]. Ver scripts/README.md.')
    console.log(JSON.stringify(await (argumentos.length ? sembrarVentas() : sembrar()), null, 2))
  } catch (error) {
    console.error(JSON.stringify({ error: error.message, ...(error.resumen ? { resumen: error.resumen } : {}) }, null, 2))
    process.exitCode = 1
  }
}

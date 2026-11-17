#!/usr/bin/env node
import { readFileSync } from 'node:fs'
import { pathToFileURL } from 'node:url'
import { exigir, coincidencia, origenGateway } from './semilla-comun.mjs'

export const cuentas = [
  { nombre: 'Víctor Ortiz', correo: 'victor.ortiz@autotachi.mx', rol: 'ADMIN', variable: 'AUTOTACHI_VICTOR_PASSWORD' },
  { nombre: 'Alejandro Bermúdez', correo: 'alejandro.bermudez@autotachi.mx', rol: 'ADMIN', variable: 'AUTOTACHI_ALEJANDRO_PASSWORD' },
  { nombre: 'Hugo Trejo', correo: 'hugo.trejo@autotachi.mx', rol: 'ADMIN', variable: 'AUTOTACHI_HUGO_PASSWORD' },
  { nombre: 'Vendedor', correo: 'vendedor@autotachi.mx', rol: 'VENDEDOR', variable: 'AUTOTACHI_VENDEDOR_PASSWORD' },
]
export const catalogo = JSON.parse(readFileSync(new URL('./catalogo-autotachi.json', import.meta.url), 'utf8'))
// Las referencias de origen no son un SKU: ProductoRequest no tiene ese campo.
export const productos = catalogo.filter(p => p.activo).map(p => ({
  nombre: p.nombre, descripcion: null, precio: p.precioCentavos / 100,
  existencia: p.existencia, umbralStock: p.umbralStock,
}))
const camposCatalogo = ['descripcion', 'precio', 'umbralStock', 'activo']
const EMPRESA_AUSENTE = Symbol('configuracion 404')

function empresaDelEntorno(env) {
  const habilitada = env.SEMILLA_EMPRESA_HABILITADA ?? 'false'
  exigir(['true', 'false'].includes(habilitada), 'SEMILLA_EMPRESA_HABILITADA debe ser true o false.')
  if (habilitada === 'false') return null
  for (const variable of ['AUTOTACHI_EMPRESA_NOMBRE', 'AUTOTACHI_EMPRESA_RFC']) {
    exigir(typeof env[variable] === 'string' && env[variable].trim().length > 0,
      `Configurar ${variable} antes de importar la empresa.`)
  }
  return {
    nombreEmpresa: env.AUTOTACHI_EMPRESA_NOMBRE.trim(),
    rfc: env.AUTOTACHI_EMPRESA_RFC.trim().toUpperCase(),
    direccion: env.AUTOTACHI_EMPRESA_DIRECCION?.trim() || null,
    telefono: env.AUTOTACHI_EMPRESA_TELEFONO?.trim() || null,
  }
}

function comprobarEmpresa(actual, esperada) {
  for (const campo of Object.keys(esperada)) {
    exigir(actual?.[campo] === esperada[campo],
      `La configuración de empresa difiere en ${campo}. No se sobrescribió una configuración existente; revisar en Reportes y alinear el entorno antes de repetir.`)
  }
}

export async function sembrarAutotachi({ env = process.env, fetchImpl = fetch, timeoutMs = 10000 } = {}) {
  const resumen = { estado: 'EN_PROCESO', usuarios: [], productos: [], ventasCreadas: 0,
    empresa: { accion: 'OMITIDA' }, referenciasExcluidas: catalogo.filter(p => !p.activo).map(p => p.referencia) }
  let operacion = 'configuración'
  try {
    const gateway = origenGateway(env)
    const empresa = empresaDelEntorno(env)
    for (const cuenta of cuentas) {
      exigir(typeof env[cuenta.variable] === 'string' && env[cuenta.variable].length > 0,
        `Configurar ${cuenta.variable}; no hay contraseña por omisión.`)
    }
    async function api(metodo, ruta, cuerpo, esperado, token) {
      operacion = `${metodo} ${ruta}`
      const escritura = (metodo === 'POST' && ruta !== '/auth/login') || metodo === 'PUT'
      const incierto = escritura ? ' La escritura puede haberse guardado; no se reintentó. Consultar el recurso antes de repetir.' : ''
      let respuesta
      try {
        respuesta = await fetchImpl(new URL(ruta, gateway), {
          method: metodo, redirect: 'error', signal: AbortSignal.timeout(timeoutMs),
          headers: { Accept: 'application/json', ...(cuerpo ? { 'Content-Type': 'application/json' } : {}),
            ...(token ? { Authorization: `Bearer ${token}` } : {}) },
          ...(cuerpo ? { body: JSON.stringify(cuerpo) } : {}),
        })
      } catch {
        throw new Error(`${operacion}: falló la conexión o venció el plazo.${incierto}`)
      }
      if (!(Array.isArray(esperado) ? esperado : [esperado]).includes(respuesta.status)) {
        try { await respuesta.body?.cancel() } catch { /* No imprimir errores del transporte. */ }
        throw new Error(`${operacion}: HTTP ${respuesta.status}.${escritura && respuesta.status >= 500 ? incierto : ''}`)
      }
      if (respuesta.status === 404) {
        await respuesta.body?.cancel()
        return EMPRESA_AUSENTE
      }
      try { return await respuesta.json() }
      catch { throw new Error(`${operacion}: respuesta JSON incompleta.${incierto}`) }
    }
    let adminToken
    // DataInitializer crea las cuentas; aquí solo se verifica el login y perfil de las cuatro.
    for (const cuenta of cuentas) {
      const acceso = await api('POST', '/auth/login', { correo: cuenta.correo, password: env[cuenta.variable] }, 200)
      exigir(acceso?.correo === cuenta.correo && acceso.rol === cuenta.rol
        && typeof acceso.token === 'string' && acceso.token.length > 0,
      `Login discordante para ${cuenta.correo}; revisar su identidad y rol.`)
      const perfil = await api('GET', '/auth/me', null, 200, acceso.token)
      exigir(perfil?.correo === cuenta.correo && perfil.nombre === cuenta.nombre && perfil.rol === cuenta.rol
        && perfil.activo === true && typeof perfil.id === 'string' && perfil.id.length > 0,
      `Perfil discordante para ${cuenta.correo}; no se modificó la cuenta.`)
      resumen.usuarios.push({ id: perfil.id, correo: cuenta.correo, rol: cuenta.rol, accion: 'VERIFICADO' })
      if (!adminToken) adminToken = acceso.token
    }
    const lista = await api('GET', '/inventario/productos', null, 200, adminToken)
    // Comprobar todo antes de escribir. Stock excluido: nunca reponer consumo ni ajustes.
    const existentes = productos.map(p => coincidencia(lista, 'nombre', { ...p, activo: true }, camposCatalogo))
    if (empresa) {
      resumen.empresa = { accion: 'VERIFICANDO' }
      const anterior = await api('GET', '/reportes/configuracion', null, [200, 404], adminToken)
      // PUT tiene id fijo en el servidor, pero actualizar en cada arranque
      // desharia ediciones legitimas. Solo crear ante un 404 confirmado.
      if (anterior !== EMPRESA_AUSENTE) comprobarEmpresa(anterior, empresa)
      else resumen.empresa = { accion: 'SIN_CONFIRMAR' }
      const actual = anterior === EMPRESA_AUSENTE
        ? await api('PUT', '/reportes/configuracion', empresa, 200, adminToken) : anterior
      comprobarEmpresa(actual, empresa)
      resumen.empresa = { accion: anterior === EMPRESA_AUSENTE ? 'CREADA' : 'REUTILIZADA', nombreEmpresa: actual.nombreEmpresa }
    }
    for (const [i, producto] of productos.entries()) {
      const existente = existentes[i]
      const actual = existente ?? await api('POST', '/inventario/productos', producto, 201, adminToken)
      exigir(actual?.nombre === producto.nombre, `Alta discordante de ${producto.nombre}; revisar el listado antes de repetir.`)
      coincidencia([actual], 'nombre', { ...producto, activo: true },
        existente ? camposCatalogo : [...camposCatalogo, 'existencia'])
      resumen.productos.push({ id: actual.id, nombre: producto.nombre, precio: actual.precio,
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
    exigir(process.argv.length === 2, 'Uso: node --env-file=infra/.env scripts/semilla-autotachi.mjs')
    console.log(JSON.stringify(await sembrarAutotachi(), null, 2))
  } catch (error) {
    console.error(JSON.stringify({ error: error.message, ...(error.resumen ? { resumen: error.resumen } : {}) }, null, 2))
    process.exitCode = 1
  }
}

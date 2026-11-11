import { setTimeout as esperar } from 'node:timers/promises'
import { clientes, productos, exigir, coincidencia, decimalCanonico, origenGateway } from './semilla-comun.mjs'

const PRODUCTO_NO_DISPONIBLE = 'El producto no está disponible o está inactivo: '
const CLIENTE_NO_DISPONIBLE = 'el cliente no está disponible; si acaba de darlo de alta, inténtelo de nuevo en unos segundos'
const idValido = id => typeof id === 'string' && id.trim().length > 0
const folioValido = folio => Number.isSafeInteger(folio) && folio > 0

// Esta fase NO es idempotente. Solo corre de forma controlada sobre la fase 1
// sin ventas previas. No guarda un archivo que prometa recuperación del POST.
export async function sembrarVentas({ env = process.env, fetchImpl = fetch,
  timeoutMs = 10000, plazoMs = Number(env.SEMILLA_VENTAS_PLAZO_MS ?? 30000), intervaloMs = 500 } = {}) {
  const resumen = { fase: 2, estado: 'EN_PROCESO', ventasPrevistas: 3, ventasConfirmadas: [], ventaEnCurso: null,
    rechazosReplica: { producto: 0, cliente: 0 },
    advertencia: 'Fase no idempotente. Conservar este resumen. No repetir el lote ni un POST incierto; consultar los ids conocidos y verificar manualmente antes de continuar.' }
  let operacion = 'configuración'
  try {
    const gateway = origenGateway(env)
    exigir(Number.isInteger(plazoMs) && plazoMs > 0 && plazoMs <= 300000, 'SEMILLA_VENTAS_PLAZO_MS debe ser un entero entre 1 y 300000.')
    exigir(Number.isInteger(timeoutMs) && timeoutMs > 0 && Number.isInteger(intervaloMs) && intervaloMs > 0,
      'Los plazos de petición y sondeo deben ser positivos.')
    let token
    const reloj = () => performance.now()
    const nuevoLimite = () => reloj() + plazoMs
    function restante(limite) {
      const ms = Math.floor(limite - reloj())
      exigir(ms > 0, `${operacion}: plazo agotado. Se conserva el último estado conocido; no se repite la venta.`)
      return ms
    }
    async function pausa(limite) { await esperar(Math.min(intervaloMs, restante(limite))) }

    // No arroja cuerpos HTTP ni errores de transporte a la salida: pueden
    // contener datos enviados, credenciales o tokens. El caller decide qué
    // respuestas permiten repetir; este helper jamás repite una petición.
    async function pedir(metodo, ruta, cuerpo, limite) {
      operacion = `${metodo} ${ruta}`
      const ms = Math.min(timeoutMs, restante(limite))
      let respuesta
      try {
        respuesta = await fetchImpl(new URL(ruta, gateway), { method: metodo, redirect: 'error',
          headers: { Accept: 'application/json', ...(cuerpo ? { 'Content-Type': 'application/json' } : {}),
            ...(token ? { Authorization: `Bearer ${token}` } : {}) },
          ...(cuerpo ? { body: JSON.stringify(cuerpo) } : {}), signal: AbortSignal.timeout(ms) })
        return { status: respuesta.status, datos: await respuesta.json() }
      } catch {
        return { status: respuesta?.status ?? null, datos: null }
      }
    }
    const transitorio = r => r.status === null || r.status >= 500 || (r.status === 200 && r.datos === null)
    async function leer(ruta, limite = nuevoLimite()) {
      for (;;) {
        const r = await pedir('GET', ruta, null, limite)
        if (transitorio(r)) { await pausa(limite); continue }
        exigir(r.status === 200, `${operacion}: HTTP ${r.status}. No se continúa con otras ventas.`)
        return r.datos
      }
    }

    const acceso = await pedir('POST', '/auth/login', { correo: 'admin@monpoint.com',
      password: env.ADMIN_DEFAULT_PASSWORD || 'Admin123!' }, nuevoLimite())
    exigir(acceso.status === 200 && acceso.datos?.rol === 'ADMIN' && idValido(acceso.datos?.token),
      'Autenticación ADMIN fallida o incompleta; no se creó ninguna venta.')
    token = acceso.datos.token
    const admin = await leer('/auth/me')
    exigir(admin?.activo === true && admin.rol === 'ADMIN' && admin.correo === 'admin@monpoint.com',
      'El administrador no está activo o su identidad no coincide.')

    // Disponibilidad HTTP, no prueba de entrega futura de eventos. El conjunto
    // debe estar sano y sus colas declaradas antes de ejecutar incluso la fase 1.
    const avisos = await leer('/notificaciones')
    exigir(Array.isArray(avisos), 'Respuesta de notificaciones inválida; no se creó ninguna venta.')
    const reporte = await leer('/reportes/ventas/agregado?periodo=DIA&desde=2000-01-01&hasta=2000-01-01')
    exigir(Array.isArray(reporte?.puntos) && decimalCanonico(reporte.total) !== null,
      'Respuesta de reportes inválida; no se creó ninguna venta.')

    // Toda la validación del lote precede al primer POST de venta.
    const catalogo = await leer('/inventario/productos')
    const contactos = await leer('/clientes')
    const existentes = productos.map(p => {
      const actual = coincidencia(catalogo, 'nombre', { ...p, activo: true },
        ['descripcion', 'precio', 'umbralStock', 'activo'])
      exigir(actual, `Falta ${p.nombre}; ejecutar la fase 1 y revisar los datos.`)
      exigir(actual.existencia === p.existencia,
        `${p.nombre}: se esperaba existencia inicial ${p.existencia}. La fase 2 requiere datos sin ventas previas; no repone stock ni repite ventas.`)
      return actual
    })
    const personas = clientes.map(c => {
      const actual = coincidencia(contactos, 'nombre', c, ['correo', 'telefono', 'direccion'])
      exigir(actual, `Falta ${c.nombre}; ejecutar la fase 1 y revisar los datos.`)
      return actual
    })
    const lote = [
      { clienteId: personas[0].id, lineas: [{ productoId: existentes[0].id, cantidad: 5 }] },
      { clienteId: personas[1].id, lineas: [{ productoId: existentes[1].id, cantidad: 2 }] },
      { clienteId: null, lineas: [{ productoId: existentes[2].id, cantidad: 1 }] },
    ]

    for (const [indice, solicitud] of lote.entries()) {
      // Un solo plazo por venta abarca la espera de ambas réplicas Y la saga.
      const limite = nuevoLimite()
      const actual = { orden: indice + 1, ...solicitud, estado: 'NO_ENVIADA', id: null, folio: null }
      resumen.ventaEnCurso = actual
      for (;;) {
        operacion = 'POST /ventas'
        restante(limite)
        actual.estado = 'INCIERTA' // Desde que se envía, pudo persistirse incluso si no llega respuesta.
        const r = await pedir('POST', '/ventas', solicitud, limite)
        if (r.status === 400) {
          actual.estado = 'RECHAZADA_HTTP'
          const producto = solicitud.lineas.some(l => r.datos?.detail === PRODUCTO_NO_DISPONIBLE + l.productoId)
          const cliente = solicitud.clienteId !== null && r.datos?.detail === CLIENTE_NO_DISPONIBLE
          exigir(producto || cliente, 'POST /ventas: HTTP 400 no reintentable. Revisar el resumen; no se continúa el lote.')
          resumen.rechazosReplica[producto ? 'producto' : 'cliente']++
          await pausa(limite)
          continue
        }
        if (r.status === 401 || r.status === 403) actual.estado = 'RECHAZADA_HTTP'
        // Guardar cualquier identidad recuperable aun con un 202 incompleto.
        if (r.status === 202) {
          if (idValido(r.datos?.id)) actual.id = r.datos.id
          if (folioValido(r.datos?.folio)) actual.folio = r.datos.folio
        }
        exigir(r.status === 202 && actual.id && actual.folio && r.datos.estado === 'PENDIENTE',
          `POST /ventas: ${r.status === null ? 'sin respuesta' : 'HTTP ' + r.status}, respuesta no aceptable o incompleta. No se reintentó; revisar ventaEnCurso y no volver a ejecutar el lote.`)
        actual.estado = 'PENDIENTE'
        break
      }

      for (;;) {
        const venta = await leer(`/ventas/${encodeURIComponent(actual.id)}`, limite)
        exigir(venta?.id === actual.id && venta.folio === actual.folio,
          'GET /ventas: identidad discordante o respuesta incompleta. Conservar el folio y verificar; no se repite el POST.')
        if (venta.estado === 'PENDIENTE') { await pausa(limite); continue }
        if (venta.estado === 'CANCELADA') {
          actual.estado = 'CANCELADA'
          throw new Error('La venta fue CANCELADA. Consultar su id para ver el motivo; no se continúa el lote.')
        }
        exigir(venta.estado === 'CONFIRMADA', 'GET /ventas: estado desconocido. Verificar el folio; no se repite el POST.')
        actual.estado = 'CONFIRMADA'
        exigir(decimalCanonico(venta.total) !== null && venta.clienteId === solicitud.clienteId
          && Array.isArray(venta.lineas) && venta.lineas.length === solicitud.lineas.length
          && solicitud.lineas.every(l => venta.lineas.some(v => v.productoId === l.productoId && v.cantidad === l.cantidad)),
        'Venta CONFIRMADA con datos discordantes o incompletos. Revisar el folio; no se continúa el lote.')
        resumen.ventasConfirmadas.push({ ...actual, total: venta.total })
        resumen.ventaEnCurso = null
        break
      }
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

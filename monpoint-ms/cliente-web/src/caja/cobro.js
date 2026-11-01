/** Estado de caja retenido en memoria durante la sesión. Nunca persiste tokens ni ventas en disco. */
export const estadoInicial = () => ({
  estado: 'inactivo',
  lineas: [],
  clienteId: '',
  venta: null,
  error: '',
  pendientes: [],
})
export const editable = (s) => ['inactivo', 'cancelada', 'sinRespuesta'].includes(s.estado)
export const cantidadValida = (n) => Number.isInteger(n) && n > 0 && n <= 2147483647

// Conserva la precisión del catálogo. Solo el estimado agregado se redondea a centavos.
function decimal(importe) {
  const m = /^(\d+)(?:\.(\d+))?$/.exec(String(importe))
  if (!m) return null
  return {
    entero: m[1],
    fraccion: m[2] ?? '',
    valor: BigInt(m[1] + (m[2] ?? '')),
    escala: (m[2] ?? '').length,
  }
}
export const precioValido = (importe) => decimal(importe) !== null
function redondear(valor, escala) {
  if (escala <= 2) return valor * 10n ** BigInt(2 - escala)
  const divisor = 10n ** BigInt(escala - 2)
  return (valor + divisor / 2n) / divisor
}
export function centavos(importe) {
  const d = decimal(importe)
  return d ? redondear(d.valor, d.escala) : null
}
export function dinero(importe) {
  const d = decimal(importe)
  // Un dato inválido no puede apagar el render ni convertirse en un importe cero.
  return d
    ? `$${BigInt(d.entero).toLocaleString('es-MX')}.${d.fraccion.padEnd(2, '0')}`
    : 'Importe no disponible'
}
export function estimado(lineas) {
  const datos = lineas.map((l) => ({ d: decimal(l.precio), cantidad: l.cantidad }))
  if (datos.some(({ d, cantidad }) => !d || !cantidadValida(cantidad))) return null
  const escala = Math.max(2, ...datos.map(({ d }) => d.escala))
  const suma = datos.reduce(
    (s, { d, cantidad }) => s + d.valor * BigInt(cantidad) * 10n ** BigInt(escala - d.escala),
    0n,
  )
  const n = redondear(suma, escala)
  return `${n / 100n}.${String(n % 100n).padStart(2, '0')}`
}

/** Reloj inyectable para verificar carreras y plazos sin esperar tiempo real. */
export function crearCobro(
  s,
  api,
  reloj = {
    ahora: () => Date.now(),
    poner: (fn, ms) => setTimeout(fn, ms),
    quitar: (id) => clearTimeout(id),
  },
) {
  let cerrado = false,
    ciclo = 0,
    consulta,
    plazo,
    control
  let enVuelo = null
  const manuales = new Set()
  function limpiar() {
    reloj.quitar(consulta)
    reloj.quitar(plazo)
    control?.abort()
    consulta = plazo = null
    control = null
  }
  function archivar(mensaje) {
    const venta = s.venta ?? enVuelo ?? {}
    s.pendientes.push({ ...venta, mensaje, consultando: false, detalle: null })
    enVuelo = null
    s.lineas = []
    s.clienteId = ''
    s.venta = null
    s.estado = 'sinRespuesta'
    s.error = ''
    ciclo++
    limpiar()
  }
  function resolver(v) {
    if (!['CONFIRMADA', 'CANCELADA'].includes(v?.estado)) return false
    s.venta = v
    enVuelo = null
    s.estado = v.estado === 'CONFIRMADA' ? 'confirmada' : 'cancelada'
    if (v.estado === 'CONFIRMADA') {
      s.lineas = []
      s.clienteId = ''
    }
    ciclo++
    limpiar()
    return true
  }
  async function sondear(turno, limite) {
    if (cerrado || turno !== ciclo) return
    if (reloj.ahora() >= limite) {
      archivar('Sin desenlace confirmado. Consulte de nuevo antes de repetir esta venta.')
      return
    }
    control = new AbortController()
    try {
      const v = await api.obtener(`/ventas/${s.venta.id}`, { senal: control.signal })
      if (cerrado || turno !== ciclo) return
      if (reloj.ahora() >= limite) {
        archivar('Sin desenlace confirmado. Consulte de nuevo.')
        return
      }
      if (resolver(v)) return
    } catch (e) {
      if (cerrado || turno !== ciclo) return
      if (e.estado === 401) {
        archivar('Sesión terminada. Verifique esta venta antes de repetirla.')
        return
      }
      // Una consulta fallida no cancela la venta: el servidor sigue trabajando.
    }
    if (!cerrado && turno === ciclo) consulta = reloj.poner(() => sondear(turno, limite), 800)
  }
  async function cobrar() {
    if (cerrado || !editable(s) || !s.lineas.length) return
    if (s.lineas.some((l) => !cantidadValida(l.cantidad))) {
      s.error = 'Use cantidades enteras positivas.'
      return
    }
    if (s.lineas.some((l) => !precioValido(l.precio))) {
      s.error = 'Hay un precio no disponible. Actualice el catálogo o quite el producto.'
      return
    }
    enVuelo = {}
    s.estado = 'enviando'
    s.error = ''
    s.venta = null
    const turno = ++ciclo
    control = new AbortController()
    // Un POST sin respuesta tampoco autoriza a repetir el cobro.
    plazo = reloj.poner(() => {
      if (turno === ciclo && !cerrado)
        archivar(
          'No recibimos el folio. El cobro pudo registrarse: verifique la operación antes de repetirla.',
        )
    }, 10000)
    try {
      const v = await api.enviar(
        '/ventas',
        {
          clienteId: s.clienteId || null,
          lineas: s.lineas.map((l) => ({ productoId: l.productoId, cantidad: l.cantidad })),
        },
        { senal: control.signal },
      )
      if (cerrado || turno !== ciclo) return
      limpiar()
      if (!v?.id || v.folio == null) {
        archivar('Respuesta de cobro incompleta. Verifique la operación antes de repetirla.')
        return
      }
      s.venta = v
      enVuelo = { id: v.id, folio: v.folio }
      s.estado = 'enProceso'
      const limite = reloj.ahora() + 10000
      plazo = reloj.poner(() => {
        if (turno === ciclo && !cerrado)
          archivar('Han pasado 10 segundos sin desenlace. La venta puede seguir en proceso.')
      }, 10000)
      void sondear(turno, limite)
    } catch (e) {
      if (cerrado || turno !== ciclo) return
      limpiar()
      if (e.estado >= 400 && e.estado < 500 && e.estado !== 408) {
        enVuelo = null
        s.estado = 'inactivo'
        s.error = e.message
      } else {
        archivar('No pudimos confirmar el registro. Verifique la operación antes de repetirla.')
      }
    }
  }
  async function consultarPendiente(p) {
    if (
      cerrado ||
      p.consultando ||
      !p.id ||
      p.detalle?.estado === 'CONFIRMADA' ||
      p.detalle?.estado === 'CANCELADA'
    )
      return
    p.consultando = true
    const c = new AbortController()
    manuales.add(c)
    const t = reloj.poner(() => c.abort(), 10000)
    try {
      const v = await api.obtener(`/ventas/${p.id}`, { senal: c.signal })
      if (!cerrado && !c.signal.aborted) {
        p.detalle = v
        p.mensaje = v.estado === 'PENDIENTE' ? 'La venta sigue en proceso.' : ''
      }
    } catch {
      if (!cerrado) p.mensaje = 'No se pudo consultar. El resultado sigue sin confirmar.'
    } finally {
      reloj.quitar(t)
      manuales.delete(c)
      p.consultando = false
    }
  }
  function nueva() {
    if (cerrado || ['enviando', 'enProceso'].includes(s.estado)) return
    s.lineas = []
    s.clienteId = ''
    s.venta = null
    s.error = ''
    s.estado = 'inactivo'
  }
  function detener() {
    if (enVuelo !== null)
      archivar('Consulta interrumpida. Verifique esta operación antes de repetirla.')
    cerrado = true
    ciclo++
    limpiar()
    for (const c of manuales) c.abort()
    manuales.clear()
  }
  return { cobrar, consultarPendiente, nueva, detener }
}

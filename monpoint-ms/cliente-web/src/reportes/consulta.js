/**
 * Reglas de la consulta de agregacion de ventas, sin Vue.
 *
 * El servidor valida periodo, fechas, orden y un maximo de 1000 puntos.
 * El cliente elige un rango inicial y un tope visual mas estricto de 370.
 * El PDF de historial tiene otro limite: 366 dias, sin importar granularidad.
 */

/** Granularidades que admite `Periodo` en ms-reportes. No existe semana. */
export const PERIODOS = [
  { valor: 'DIA', etiqueta: 'Por día' },
  { valor: 'MES', etiqueta: 'Por mes' },
  { valor: 'ANIO', etiqueta: 'Por año' },
]

/**
 * Tope de puntos que se piden de una vez.
 *
 * El servidor rellena con cero los periodos sin ventas, asi que un rango
 * muy largo genera demasiadas barras. Este limite visual es mas estricto que
 * los 1000 puntos de AgregacionVentasService; la API no acepta cualquier rango.
 */
export const MAX_PUNTOS = 370
export const MAX_DIAS_HISTORIAL = 366

function fechaUtc (a, m, d) {
  const fecha = new Date(0)
  // Date.UTC transforma los anios 0..99 en 1900..1999; setUTCFullYear no.
  fecha.setUTCFullYear(a, m - 1, d)
  fecha.setUTCHours(0, 0, 0, 0)
  return fecha
}

const iso = (d) => d.toISOString().slice(0, 10)

/** Ultimos treinta dias, contando hoy. */
export function rangoPorOmision (hoy = new Date()) {
  const fin = fechaUtc(hoy.getFullYear(), hoy.getMonth() + 1, hoy.getDate())
  const inicio = new Date(fin)
  inicio.setUTCDate(inicio.getUTCDate() - 29)
  return { periodo: 'DIA', desde: iso(inicio), hasta: iso(fin) }
}

const FECHA = /^\d{4}-\d{2}-\d{2}$/

/** Fecha ISO a partes numericas, o null si no es una fecha real. */
function partes (texto) {
  if (!FECHA.test(texto)) return null
  const [a, m, d] = texto.split('-').map(Number)
  const fecha = fechaUtc(a, m, d)
  // Rechaza 2026-02-31: el constructor lo desplaza en vez de fallar.
  if (fecha.getUTCFullYear() !== a || fecha.getUTCMonth() !== m - 1 || fecha.getUTCDate() !== d) {
    return null
  }
  return { a, m, d, fecha }
}

/**
 * Cuantos puntos devolvera el servidor, contando los extremos.
 *
 * Reproduce `Periodo.periodosEntre`: cuenta periodos entre los inicios, no
 * dias entre fechas.
 */
export function cuentaPuntos (periodo, desde, hasta) {
  if (!PERIODOS.some((p) => p.valor === periodo)) return null
  const a = partes(desde)
  const b = partes(hasta)
  if (!a || !b) return null
  if (periodo === 'ANIO') return b.a - a.a + 1
  if (periodo === 'MES') return (b.a - a.a) * 12 + (b.m - a.m) + 1
  return Math.round((b.fecha - a.fecha) / 86400000) + 1
}

export function validar (consulta) {
  const errores = {}
  if (!PERIODOS.some((p) => p.valor === consulta.periodo)) errores.periodo = 'Indique una granularidad válida'
  const a = partes(consulta.desde)
  const b = partes(consulta.hasta)

  if (!a) errores.desde = 'Indique una fecha válida'
  if (!b) errores.hasta = 'Indique una fecha válida'
  if (a && b && a.fecha > b.fecha) {
    errores.hasta = 'La fecha final no puede ser anterior a la inicial'
  }

  if (a && b && !errores.hasta) {
    const puntos = cuentaPuntos(consulta.periodo, consulta.desde, consulta.hasta)
    if (puntos > MAX_PUNTOS) {
      errores.hasta =
        `El rango da ${puntos} puntos y la gráfica admite ${MAX_PUNTOS}. ` +
        'Acorte el rango o use una granularidad mayor.'
    }
  }

  return errores
}

export const consultaValida = (consulta) => Object.keys(validar(consulta)).length === 0

/** El historial cuenta dias inclusivos, no barras del agregado. */
export function validarHistorial ({ desde, hasta }) {
  const errores = {}
  const a = partes(desde)
  const b = partes(hasta)
  if (!a) errores.desde = 'Indique una fecha válida'
  if (!b) errores.hasta = 'Indique una fecha válida'
  if (a && b) {
    const dias = cuentaPuntos('DIA', desde, hasta)
    if (dias < 1) errores.hasta = 'La fecha final no puede ser anterior a la inicial'
    else if (dias > MAX_DIAS_HISTORIAL) errores.hasta = `El historial admite como máximo ${MAX_DIAS_HISTORIAL} días; el rango pide ${dias}.`
  }
  return errores
}

/** Ruta con su cadena de consulta, lista para el Gateway. */
export const rutaAgregado = ({ periodo, desde, hasta }) =>
  `/reportes/ventas/agregado?periodo=${encodeURIComponent(periodo)}` +
  `&desde=${encodeURIComponent(desde)}&hasta=${encodeURIComponent(hasta)}`

/**
 * Etiqueta del eje.
 *
 * Se construye desde las partes de la cadena ISO y no con `new Date(texto)`
 * seguido de un formateador local: eso interpretaria la fecha en UTC y podria
 * mostrar el dia anterior en una zona al oeste de Greenwich, que es
 * exactamente la de este proyecto.
 */
export function etiquetaPunto (periodo, isoFecha) {
  const p = partes(isoFecha)
  if (!p) return isoFecha
  const meses = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic']
  if (periodo === 'ANIO') return String(p.a)
  if (periodo === 'MES') return `${meses[p.m - 1]} ${p.a}`
  return `${p.d} ${meses[p.m - 1]}`
}

/** Nombre con el que se guarda un documento, ya que el servidor no lo expone. */
export const nombreDocumento = (base, extra = '') =>
  `${base}${extra ? `-${extra}` : ''}.pdf`

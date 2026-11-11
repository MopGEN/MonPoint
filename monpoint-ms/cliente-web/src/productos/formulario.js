import { decimal } from '../comun/importe.js'
import { filtrarPor } from '../comun/filtro.js'

/**
 * Reglas del formulario de producto, sin Vue, para poder probarlas solas.
 *
 * Valida los campos de `ProductoRequest` y limita el formato de precio del
 * cliente a dos decimales y doce enteros; el servidor admite otros BigDecimal
 * no negativos. No sustituye la validacion remota. Lo que se
 * gana validando aqui es no gastar una peticion en un error evidente y poder
 * senalar el campo antes de enviarlo.
 *
 * Las claves de los errores son los nombres de campo del DTO, para que un error
 * del cliente y uno del servidor —que llegan en el mapa `campos` del
 * ProblemDetail— se pinten en el mismo sitio sin traduccion.
 */

/** Valor que usa el servidor cuando se da de alta sin umbral. */
export const UMBRAL_POR_OMISION = 5

/** Maximo de un int de Java: el servidor rechaza cualquier cosa mayor. */
const MAX_ENTERO = 2147483647

/**
 * Digitos de parte entera admitidos en el precio.
 *
 * El precio viaja como numero JSON, que es el camino ya probado contra el
 * servicio. Number no representa exactamente muchas fracciones decimales.
 * El limite conserva el valor decimal al serializar de nuevo el numero como
 * JSON; no se afirma exactitud binaria. Un literal mayor se rechaza en vez de
 * enviarse con una precision no garantizada. Es una restriccion del cliente.
 */
const MAX_ENTEROS_PRECIO = 12

export const vacio = () => ({
  nombre: '',
  descripcion: '',
  precio: '',
  existencia: '',
  umbralStock: '',
})

/** Carga un producto del catalogo en el formulario. Todo viaja como texto. */
export const desdeProducto = (p) => ({
  nombre: p?.nombre ?? '',
  descripcion: p?.descripcion ?? '',
  precio: p?.precio === null || p?.precio === undefined ? '' : String(p.precio),
  existencia: p?.existencia === null || p?.existencia === undefined ? '' : String(p.existencia),
  umbralStock:
    p?.umbralStock === null || p?.umbralStock === undefined ? '' : String(p.umbralStock),
})

function enteroValido (texto) {
  if (!/^\d+$/.test(texto)) return null
  const n = Number(texto)
  return Number.isSafeInteger(n) && n <= MAX_ENTERO ? n : null
}

/**
 * @param {object} forma
 * @param {object} [opciones]
 * @param {boolean} [opciones.edicion] en edicion el umbral es obligatorio
 * @returns {Record<string,string>} errores por campo; vacio si todo es valido
 */
export function validar (forma, { edicion = false } = {}) {
  const errores = {}

  if (forma.nombre.trim() === '') errores.nombre = 'El nombre es obligatorio'

  const p = decimal(forma.precio.trim())
  if (forma.precio.trim() === '') errores.precio = 'El precio es obligatorio'
  else if (!p) errores.precio = 'Use solo digitos y un punto decimal, sin signo'
  else if (p.escala > 2) errores.precio = 'Como maximo dos decimales'
  else if (p.entero.replace(/^0+(?=\d)/, '').length > MAX_ENTEROS_PRECIO) {
    errores.precio = 'El importe es demasiado grande'
  }

  if (forma.existencia.trim() === '') errores.existencia = 'La existencia es obligatoria'
  else if (enteroValido(forma.existencia.trim()) === null) {
    errores.existencia = 'Use un numero entero de cero en adelante'
  }

  const umbral = forma.umbralStock.trim()
  if (umbral === '') {
    // Al crear, el servidor pone el valor por omision. Al editar, un umbral
    // nulo CONSERVA el actual en lugar de restablecerlo, asi que dejar el
    // campo vacio no haria lo que el usuario espera: se exige.
    if (edicion) errores.umbralStock = 'Indique el umbral; dejarlo vacio conservaria el anterior'
  } else if (enteroValido(umbral) === null) {
    errores.umbralStock = 'Use un numero entero de cero en adelante'
  }

  return errores
}

/**
 * Cuerpo de la peticion. Solo se llama con un formulario ya validado.
 *
 * `descripcion` vacia viaja como null y no como cadena vacia: el campo es
 * opcional en el DTO y guardar "" convertiria "sin descripcion" en una
 * descripcion que existe y esta en blanco.
 */
export function aPeticion (forma) {
  const umbral = forma.umbralStock.trim()
  const descripcion = forma.descripcion.trim()
  return {
    nombre: forma.nombre.trim(),
    descripcion: descripcion === '' ? null : descripcion,
    precio: Number(forma.precio.trim()),
    existencia: Number(forma.existencia.trim()),
    umbralStock: umbral === '' ? null : Number(umbral),
  }
}

/** true cuando el producto esta en su umbral o por debajo. */
export const bajoUmbral = (p) =>
  Number.isFinite(p?.existencia) && Number.isFinite(p?.umbralStock) && p.existencia <= p.umbralStock

/**
 * Filtro por nombre y descripcion.
 *
 * La mecanica vive en comun/filtro.js, que la comparten las demas pantallas;
 * aqui solo se fijan los campos del catalogo.
 */
export const filtrar = (productos, consulta) =>
  filtrarPor(productos, consulta, ['nombre', 'descripcion'])

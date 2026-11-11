import { filtrarPor } from '../comun/filtro.js'

/**
 * Reglas del formulario de cliente, sin Vue, para poder probarlas solas.
 *
 * Reproducen las de `ClienteRequest`: nombre obligatorio y correo opcional
 * pero con formato valido si viene. Telefono y direccion no tienen validacion
 * en el servidor y aqui tampoco se inventa ninguna.
 *
 * Las claves de los errores son los nombres de campo del DTO, para que un
 * error del cliente y uno del servidor —que llega en el mapa `campos` del
 * ProblemDetail— se pinten en el mismo sitio.
 */

export const vacio = () => ({ nombre: '', correo: '', telefono: '', direccion: '' })

export const desdeCliente = (c) => ({
  nombre: c?.nombre ?? '',
  correo: c?.correo ?? '',
  telefono: c?.telefono ?? '',
  direccion: c?.direccion ?? '',
})

/**
 * Comprobacion deliberadamente laxa del correo.
 *
 * El servidor usa `@Email` de Jakarta, cuyo patron es permisivo. Validar aqui
 * con una expresion mas estricta rechazaria direcciones que el servidor
 * aceptaria, y el usuario no tendria forma de saber que el limite lo pone el
 * cliente. Solo se descarta lo que ningun correo puede ser: sin arroba, con
 * mas de una, con algun lado vacio o con espacios.
 */
export function correoPlausible (texto) {
  if (/\s/.test(texto)) return false
  const partes = texto.split('@')
  return partes.length === 2 && partes[0].length > 0 && partes[1].length > 0
}

export function validar (forma) {
  const errores = {}
  if (forma.nombre.trim() === '') errores.nombre = 'El nombre es obligatorio'
  const correo = forma.correo.trim()
  if (correo !== '' && !correoPlausible(correo)) {
    errores.correo = 'El correo no tiene un formato válido'
  }
  return errores
}

/**
 * Cuerpo de la peticion.
 *
 * Los campos opcionales vacios viajan como null y no como cadena vacia, pero
 * NO porque el modelo los distinga: `Cliente.limpiar()` recorta el texto y
 * guarda null cuando queda vacio, asi que ambos acaban igual en la base.
 *
 * La razon es otra: enviar null es decir la verdad sobre lo que quedara
 * almacenado. Enviando "" el servidor responderia null, y cualquier
 * comparacion entre lo enviado y lo devuelto veria un cambio que nunca hubo.
 */
export function aPeticion (forma) {
  const limpio = (v) => {
    const t = v.trim()
    return t === '' ? null : t
  }
  return {
    nombre: forma.nombre.trim(),
    correo: limpio(forma.correo),
    telefono: limpio(forma.telefono),
    direccion: limpio(forma.direccion),
  }
}

/**
 * Filtro por nombre, correo y telefono.
 *
 * No se busca por direccion: es el campo mas largo, el que mas coincidencias
 * casuales produce, y quien busca un cliente lo busca por como se llama o por
 * como contactarlo.
 */
export const filtrar = (clientes, consulta) =>
  filtrarPor(clientes, consulta, ['nombre', 'correo', 'telefono'])

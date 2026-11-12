import { filtrarPor } from '../comun/filtro.js'

/**
 * Reglas del formulario de usuario, sin Vue.
 *
 * DELIBERADAMENTE NO se reimplementa la politica de contrasena. El servidor la
 * aplica en PasswordValidator y responde con la lista completa de reglas
 * incumplidas, no solo la primera. Reproducirla aqui tendria dos costos y
 * ninguna ventaja: la regla de los 72 bytes cuenta acentos y ñ como dos, lo
 * que en JavaScript es facil de calcular mal, y cualquier divergencia haria
 * que la pantalla rechazara contrasenas que el sistema aceptaria. Aqui solo se
 * exige que venga cuando es obligatoria; el resto lo dice el servicio y se
 * muestra tal cual.
 */

/** Los dos valores del enum Rol. Uno distinto falla al deserializar el JSON. */
export const ROLES = [
  { valor: 'ADMIN', etiqueta: 'Administrador' },
  { valor: 'VENDEDOR', etiqueta: 'Vendedor' },
]

/** Texto informativo, no validacion: la autoridad es PasswordValidator. */
export const REGLAS_PASSWORD =
  'Al menos 8 caracteres, con mayúscula, minúscula, número y uno de @$!%*?&.'

export const vacio = () => ({ nombre: '', correo: '', password: '', rol: 'VENDEDOR' })

/** La contrasena NUNCA se precarga: el servidor no la devuelve ni podria. */
export const desdeUsuario = (u) => ({
  nombre: u?.nombre ?? '',
  correo: u?.correo ?? '',
  password: '',
  rol: u?.rol ?? 'VENDEDOR',
})

/**
 * @param {object} forma
 * @param {object} [opciones]
 * @param {boolean} [opciones.edicion] al editar, la contrasena es opcional
 */
export function validar (forma, { edicion = false } = {}) {
  const errores = {}
  if (forma.nombre.trim() === '') errores.nombre = 'El nombre es obligatorio'
  if (forma.correo.trim() === '') errores.correo = 'El correo es obligatorio'
  if (!edicion && forma.password === '') errores.password = 'La contraseña es obligatoria'
  if (!ROLES.some((r) => r.valor === forma.rol)) errores.rol = 'Elija un rol'
  return errores
}

/**
 * Cuerpo de la peticion.
 *
 * Al editar, una contrasena vacia viaja como null para conservar la actual, tal como
 * documenta ActualizarUsuarioRequest. La contrasena no se recorta: un espacio
 * al principio o al final es un caracter valido y recortarlo guardaria algo
 * distinto de lo que la persona escribio.
 *
 * `rol` lleva valor en el alta y al cambiarlo en la edicion; null conserva el
 * actual. El servicio rechaza un cambio de rol propio, no un valor sin cambio.
 */
export function aPeticion (forma, { edicion = false, rolOriginal = null } = {}) {
  const base = {
    nombre: forma.nombre.trim(),
    correo: forma.correo.trim(),
  }
  if (!edicion) return { ...base, password: forma.password, rol: forma.rol }

  const cuerpo = { ...base, password: forma.password === '' ? null : forma.password }
  cuerpo.rol = forma.rol === rolOriginal ? null : forma.rol
  return cuerpo
}

/**
 * Traduce un fallo del servicio a errores por campo.
 *
 * ms-auth devuelve tres formas distintas y ninguna pantalla deberia tener que
 * recordarlas:
 *
 * - Validacion de Bean Validation: mapa `campos`, que ya sirve tal cual.
 * - Politica de contrasena: lista en `errores`, no un mapa. Se juntan bajo el
 *   campo password, que es el unico al que se refieren.
 * - Correo repetido: 409 sin campos. GlobalExceptionHandler de ms-auth mapea
 *   CorreoDuplicadoException y DuplicateKeyException a "Correo duplicado".
 *   Esta traduccion se limita al contrato actual de esas operaciones.
 *
 * @returns {Record<string,string>|null} null cuando el fallo no es de un campo
 */
export function erroresDeCausa (causa) {
  const campos = causa?.campos
  if (campos && typeof campos === 'object' && Object.keys(campos).length > 0) return campos

  const reglas = causa?.cuerpo?.errores
  if (Array.isArray(reglas) && reglas.length > 0) return { password: reglas.join(' ') }

  if (causa?.estado === 409) return { correo: causa.message }

  return null
}

export const filtrar = (usuarios, consulta) =>
  filtrarPor(usuarios, consulta, ['nombre', 'correo', 'rol'])

/** /auth/me aporta el id estable, aunque el correo haya cambiado. */
export const esUnoMismo = (usuario, idPropio) =>
  Boolean(idPropio) && usuario?.id === idPropio

/**
 * Cliente HTTP unico contra el api-gateway.
 *
 * Ninguna vista debe llamar a `fetch` directamente: todo pasa por aqui para que
 * el token, el formato de error y la expiracion de sesion se traten en un solo
 * lugar.
 *
 * Este modulo NO importa el almacen de sesion ni el enrutador. Si lo hiciera se
 * formaria un ciclo (gateway -> enrutador -> almacen -> gateway) que en ESM se
 * resuelve con valores a medio inicializar y falla de forma intermitente. En su
 * lugar `main.js` le inyecta dos funciones al arrancar.
 */

const BASE = (import.meta.env.VITE_GATEWAY_URL ?? 'http://localhost:8080').replace(/\/+$/, '')

/** Inyectado por main.js. Devuelve el token vigente o null. */
let obtenerToken = () => null

/** Inyectado por main.js. Se invoca cuando el Gateway rechaza el token. */
let alExpirarSesion = () => {}

export function configurarGateway (opciones) {
  if (typeof opciones.obtenerToken === 'function') obtenerToken = opciones.obtenerToken
  if (typeof opciones.alExpirarSesion === 'function') alExpirarSesion = opciones.alExpirarSesion
}

/**
 * Error con la informacion que una vista necesita para decidir que mostrar.
 *
 * `mensaje` ya viene listo para ensenarse al cajero: se toma del `detail` del
 * ProblemDetail (RFC 9457) que devuelven los servicios, que es donde viven los
 * textos redactados en el dominio, por ejemplo el de cliente no disponible
 * todavia por replica en vuelo.
 */
export class ErrorDeGateway extends Error {
  constructor (mensaje, { estado = 0, titulo = null, campos = null, cuerpo = null } = {}) {
    super(mensaje)
    this.name = 'ErrorDeGateway'
    this.estado = estado
    this.titulo = titulo
    /** Errores de validacion por campo, cuando el servicio los envia. */
    this.campos = campos
    /** Cuerpo crudo, para depuracion. */
    this.cuerpo = cuerpo
  }

  /** true cuando el fallo es de red o el Gateway no respondio. */
  get esDeRed () {
    return this.estado === 0
  }
}

async function leerCuerpo (respuesta) {
  if (respuesta.status === 204 || respuesta.status === 205) return null
  const texto = await respuesta.text()
  if (texto === '') return null
  const tipo = respuesta.headers.get('content-type') ?? ''
  if (tipo.includes('json')) {
    try {
      return JSON.parse(texto)
    } catch {
      return texto
    }
  }
  return texto
}

function mensajeDeProblema (cuerpo, estado) {
  if (cuerpo && typeof cuerpo === 'object') {
    // ProblemDetail: `detail` es el texto especifico del caso, `title` el
    // generico. Se prefiere el especifico.
    if (typeof cuerpo.detail === 'string' && cuerpo.detail !== '') return cuerpo.detail
    if (typeof cuerpo.message === 'string' && cuerpo.message !== '') return cuerpo.message
    if (typeof cuerpo.title === 'string' && cuerpo.title !== '') return cuerpo.title
  }
  if (typeof cuerpo === 'string' && cuerpo !== '') return cuerpo
  return `El servidor respondio ${estado}.`
}

/**
 * @param {string} metodo
 * @param {string} ruta         ruta absoluta del Gateway, p. ej. '/ventas'
 * @param {object} [opciones]
 * @param {unknown} [opciones.cuerpo]        se envia como JSON
 * @param {boolean} [opciones.sinAutenticar] no adjunta token y no dispara la
 *   expiracion de sesion ante un 401. Lo usa el inicio de sesion: ahi un 401
 *   significa contrasena incorrecta, no sesion caducada, y tratarlo como
 *   caducada provocaria un bucle de redirecciones hacia la propia pantalla de
 *   inicio de sesion.
 * @param {AbortSignal} [opciones.senal]
 */
async function peticion (metodo, ruta, opciones = {}) {
  const { cuerpo, sinAutenticar = false, senal } = opciones

  const cabeceras = { Accept: 'application/json' }
  if (cuerpo !== undefined) cabeceras['Content-Type'] = 'application/json'

  if (!sinAutenticar) {
    const token = obtenerToken()
    if (token) cabeceras.Authorization = `Bearer ${token}`
  }

  let respuesta
  try {
    respuesta = await fetch(`${BASE}${ruta}`, {
      method: metodo,
      headers: cabeceras,
      body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
      signal: senal,
      // No se usan cookies: la credencial es el token en la cabecera. Mantener
      // 'omit' evita que el navegador exija
      // Access-Control-Allow-Credentials en el Gateway.
      credentials: 'omit',
    })
  } catch (causa) {
    if (causa?.name === 'AbortError') throw causa
    throw new ErrorDeGateway(
      'No se pudo contactar al servidor. Revise que la infraestructura este levantada.',
      { estado: 0 },
    )
  }

  const datos = await leerCuerpo(respuesta)

  if (respuesta.ok) return datos

  if (respuesta.status === 401 && !sinAutenticar) {
    // El Gateway rechazo el token: caducado, mal firmado o ausente.
    alExpirarSesion()
    throw new ErrorDeGateway('Su sesion termino. Inicie sesion de nuevo.', {
      estado: 401,
      cuerpo: datos,
    })
  }

  throw new ErrorDeGateway(mensajeDeProblema(datos, respuesta.status), {
    estado: respuesta.status,
    titulo: datos?.title ?? null,
    campos: datos?.campos ?? datos?.errors ?? null,
    cuerpo: datos,
  })
}

export const gateway = {
  base: BASE,
  obtener: (ruta, opciones) => peticion('GET', ruta, opciones),
  enviar: (ruta, cuerpo, opciones) => peticion('POST', ruta, { ...opciones, cuerpo }),
  reemplazar: (ruta, cuerpo, opciones) => peticion('PUT', ruta, { ...opciones, cuerpo }),
  modificar: (ruta, cuerpo, opciones) => peticion('PATCH', ruta, { ...opciones, cuerpo }),
  eliminar: (ruta, opciones) => peticion('DELETE', ruta, opciones),
}

import { ref, shallowRef, onScopeDispose } from 'vue'

/**
 * Carga de un recurso de lectura, con los estados que toda pantalla de listado
 * necesita y los dos fallos que son faciles de escribir mal.
 *
 * PRIMER FALLO: la carrera. Si el usuario recarga dos veces, la primera
 * respuesta puede llegar despues de la segunda y pisarla con datos viejos. No
 * basta con cancelar: `fetch` cancelado rechaza de forma asincrona y el codigo
 * que sigue al `await` de la peticion vieja aun puede ejecutarse. Por eso cada
 * carga lleva un numero de secuencia y solo la ultima escribe en el estado.
 *
 * SEGUNDO FALLO: tratar 403 como un error cualquiera. Un 403 no se arregla
 * reintentando; la pantalla debe decir que el rol no alcanza, no ofrecer un
 * boton de "volver a intentar" que fallara igual. Se expone aparte.
 *
 * El 401 no llega hasta aqui: `gateway.js` dispara la expiracion de sesion y el
 * enrutador lleva al inicio de sesion antes de que la vista pueda reaccionar.
 *
 * @param {(opciones: {senal: AbortSignal}) => Promise<unknown>} cargar
 * @param {object} [opciones]
 * @param {boolean} [opciones.inmediato] arranca la primera carga al crear
 * @param {unknown} [opciones.inicial]   valor de `datos` antes de la primera carga
 */
export function useRecurso (cargar, { inmediato = true, inicial = null } = {}) {
  const datos = shallowRef(inicial)
  const cargando = ref(false)
  const error = shallowRef(null)
  /** true cuando el fallo fue de autorizacion: reintentar no sirve de nada. */
  const denegado = ref(false)
  /** false hasta que una carga termina, con exito o sin el. */
  const resuelto = ref(false)

  let secuencia = 0
  let enCurso = null

  async function recargar () {
    const mia = ++secuencia
    enCurso?.abort()
    const control = new AbortController()
    enCurso = control

    cargando.value = true
    error.value = null
    denegado.value = false

    try {
      const resultado = await cargar({ senal: control.signal })
      // Una respuesta que ya no es la ultima se descarta en silencio.
      if (mia !== secuencia) return
      datos.value = resultado
    } catch (causa) {
      if (causa?.name === 'AbortError') return
      if (mia !== secuencia) return
      denegado.value = causa?.estado === 403
      error.value = causa
    } finally {
      if (mia === secuencia) {
        cargando.value = false
        resuelto.value = true
        enCurso = null
      }
    }
  }

  // Si el componente se desmonta a mitad de carga, la peticion se aborta y la
  // secuencia avanza, de modo que una respuesta tardia no toca un estado
  // huerfano.
  onScopeDispose(() => {
    secuencia++
    enCurso?.abort()
  })

  if (inmediato) recargar()

  return { datos, cargando, error, denegado, resuelto, recargar }
}

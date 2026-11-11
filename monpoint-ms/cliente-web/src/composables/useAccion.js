import { ref, shallowRef } from 'vue'

/**
 * Operacion de escritura: alta, cambio o borrado.
 *
 * Hermano de `useRecurso`, que cubre la lectura. Existe porque las tres cosas
 * que toda escritura necesita se habian decidido por separado en cada pantalla
 * y una de las veces salio mal.
 *
 * DOS BANDERAS, A PROPOSITO. `enCurso` es reactiva y sirve para la interfaz:
 * deshabilitar el boton, cambiar su texto. `bloqueado` no es reactiva y es la
 * que impide la segunda escritura. Se separan porque cumplen papeles
 * distintos. El valor de un ref cambia sincronicamente; lo que espera al
 * render es el DOM. El cerrojo privado separa el bloqueo del estado publicado
 * para la interfaz, no compensa un retraso de enCurso.value. Confiar el
 * bloqueo al `disabled` del boton deja pasar dos clics en el mismo fotograma,
 * porque entre el primero y el repintado no hay nada que los frene.
 *
 * REPARTO DEL ERROR. Los servicios devuelven las validaciones en el mapa
 * `campos` del ProblemDetail, con la clave igual al nombre del campo del DTO.
 * Cuando vienen, se pintan en su campo y NO se muestra ademas el aviso
 * general: su texto es el generico "uno o mas campos no cumplen las
 * validaciones" y junto al mensaje concreto solo anade ruido. Cuando no
 * vienen, el fallo es de otra clase y va al aviso general.
 *
 * `campos` tambien recibe los errores que la pantalla detecta antes de enviar,
 * mediante `fijarCampos`, para que exista un unico sitio del que leer al
 * pintar. Si cada pantalla mezclara los suyos con los del servidor, volveria a
 * decidir por su cuenta lo que este modulo decide una vez.
 *
 * @param {(...args: unknown[]) => Promise<unknown>} ejecutar
 */
export function useAccion (ejecutar) {
  const enCurso = ref(false)
  const error = shallowRef(null)
  const campos = ref({})

  let bloqueado = false

  /**
   * @returns {{ok: true, valor: unknown} | {ok: false, causa?: unknown, omitida?: true}}
   *   `omitida` marca la llamada que la guarda descarto; no es un fallo y la
   *   pantalla no debe mostrar nada por ella.
   */
  async function correr (...args) {
    if (bloqueado) return { ok: false, omitida: true }
    bloqueado = true
    enCurso.value = true
    error.value = null
    campos.value = {}

    try {
      const valor = await ejecutar(...args)
      return { ok: true, valor }
    } catch (causa) {
      const recibidos = causa?.campos
      const hayCampos =
        recibidos && typeof recibidos === 'object' && Object.keys(recibidos).length > 0
      campos.value = hayCampos ? recibidos : {}
      error.value = hayCampos ? null : causa
      return { ok: false, causa }
    } finally {
      bloqueado = false
      enCurso.value = false
    }
  }

  /** Errores detectados por la pantalla antes de enviar. */
  function fijarCampos (nuevos) {
    campos.value = nuevos ?? {}
    error.value = null
  }

  function limpiar () {
    campos.value = {}
    error.value = null
  }

  return { enCurso, error, campos, correr, fijarCampos, limpiar }
}

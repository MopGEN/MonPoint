/**
 * Filtro de texto sobre una lista ya cargada.
 *
 * Ninguno de los servicios ofrece busqueda, asi que las pantallas filtran lo
 * que ya tienen en memoria. Con un catalogo grande esto habria que pedirselo
 * al servidor; queda anotado en vez de fingir que escala.
 *
 * Se normaliza quitando acentos y mayusculas: quien escribe "automatico" en
 * una caja espera encontrar "Semilla automático", y exigirle el acento
 * convierte la busqueda en un examen de ortografia.
 *
 * Importaciones relativas y sin el alias `@`: este modulo tambien se ejecuta
 * bajo `node --test`, que no resuelve el alias de Vite.
 */

/** Minusculas y sin diacriticos. Un valor ausente se trata como cadena vacia. */
export const plano = (s) =>
  String(s ?? '')
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .toLowerCase()

/**
 * @param {object[]} elementos
 * @param {string} consulta    texto libre; en blanco no filtra nada
 * @param {string[]} campos    propiedades donde buscar, en orden
 */
export function filtrarPor (elementos, consulta, campos) {
  const q = plano(consulta).trim()
  if (q === '') return elementos
  return elementos.filter((e) => campos.some((campo) => plano(e?.[campo]).includes(q)))
}

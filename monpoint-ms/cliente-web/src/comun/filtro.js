/**
 * Filtro de texto sobre una lista ya cargada.
 *
 * Las pantallas filtran lo que ya tienen en memoria para buscar tambien por
 * correo/telefono y sin acentos. Clientes si ofrece GET /clientes?nombre;
 * este filtro local no lo usa. No sustituye paginacion ni busqueda remota para
 * catalogos grandes.
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

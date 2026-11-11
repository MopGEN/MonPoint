/**
 * Aritmetica decimal exacta para importes.
 *
 * Vive fuera de `caja/` porque otras pantallas necesitan formatear precios y
 * ninguna deberia importar del modulo de la caja para hacerlo. El contenido se
 * movio tal cual desde `caja/cobro.js` (tarea 5.5): no se reescribio nada.
 *
 * No se usa `Number` en ningun punto. El catalogo trae precios con la escala
 * que decidio el servidor, y en coma flotante 41.15 * 3 da 123.44999999999999;
 * aqui todo pasa por BigInt sobre la representacion textual.
 *
 * Importaciones relativas y no con el alias `@`: este modulo se ejecuta tambien
 * bajo `node --test`, que no resuelve el alias de Vite.
 */

// Conserva la precisión del catálogo. Solo el estimado agregado se redondea a centavos.
export function decimal(importe) {
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
export function redondear(valor, escala) {
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

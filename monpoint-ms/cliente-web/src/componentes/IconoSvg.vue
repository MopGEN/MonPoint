<script setup>
import { computed } from 'vue'

/**
 * Iconos de la interfaz, dibujados y no tipografiados.
 *
 * Antes eran caracteres Unicode del bloque de formas geometricas (▤ ▦ ◍ ◐ …).
 * Inter no cubre ese bloque, asi que los resolvia una fuente de simbolos del
 * sistema: en una maquina sin esa cobertura salen como rectangulos vacios, y
 * ahi se pierde la barra de navegacion entera. Un trazado no depende de lo que
 * haya instalado.
 *
 * Todos son decorativos (`aria-hidden`): el nombre accesible lo pone siempre
 * el texto o el `aria-label` del elemento que los contiene, nunca el icono.
 *
 * El color sale de `currentColor`, igual que hacian los glifos, de modo que
 * siguen heredando el tema sin ninguna regla adicional.
 *
 * NO se incluye `⌘` de la pista de atajos de Caja: ahi no es un icono sino el
 * nombre de una tecla, en la misma frase que «Ctrl» y «Enter», y el usuario lo
 * coteja contra el simbolo grabado en su teclado. Dibujarlo lo sacaria del
 * flujo del texto y romperia esa correspondencia.
 */
const props = defineProps({
  nombre: { type: String, required: true },
  tamano: { type: [Number, String], default: 20 },
})

// Cada entrada son trazados sobre una rejilla de 24x24. `relleno` distingue
// las siluetas macizas de las que se dibujan con linea.
const ICONOS = {
  // --- Barra de navegacion ---
  caja: { d: [
    'M4 10h16a1 1 0 0 1 1 1v8a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1v-8a1 1 0 0 1 1-1Z',
    'M7 10V5a1 1 0 0 1 1-1h8a1 1 0 0 1 1 1v5',
    'M7 15h4',
  ] },
  productos: { d: [
    'M3.5 7.5 12 3l8.5 4.5v9L12 21l-8.5-4.5Z',
    'M3.5 7.5 12 12l8.5-4.5',
    'M12 12v9',
  ] },
  clientes: { d: [
    'M9 11a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7Z',
    'M2.5 20a6.5 6.5 0 0 1 13 0',
    'M16.5 11.4a3 3 0 0 0 0-5.8',
    'M17 14.2a6 6 0 0 1 4.5 5.8',
  ] },
  reportes: { d: [
    'M3.5 20.5h17',
    'M7 20.5V12',
    'M12 20.5V5',
    'M17 20.5V9',
  ] },
  usuarios: { d: [
    'M10 11.5a4 4 0 1 0 0-8 4 4 0 0 0 0 8Z',
    'M3 20.5a7 7 0 0 1 10.5-6',
    'M18 13.5l3 1.2v2.6c0 1.8-1.3 3.2-3 3.7-1.7-.5-3-1.9-3-3.7v-2.6Z',
  ] },
  notificaciones: { d: [
    'M18 16v-5a6 6 0 0 0-12 0v5l-1.5 2.5h15Z',
    'M10 19.5a2 2 0 0 0 4 0',
  ] },

  // --- Cabecera ---
  luna: { d: ['M20 14.5A8.5 8.5 0 0 1 9.5 4 8.5 8.5 0 1 0 20 14.5Z'] },
  sol: { d: [
    'M12 16.5a4.5 4.5 0 1 0 0-9 4.5 4.5 0 0 0 0 9Z',
    'M12 2.5v2', 'M12 19.5v2', 'M2.5 12h2', 'M19.5 12h2',
    'M5.3 5.3l1.4 1.4', 'M17.3 17.3l1.4 1.4',
    'M5.3 18.7l1.4-1.4', 'M17.3 6.7l1.4-1.4',
  ] },
  'cheuron-abajo': { d: ['M6 9.5l6 6 6-6'] },

  // --- Caja ---
  punto: { d: ['M12 8a4 4 0 1 1 0 8 4 4 0 0 1 0-8Z'], relleno: true },
  triangulo: { d: ['M12 5.5 21 20.5H3Z'], relleno: true },
  equis: { d: ['M6 6l12 12', 'M18 6 6 18'] },
  'flecha-derecha': { d: ['M4 12h15', 'M13.5 6.5 20 12l-6.5 5.5'] },
  carrito: { d: [
    'M2.5 4h2.6l2.6 11.2a1.6 1.6 0 0 0 1.6 1.3h8.1a1.6 1.6 0 0 0 1.6-1.2l1.5-6.3H6.3',
    'M10 20.5a1.2 1.2 0 1 0 0-2.4 1.2 1.2 0 0 0 0 2.4Z',
    'M17.5 20.5a1.2 1.2 0 1 0 0-2.4 1.2 1.2 0 0 0 0 2.4Z',
  ] },
  aviso: { d: ['M12 3.5 22 20.5H2Z', 'M12 10v4.4', 'M12 17.4v.2'] },
  reloj: { d: ['M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18Z', 'M12 7v5.2l3.4 2'] },
  visto: { d: ['M4.5 12.8 9.5 18 19.5 6.5'] },
}

const icono = computed(() => {
  const encontrado = ICONOS[props.nombre]
  // Un nombre mal escrito dejaria un hueco invisible, que es el fallo mas
  // dificil de notar en una revision. Se avisa en desarrollo.
  if (!encontrado && import.meta.env.DEV) {
    console.warn(`[IconoSvg] no existe el icono "${props.nombre}"`)
  }
  return encontrado ?? { d: [] }
})
</script>

<template>
  <svg
    class="icono-svg"
    :width="tamano"
    :height="tamano"
    viewBox="0 0 24 24"
    :fill="icono.relleno ? 'currentColor' : 'none'"
    :stroke="icono.relleno ? 'none' : 'currentColor'"
    stroke-width="1.75"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    focusable="false"
  >
    <path v-for="(d, i) in icono.d" :key="i" :d="d" />
  </svg>
</template>

<style scoped>
/* `block` evita el hueco que el navegador reserva bajo los elementos en linea
   por la linea base; dentro de una frase se alinea con `vertical-align`. */
.icono-svg {
  display: block;
  flex: none;
}
</style>

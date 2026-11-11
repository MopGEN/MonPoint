<script setup>
import { computed } from 'vue'
import { dinero } from '@/comun/importe'

/**
 * Grafica de barras en SVG, sin dependencias.
 *
 * Se descarto traer una libreria de graficas: el dato es una serie de pares
 * fecha/importe y el proyecto entra en entrega. Una dependencia mas es una
 * version mas que fijar, auditar y explicar en el documento de cierre.
 *
 * La grafica es `role="img"` con un resumen, y debajo va la misma serie como
 * tabla para lectores de pantalla. Una grafica sin su tabla es un dato que
 * solo existe para quien puede verlo.
 *
 * Las alturas se calculan con `Number`, no con la aritmetica exacta de
 * importes: son pixeles, no dinero. Los importes que se MUESTRAN siguen
 * pasando por `dinero`, que no usa coma flotante.
 */
const props = defineProps({
  /** [{ inicio, total, etiqueta }] ya ordenados. */
  puntos: { type: Array, required: true },
  descripcion: { type: String, required: true },
})

const ALTO = 220
const MARGEN = 8

const maximo = computed(() => {
  const valores = props.puntos.map((p) => Number(p.total) || 0)
  return Math.max(0, ...valores)
})

const barras = computed(() => {
  const n = props.puntos.length
  if (n === 0) return []
  const ancho = 100 / n
  return props.puntos.map((p, i) => {
    const valor = Number(p.total) || 0
    // Con todo en cero no hay escala posible: se dibujan barras vacias en vez
    // de dividir entre cero y pintar NaN.
    const proporcion = maximo.value > 0 ? valor / maximo.value : 0
    const alto = proporcion * (ALTO - MARGEN * 2)
    return {
      clave: p.inicio,
      etiqueta: p.etiqueta,
      total: p.total,
      vacia: valor === 0,
      x: i * ancho,
      ancho,
      y: ALTO - MARGEN - alto,
      alto: Math.max(alto, valor > 0 ? 1 : 0),
    }
  })
})

/** Como mucho ocho etiquetas bajo el eje: mas se solapan y no se leen. */
const paso = computed(() => Math.max(1, Math.ceil(props.puntos.length / 8)))
</script>

<template>
  <figure class="grafica">
    <svg
      v-if="puntos.length > 0"
      class="lienzo"
      :viewBox="`0 0 100 ${ALTO}`"
      preserveAspectRatio="none"
      role="img"
      :aria-label="descripcion"
    >
      <!-- Linea base: sin ella, una serie con todo en cero parece un fallo de
           dibujo en vez de un periodo sin ventas. -->
      <line x1="0" :y1="ALTO - MARGEN" x2="100" :y2="ALTO - MARGEN" class="eje" />
      <rect
        v-for="b in barras"
        :key="b.clave"
        :x="b.x + b.ancho * 0.15"
        :y="b.y"
        :width="b.ancho * 0.7"
        :height="b.alto"
        :class="['barra', { vacia: b.vacia }]"
      >
        <title>{{ b.etiqueta }}: {{ dinero(b.total) }}</title>
      </rect>
    </svg>

    <div v-if="puntos.length > 0" class="etiquetas" aria-hidden="true">
      <span
        v-for="(b, i) in barras"
        :key="b.clave"
        class="etiqueta"
        :style="{ left: `${b.x + b.ancho / 2}%` }"
      >
        {{ i % paso === 0 ? b.etiqueta : '' }}
      </span>
    </div>

    <figcaption class="mp-solo-lectores">
      <table>
        <caption>{{ descripcion }}</caption>
        <thead>
          <tr><th scope="col">Periodo</th><th scope="col">Total</th></tr>
        </thead>
        <tbody>
          <tr v-for="b in barras" :key="b.clave">
            <th scope="row">{{ b.etiqueta }}</th>
            <td>{{ dinero(b.total) }}</td>
          </tr>
        </tbody>
      </table>
    </figcaption>
  </figure>
</template>

<style scoped>
.grafica {
  margin: 0;
}

.lienzo {
  display: block;
  width: 100%;
  height: 220px;
}

.eje {
  stroke: var(--mp-borde);
  stroke-width: 1;
  vector-effect: non-scaling-stroke;
}

.barra {
  fill: var(--mp-verde);
}

/* Un periodo sin ventas no se dibuja, pero su hueco se conserva: la ausencia
   es parte de la informacion. */
.barra.vacia {
  fill: transparent;
}

.etiquetas {
  position: relative;
  height: 1.3rem;
  margin-top: 0.3rem;
}

.etiqueta {
  position: absolute;
  transform: translateX(-50%);
  white-space: nowrap;
  font-size: 0.75rem;
  color: var(--mp-texto-suave);
}
</style>

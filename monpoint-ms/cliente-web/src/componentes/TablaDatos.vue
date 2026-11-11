<script setup>
/**
 * Tabla de datos con columnas declaradas.
 *
 * Cada columna es `{ clave, titulo, cifra?, ancho? }`. `cifra: true` alinea a
 * la derecha y usa tipografia de ancho fijo, para que las columnas de importes
 * y existencias no bailen al cambiar el numero.
 *
 * El contenido de una celda se puede sustituir con una ranura nombrada
 * `celda-<clave>`, que recibe la fila. Asi una pantalla pone botones o
 * insignias sin que esta tabla sepa nada de su dominio.
 *
 * Se usa `<table>` de verdad, no una rejilla de `div`: un lector de pantalla
 * anuncia fila y columna sin trabajo adicional, y eso en una tabla de
 * existencias es la diferencia entre poder usarla y no.
 */
defineProps({
  columnas: { type: Array, required: true },
  filas: { type: Array, required: true },
  /** Propiedad que identifica cada fila de forma estable. */
  clave: { type: String, default: 'id' },
  /** Texto para lectores de pantalla que describe la tabla. */
  descripcion: { type: String, required: true },
})
</script>

<template>
  <div class="envoltura">
    <table class="mp-tabla">
      <caption class="mp-solo-lectores">{{ descripcion }}</caption>
      <thead>
        <tr>
          <th
            v-for="c in columnas"
            :key="c.clave"
            scope="col"
            :class="{ cifra: c.cifra }"
            :style="c.ancho ? { width: c.ancho } : null"
          >
            {{ c.titulo }}
          </th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="fila in filas" :key="fila[clave]">
          <td v-for="c in columnas" :key="c.clave" :class="{ cifra: c.cifra }">
            <slot :name="`celda-${c.clave}`" :fila="fila" :valor="fila[c.clave]">
              {{ fila[c.clave] }}
            </slot>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
/* El desplazamiento horizontal vive aqui y no en la pagina: en una pantalla
   estrecha se mueve la tabla, no toda la vista. */
.envoltura {
  overflow-x: auto;
}

.cifra {
  text-align: right;
  font-family: var(--mp-tipografia-cifras);
  font-variant-numeric: tabular-nums;
}

th.cifra {
  text-align: right;
}
</style>

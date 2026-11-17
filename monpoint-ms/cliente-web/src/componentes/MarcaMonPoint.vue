<script setup>
import completo from '@/assets/logo-completo.svg'
import simbolo from '@/assets/logo-simbolo.svg'

/**
 * Marca MonPoint en sus dos presentaciones.
 *
 * Son dos archivos y no uno recortado por CSS porque el conjunto completo
 * incluye el nombre dibujado. En la barra lateral ese nombre aparece ademas
 * como texto al lado, asi que el logotipo completo lo repetiria; alli se usa
 * solo el simbolo.
 *
 * Los dos SVG declaran los mismos identificadores de degradado
 * (`navyGradient`, `greenGradient`, `facetGradient`) y tambien `title` y
 * `desc`. Cargados con `<img src>` cada uno es un documento aparte y no se
 * pisan. Si alguna vez se incrustan en linea dentro de un componente, esos
 * identificadores colisionarian y los degradados se mezclarian: habria que
 * renombrarlos antes.
 */
defineProps({ completa: { type: Boolean, default: false } })
</script>

<template>
  <img v-if="completa" :src="completo" class="logo-completo" alt="MonPoint" width="124" height="124" />
  <span v-else class="marca-monpoint">
    <!-- El `alt` va vacio a proposito: el nombre esta en el texto de al lado y
         repetirlo haria que un lector de pantalla lo anunciara dos veces. -->
    <img :src="simbolo" class="logo-compacto" alt="" width="40" height="40" />
    <span>MonPoint <span class="version">v3</span></span>
  </span>
</template>

<style scoped>
.marca-monpoint {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 16px;
  font-weight: 700;
  white-space: nowrap;
}

/* La placa blanca reemplaza a la que el PNG anterior traia pintada dentro. Sin
   ella el hexagono, que es azul marino, se pierde contra la barra lateral, que
   tambien lo es. */
.logo-compacto {
  width: 40px;
  height: 40px;
  box-sizing: border-box;
  padding: 5px;
  border-radius: var(--mp-radio);
  background: #fff;
}

.logo-completo {
  display: block;
  width: 124px;
  height: 124px;
}
</style>

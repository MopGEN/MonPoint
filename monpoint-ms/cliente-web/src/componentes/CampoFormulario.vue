<script setup>
/**
 * Etiqueta, control y error de un campo.
 *
 * El error que recibe viene del mapa `campos` del ProblemDetail que devuelven
 * los servicios ante una validacion fallida, con la clave igual al nombre del
 * campo en el DTO. Por eso el `id` de cada campo coincide con ese nombre: la
 * pantalla no traduce claves, solo las usa.
 *
 * El control se pasa por ranura para no envolver cada tipo de `input`. A cambio
 * la ranura recibe los atributos de accesibilidad ya calculados —`id`,
 * `aria-invalid` y `aria-describedby`— y debe aplicarlos con `v-bind`: si no se
 * aplican, el mensaje de error existe en pantalla pero un lector no lo asocia
 * al campo, que es la mitad del trabajo.
 */
const props = defineProps({
  id: { type: String, required: true },
  etiqueta: { type: String, required: true },
  error: { type: String, default: '' },
  ayuda: { type: String, default: '' },
})

const idError = `${props.id}-error`
const idAyuda = `${props.id}-ayuda`
</script>

<template>
  <div class="campo">
    <label class="mp-etiqueta" :for="id">{{ etiqueta }}</label>

    <slot
      :atributos="{
        id,
        'aria-invalid': error ? 'true' : undefined,
        'aria-describedby': [error ? idError : null, ayuda ? idAyuda : null]
          .filter(Boolean)
          .join(' ') || undefined,
      }"
    />

    <p v-if="ayuda" :id="idAyuda" class="ayuda">{{ ayuda }}</p>
    <p v-if="error" :id="idError" class="error">{{ error }}</p>
  </div>
</template>

<style scoped>
.campo + .campo {
  margin-top: 0.85rem;
}

.ayuda,
.error {
  margin: 0.3rem 0 0;
  font-size: 0.8125rem;
}

.ayuda {
  color: var(--mp-texto-suave);
}

.error {
  color: var(--mp-rojo);
  font-weight: 600;
}
</style>

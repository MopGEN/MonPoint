<script setup>
import { ref, watch, onMounted, onBeforeUnmount, useId } from 'vue'

/**
 * Dialogo modal sobre el elemento nativo `<dialog>`.
 *
 * Se usa `showModal()` y no una capa propia con `div` porque el navegador ya
 * resuelve bien lo que una implementacion casera suele resolver mal: el foco
 * queda atrapado dentro, el resto de la pagina se vuelve inerte para lectores
 * de pantalla, Escape cierra, y al cerrarse el foco vuelve al elemento que lo
 * abrio.
 *
 * NO se cierra al pulsar fuera. Estos dialogos contienen formularios: un clic
 * descuidado en el fondo no debe tirar lo que el usuario acaba de escribir. Se
 * cierra con Escape o con sus botones, que es cuando la intencion es explicita.
 */
const props = defineProps({
  abierto: { type: Boolean, required: true },
  titulo: { type: String, required: true },
  /** Impide cerrar mientras hay una operacion en vuelo. */
  ocupado: { type: Boolean, default: false },
})

const emit = defineEmits(['cerrar'])

const dialogo = ref(null)
const tituloId = useId()

function sincronizar () {
  const el = dialogo.value
  if (!el) return
  if (props.abierto && !el.open) el.showModal()
  if (!props.abierto && el.open) el.close()
}
onMounted(sincronizar)
watch(() => props.abierto, sincronizar, { flush: 'post' })

// Escape dispara `cancel`. Se intercepta para avisar al padre en lugar de
// dejar que el navegador cierre por su cuenta: asi el estado del padre y el
// del dialogo no se separan, y una operacion en vuelo puede impedir el cierre.
function alCancelar (evento) {
  evento.preventDefault()
  if (!props.ocupado) emit('cerrar')
}

// Un dialogo abierto que se desmonta deja el documento bloqueado en algunos
// navegadores. Se cierra a mano.
onBeforeUnmount(() => {
  if (dialogo.value?.open) dialogo.value.close()
})
</script>

<template>
  <dialog ref="dialogo" class="dialogo" :aria-labelledby="tituloId" @cancel="alCancelar">
    <!-- El formulario con method="dialog" no se usa: el envio lo controla el
         padre, que necesita esperar al servidor antes de cerrar. -->
    <header class="cabecera">
      <h2 :id="tituloId">{{ titulo }}</h2>
      <button
        type="button"
        class="cerrar"
        aria-label="Cerrar"
        :disabled="ocupado"
        @click="emit('cerrar')"
      >
        ×
      </button>
    </header>

    <div class="cuerpo">
      <slot />
    </div>

    <footer v-if="$slots.pie" class="pie">
      <slot name="pie" />
    </footer>
  </dialog>
</template>

<style scoped>
.dialogo {
  width: min(32rem, calc(100vw - 2rem));
  padding: 0;
  border: 1px solid var(--mp-borde);
  border-radius: var(--mp-radio);
  background: var(--mp-superficie);
  color: var(--mp-texto);
  box-shadow: var(--mp-sombra);
}

.dialogo::backdrop {
  background: rgb(10 20 34 / 45%);
}

.cabecera {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 0.9rem 1rem;
  border-bottom: 1px solid var(--mp-borde);
}

.cabecera h2 {
  flex: 1;
  margin: 0;
  font-size: 1.125rem;
}

.cerrar {
  width: 2rem;
  height: 2rem;
  border: 0;
  border-radius: var(--mp-radio);
  background: transparent;
  color: var(--mp-texto-suave);
  font-size: 1.4rem;
  line-height: 1;
  cursor: pointer;
}

.cerrar:hover:not(:disabled) {
  background: var(--mp-fondo);
  color: var(--mp-texto);
}

.cuerpo {
  padding: 1rem;
}

.pie {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
  padding: 0.85rem 1rem;
  border-top: 1px solid var(--mp-borde);
  background: var(--mp-fondo);
}
</style>

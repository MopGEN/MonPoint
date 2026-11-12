<script setup>
import { onScopeDispose } from 'vue'
import { gateway } from '@/api/gateway'
import { useAccion } from '@/composables/useAccion'
import { guardarDocumento } from '@/comun/documento'

const props = defineProps({ venta: { type: Object, required: true } })
let control = null
let desmontado = false
const descarga = useAccion(async ({ id, folio }) => {
  control = new AbortController()
  try {
    const blob = await gateway.descargar(`/reportes/ventas/${encodeURIComponent(id)}/ticket`, {
      senal: control.signal,
    })
    if (!desmontado && !control.signal.aborted) guardarDocumento(blob, `ticket-folio-${folio}.pdf`)
  } finally {
    control = null
  }
})

function descargar () {
  if (props.venta.estado !== 'CONFIRMADA' || !props.venta.id) return
  // Se captura la identidad antes de esperar: nombre y contenido son del
  // mismo folio aunque cambie el resultado que muestra Caja.
  return descarga.correr({ id: props.venta.id, folio: props.venta.folio })
}

onScopeDispose(() => {
  desmontado = true
  control?.abort()
})
</script>

<template>
  <div v-if="venta.estado === 'CONFIRMADA' && venta.id" class="ticket">
    <button
      type="button"
      class="mp-boton mp-boton--secundario"
      :disabled="descarga.enCurso.value"
      :aria-label="`Descargar ticket del folio ${venta.folio} (PDF)`"
      @click="descargar"
    >
      {{ descarga.enCurso.value ? 'Generando ticket…' : 'Descargar ticket (PDF)' }}
    </button>
    <p v-if="descarga.error.value" class="mp-aviso mp-aviso--error" role="alert">
      {{ descarga.error.value.message }}
      <span v-if="descarga.error.value.cuerpo?.codigo === 'VENTA_NO_DISPONIBLE'">
        La venta sigue confirmada. Puede volver a pedir el ticket.
      </span>
    </p>
  </div>
</template>

<style scoped>
.ticket { margin: 0.75rem 0; }
.ticket .mp-boton { color: inherit; border-color: currentColor; }
.ticket .mp-boton:hover:not(:disabled) { color: var(--mp-texto); }
.ticket .mp-aviso { margin-bottom: 0; }
</style>

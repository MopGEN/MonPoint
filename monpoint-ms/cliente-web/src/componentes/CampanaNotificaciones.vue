<script setup>
import { computed, useId, watch } from 'vue'
import { gateway } from '@/api/gateway'
import { useRecurso } from '@/composables/useRecurso'
import { useDesplegable } from '@/composables/useDesplegable'
import { revisionAvisos } from '@/composables/actualizacionesInterfaz'
import EstadoRecurso from '@/componentes/EstadoRecurso.vue'
const { zona, boton, abierto } = useDesplegable()
const id = useId()
const recurso = useRecurso(({ senal }) => gateway.obtener('/notificaciones?leida=false', { senal }), { inicial: [] })
const filas = computed(() => Array.isArray(recurso.datos.value) ? recurso.datos.value : [])
const conocido = computed(() => recurso.resuelto.value && !recurso.error.value && !recurso.cargando.value)
const etiqueta = computed(() => conocido.value ? `Notificaciones: ${filas.value.length} sin leer` : 'Notificaciones: consultar avisos')
watch(revisionAvisos, () => recurso.recargar())
function alternar () { abierto.value = !abierto.value; if (abierto.value) recurso.recargar() }
</script>
<template>
  <div ref="zona" class="campana">
    <button ref="boton" type="button" class="icono-cabecera" :aria-label="etiqueta" :aria-expanded="abierto" :aria-controls="id" @click="alternar">
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true"><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4" /></svg>
      <span v-if="conocido && filas.length" class="badge" aria-hidden="true">{{ filas.length }}</span>
    </button>
    <section v-if="abierto" :id="id" class="desplegable avisos" aria-label="Avisos de existencia baja">
      <header><strong>Existencia baja</strong><RouterLink :to="{ name: 'notificaciones' }">Ver todas</RouterLink></header>
      <EstadoRecurso :cargando="recurso.cargando.value" :error="recurso.error.value" :denegado="recurso.denegado.value" :vacio="conocido && filas.length === 0" mensaje-vacio="No hay alertas sin leer." @reintentar="recurso.recargar()">
        <ul><li v-for="n in filas.slice(0, 5)" :key="n.id"><strong>{{ n.productoNombre }}</strong><p>Existencia {{ n.existenciaResultante }}</p><p>{{ n.mensaje }}</p></li></ul>
        <p class="nota">Los avisos se actualizan al abrir este panel o marcar una alerta en Notificaciones.</p>
      </EstadoRecurso>
    </section>
  </div>
</template>
<style scoped>
.campana { position: relative; }
.badge { position: absolute; top: 2px; right: 2px; min-width: 18px; padding: 0 4px; border-radius: 12px; background: var(--mp-error); color: var(--mp-superficie); font-size: 11px; line-height: 18px; text-align: center; }
.avisos { width: 352px; max-height: min(70vh, 520px); overflow-y: auto; }
header { display: flex; justify-content: space-between; gap: 12px; padding: 16px; border-bottom: 1px solid var(--mp-borde-suave); }
ul { margin: 0; padding: 0; list-style: none; }
li { padding: 12px 16px; border-bottom: 1px solid var(--mp-borde-suave); background: var(--mp-aviso-fondo); }
p { margin: 4px 0 0; font-size: 12px; color: var(--mp-texto-suave); overflow-wrap: anywhere; }
.nota { padding: 12px 16px; }
</style>

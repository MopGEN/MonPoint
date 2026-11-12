<script setup>
import { useSesion } from '@/stores/sesion'
import { usePreferencias } from '@/stores/preferencias'
import NavegacionLateral from '@/componentes/NavegacionLateral.vue'
import CabeceraAplicacion from '@/componentes/CabeceraAplicacion.vue'
import MarcaMonPoint from '@/componentes/MarcaMonPoint.vue'
const sesion = useSesion()
const preferencias = usePreferencias()
</script>

<template>
  <div v-if="sesion.autenticado" class="app" :class="{ colapsada: preferencias.colapsada }">
    <aside class="lateral">
      <RouterLink :to="{ name: 'caja' }" class="marca" aria-label="MonPoint" :title="preferencias.colapsada ? 'MonPoint v3' : undefined"><MarcaMonPoint /></RouterLink>
      <NavegacionLateral :colapsada="preferencias.colapsada" />
      <button type="button" class="colapsar" :aria-label="preferencias.colapsada ? 'Expandir barra' : 'Colapsar barra'" :aria-expanded="!preferencias.colapsada" @click="preferencias.colapsada = !preferencias.colapsada">
        <span aria-hidden="true">{{ preferencias.colapsada ? '»' : '«' }}</span><span v-if="!preferencias.colapsada">Colapsar barra</span>
      </button>
    </aside>
    <div class="cuerpo"><CabeceraAplicacion /><main id="contenido" class="contenido"><RouterView /></main></div>
  </div>
  <main v-else class="sin-sesion"><RouterView /></main>
</template>

<style scoped>
.app { display: grid; grid-template-columns: 232px minmax(0, 1fr); min-height: 100dvh; }
.app.colapsada { grid-template-columns: 64px minmax(0, 1fr); }
.lateral { display: flex; flex-direction: column; min-width: 0; position: sticky; top: 0; height: 100dvh; background: var(--mp-barra); color: var(--mp-barra-texto); overflow-y: auto; }
.marca { display: flex; align-items: center; min-height: 64px; padding: 8px 12px; color: var(--mp-barra-texto); text-decoration: none; }
.colapsada .marca :deep(.marca-monpoint > span) { display: none; }
.colapsar { display: flex; align-items: center; justify-content: center; gap: 12px; width: calc(100% - 24px); min-height: 44px; margin: auto 12px 12px; padding: 8px; border: 0; border-radius: var(--mp-radio); background: transparent; color: var(--mp-barra-texto); font-size: 12px; cursor: pointer; }
.colapsar:hover { background: var(--mp-barra-activo); }
.cuerpo { min-width: 0; }
.contenido { min-width: 0; padding: 24px 20px; }
.sin-sesion { min-height: 100dvh; }
@media (max-width: 720px) {
  .app, .app.colapsada { display: flex; flex-direction: column; }
  .lateral { position: static; height: auto; }
  .marca, .colapsar { display: none; }
  .contenido { padding: 20px 16px; }
}
</style>

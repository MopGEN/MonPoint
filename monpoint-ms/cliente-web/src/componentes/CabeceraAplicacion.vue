<script setup>
import { computed, useId, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { gateway } from '@/api/gateway'
import { useSesion } from '@/stores/sesion'
import { usePreferencias } from '@/stores/preferencias'
import { useRecurso } from '@/composables/useRecurso'
import { useDesplegable } from '@/composables/useDesplegable'
import { revisionEmpresa } from '@/composables/actualizacionesInterfaz'
import CampanaNotificaciones from '@/componentes/CampanaNotificaciones.vue'
const sesion = useSesion(), preferencias = usePreferencias()
const ruta = useRoute(), router = useRouter()
const { zona, boton, abierto, cerrar } = useDesplegable()
const id = useId()
const empresa = useRecurso(async ({ senal }) => {
  try { return await gateway.obtener('/reportes/configuracion', { senal }) }
  catch (causa) { if (causa.estado === 404) return null; throw causa }
})
watch(revisionEmpresa, () => empresa.recargar())
const negocio = computed(() => empresa.datos.value?.nombreEmpresa || (empresa.error.value || !empresa.resuelto.value ? 'MonPoint' : 'Auto Tachi'))
const iniciales = computed(() => sesion.nombre.trim().split(/\s+/).slice(0, 2).map((n) => n[0]).join('').toLocaleUpperCase('es-MX'))
function salir () { cerrar(); sesion.cerrar(); router.replace({ name: 'login' }) }
</script>
<template>
  <header class="barra">
    <div class="identidad-pagina">
      <div class="titulo-pagina"><p class="negocio">{{ negocio }}</p><h1>{{ ruta.meta.titulo }}</h1></div>
      <code class="ruta">{{ ruta.path }}</code>
    </div>
    <div class="acciones-cabecera">
      <CampanaNotificaciones v-if="sesion.esAdmin" />
      <button type="button" class="icono-cabecera" :aria-label="preferencias.tema === 'claro' ? 'Cambiar a tema oscuro' : 'Cambiar a tema claro'" @click="preferencias.alternarTema"><span aria-hidden="true">{{ preferencias.tema === 'claro' ? '◐' : '◑' }}</span></button>
      <div ref="zona" class="cuenta-usuario">
        <button ref="boton" type="button" class="usuario" :aria-label="`Menú de usuario: ${sesion.nombre}`" :aria-expanded="abierto" :aria-controls="id" @click="abierto = !abierto">
          <span class="avatar" aria-hidden="true">{{ iniciales }}</span>
          <span class="datos-usuario"><span class="nombre-usuario">{{ sesion.nombre }}</span><span class="rol">{{ sesion.esAdmin ? 'Administrador' : 'Vendedor' }}</span></span>
          <span class="flecha" aria-hidden="true">▾</span>
        </button>
        <section v-if="abierto" :id="id" class="desplegable cuenta" aria-label="Cuenta de usuario">
          <strong>{{ sesion.nombre }}</strong><p>{{ sesion.usuario?.correo }}</p><p class="mp-insignia mp-insignia--apagada">{{ sesion.rol }}</p>
          <button type="button" class="mp-boton mp-boton--secundario salir" @click="salir">Salir</button>
        </section>
      </div>
    </div>
  </header>
</template>
<style scoped>
.barra { position: sticky; top: 0; z-index: 20; display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px; min-height: 60px; padding: 8px 20px; background: var(--mp-superficie); border-bottom: 1px solid var(--mp-borde-suave); }
.identidad-pagina { display: flex; align-items: center; gap: 16px; min-width: 0; flex: 1; }
.titulo-pagina { min-width: 0; }
.negocio { margin: 0; color: var(--mp-texto-auxiliar); text-transform: uppercase; font-size: 10px; font-weight: 700; letter-spacing: .09em; overflow-wrap: anywhere; }
h1 { margin: 0; font-size: 16px; font-weight: 600; }
.ruta { flex-shrink: 0; padding: 3px 8px; border: 1px solid var(--mp-borde-suave); border-radius: 6px; font-family: var(--mp-tipografia-cifras); font-size: 11px; color: var(--mp-texto-auxiliar); }
.acciones-cabecera { display: flex; align-items: center; gap: 8px; margin-left: auto; }
.cuenta-usuario { position: relative; }
.usuario { display: flex; align-items: center; gap: 10px; min-height: 44px; max-width: 280px; border: 1px solid var(--mp-borde-suave); border-radius: var(--mp-radio); padding: 6px 10px; background: var(--mp-superficie); cursor: pointer; }
.usuario:hover { background: var(--mp-superficie-2); }
.avatar { flex-shrink: 0; display: grid; place-items: center; width: 30px; height: 30px; border-radius: 50%; background: var(--mp-marino-suave); color: var(--mp-texto); font-size: 12px; font-weight: 700; }
.datos-usuario { line-height: 1.2; display: flex; flex-direction: column; min-width: 0; text-align: left; }
.nombre-usuario { font-size: 13px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.rol { font-size: 10px; font-weight: 700; letter-spacing: .08em; text-transform: uppercase; color: var(--mp-texto-auxiliar); }
.flecha { color: var(--mp-texto-auxiliar); }
.cuenta { width: 300px; padding: 16px; }
.cuenta p { color: var(--mp-texto-suave); overflow-wrap: anywhere; }
.salir { display: flex; width: 100%; margin-top: 12px; }
@media (max-width: 720px) {
  .barra { padding: 12px 16px; }
  .identidad-pagina { flex-basis: 100%; }
  .usuario { max-width: 240px; }
}
</style>

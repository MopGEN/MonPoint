<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useSesion } from '@/stores/sesion'
import { rolPermitido } from '@/router'

/**
 * Barra de navegacion derivada del propio enrutador.
 *
 * No hay una lista paralela de enlaces. Las entradas salen de las rutas que
 * declaran `meta.nav`, y se filtran con `rolPermitido`, la misma funcion que
 * usa la guarda. De ahi se siguen dos propiedades que conviene no perder:
 *
 * - Una entrada no puede apuntar a una ruta que no existe, porque sale de la
 *   tabla de rutas.
 * - Una entrada no puede ofrecer algo que la guarda rechazaria, porque ambas
 *   preguntan a la misma funcion.
 *
 * Las pantallas del prototipo que no existen todavia simplemente no aparecen.
 * Se descarto mostrarlas deshabilitadas: un enlace que nunca se activa ocupa
 * espacio, invita a pulsarlo y no ensena nada que el usuario pueda usar.
 */
defineProps({ colapsada: { type: Boolean, default: false } })
const router = useRouter()
const sesion = useSesion()

const entradas = computed(() =>
  router
    .getRoutes()
    .filter((r) => r.meta?.nav && r.name)
    .filter((r) => rolPermitido(r.meta, sesion.rol))
    .sort((a, b) => (a.meta.nav.orden ?? 0) - (b.meta.nav.orden ?? 0))
    .map((r) => ({
      nombre: r.name,
      titulo: r.meta.titulo ?? String(r.name),
      icono: r.meta.nav.icono ?? '·',
    })),
)
</script>

<template>
  <nav class="navegacion" :class="{ compacta: colapsada }" aria-label="Secciones">
    <ul>
      <li v-for="e in entradas" :key="e.nombre">
        <RouterLink :to="{ name: e.nombre }" class="enlace" active-class="activo" :aria-label="e.titulo" :title="colapsada ? e.titulo : undefined">
          <!-- El icono es decorativo: el nombre de la seccion ya esta en el
               texto, asi que anunciarlo seria ruido para un lector. -->
          <span class="icono" aria-hidden="true">{{ e.icono }}</span>
          <span class="texto">{{ e.titulo }}</span>
        </RouterLink>
      </li>
    </ul>
  </nav>
</template>

<style scoped>
.navegacion {
  padding: 8px 12px;
}

ul {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.enlace {
  display: flex;
  align-items: center;
  gap: 0.7rem;
  min-height: 44px;
  padding: 0 12px;
  border-radius: var(--mp-radio);
  color: var(--mp-barra-texto);
  text-decoration: none;
  font-weight: 400;
}

.enlace:hover {
  background: var(--mp-barra-activo);
  color: var(--mp-barra-texto);
}

/* aria-current identifica la ruta activa; el peso tipografico tambien cambia. */
.enlace.activo {
  background: var(--mp-barra-activo);
  color: #8fe3c0;
  font-weight: 600;

}

.icono {
  width: 1.25rem;
  text-align: center;
  font-size: 1.05rem;
}

.compacta .texto { display: none; }
.compacta .enlace { justify-content: center; padding: 0; }

/* En pantalla estrecha la barra pasa a ser una fila desplazable sobre el
   contenido, que es lo unico que cabe sin robarle sitio a una tabla. */
@media (max-width: 720px) {
  .navegacion {
    padding: 0.5rem;
    overflow-x: auto;
  }

  ul {
    flex-direction: row;
  }

  .texto, .compacta .texto { display: inline; white-space: nowrap; }
  .compacta .enlace { justify-content: flex-start; padding: 0 12px; }

  .enlace.activo {
    box-shadow: inset 0 -3px 0 var(--mp-verde);
  }
}
</style>

<script setup>
import { useRouter } from 'vue-router'
import { useSesion } from '@/stores/sesion'
import NavegacionLateral from '@/componentes/NavegacionLateral.vue'

const sesion = useSesion()
const router = useRouter()

function salir () {
  sesion.cerrar()
  router.replace({ name: 'login' })
}
</script>

<template>
  <div class="app">
    <!-- La barra solo existe con sesion abierta: en la pantalla de inicio de
         sesion no hay nada que mostrar en ella. -->
    <header v-if="sesion.autenticado" class="barra">
      <span class="marca">MonPoint</span>

      <span class="usuario">
        {{ sesion.nombre }}
        <span class="rol">{{ sesion.rol }}</span>
      </span>

      <button type="button" class="mp-boton mp-boton--secundario salir" @click="salir">
        Salir
      </button>
    </header>

    <!-- Con sesion abierta la pagina se parte en barra lateral y contenido.
         Sin ella no hay nada que navegar, asi que el inicio de sesion ocupa el
         ancho completo. -->
    <div class="cuerpo" :class="{ 'con-barra': sesion.autenticado }">
      <aside v-if="sesion.autenticado" class="lateral">
        <NavegacionLateral />
      </aside>

      <main class="contenido">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<style scoped>
.app {
  display: flex;
  flex-direction: column;
  min-height: 100%;
}

.barra {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 0.75rem 1rem;
  background: var(--mp-marino);
  color: #fff;
}

.marca {
  font-weight: 700;
  letter-spacing: 0.02em;
}

.usuario {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.9375rem;
}

.rol {
  padding: 0.1rem 0.5rem;
  border-radius: 999px;
  background: rgb(255 255 255 / 15%);
  font-size: 0.75rem;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.salir {
  color: #fff;
  border-color: rgb(255 255 255 / 30%);
}

.salir:hover:not(:disabled) {
  background: rgb(255 255 255 / 12%);
}

.cuerpo {
  flex: 1;
  display: flex;
  min-height: 0;
}

.lateral {
  flex: 0 0 13.5rem;
  border-right: 1px solid var(--mp-borde);
  background: var(--mp-superficie);
}

.contenido {
  flex: 1;
  /* min-width: 0 permite que una tabla ancha se desplace dentro de su
     envoltura en vez de ensanchar la pagina entera y sacar la barra lateral
     de la pantalla. Sin esto, un nombre de producto largo rompe el diseno. */
  min-width: 0;
  padding: 1.25rem 1rem;
}

/* Por debajo de 720px la barra deja de ser columna y pasa a ser una fila bajo
   la cabecera, que es lo que hace la propia barra en su media query. */
@media (max-width: 720px) {
  .cuerpo.con-barra {
    flex-direction: column;
  }

  .lateral {
    flex: none;
    border-right: 0;
    border-bottom: 1px solid var(--mp-borde);
  }
}

@media (max-width: 480px) {
  .contenido {
    padding: 1rem 16px;
  }
}
</style>

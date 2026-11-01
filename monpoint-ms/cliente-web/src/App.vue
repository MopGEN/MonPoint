<script setup>
import { useRouter } from 'vue-router'
import { useSesion } from '@/stores/sesion'

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

    <main class="contenido">
      <RouterView />
    </main>
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

.contenido {
  flex: 1;
  padding: 1.25rem 1rem;
}

@media (max-width: 480px) {
  .contenido {
    padding: 1rem 16px;
  }
}
</style>

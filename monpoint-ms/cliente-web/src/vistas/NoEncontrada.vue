<script setup>
import { useSesion } from '@/stores/sesion'

const sesion = useSesion()
</script>

<template>
  <section class="mp-tarjeta panel">
    <h1>Pagina no encontrada</h1>
    <p class="nota">La direccion que abrio no corresponde a ninguna pantalla.</p>
    <RouterLink class="mp-boton" :to="sesion.autenticado ? '/caja' : '/login'">
      {{ sesion.autenticado ? 'Ir a caja' : 'Iniciar sesion' }}
    </RouterLink>
  </section>
</template>

<style scoped>
.panel {
  max-width: 480px;
  padding: 1.5rem;
}

.nota {
  margin: 0 0 1.25rem;
  color: var(--mp-texto-suave);
}

a.mp-boton {
  text-decoration: none;
}
</style>

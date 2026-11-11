<script setup>
/**
 * Envoltura de los cuatro estados de una pantalla de listado: cargando, error,
 * sin permiso y vacio. El contenido solo se muestra cuando hay datos.
 *
 * Existe para que las cinco pantallas no escriban cinco veces la misma cadena
 * de `v-if`, que es donde se cuela el caso olvidado: tipicamente mostrar "no
 * hay registros" cuando en realidad la peticion fallo.
 *
 * La distincion entre error y denegado es deliberada: ante un 403 no se ofrece
 * reintentar, porque volver a pedir lo mismo con el mismo rol da el mismo 403 y
 * el boton solo sirve para que el usuario se canse.
 */
defineProps({
  cargando: { type: Boolean, default: false },
  /** Instancia de ErrorDeGateway, o null. */
  error: { type: Object, default: null },
  denegado: { type: Boolean, default: false },
  /** true cuando la carga termino y no hay nada que mostrar. */
  vacio: { type: Boolean, default: false },
  mensajeVacio: { type: String, default: 'No hay registros que mostrar.' },
})

defineEmits(['reintentar'])
</script>

<template>
  <!-- aria-busy informa del cargando a los lectores de pantalla sin depender
       del texto, que puede quedar fuera de la vista. -->
  <div class="estado" :aria-busy="cargando">
    <p v-if="cargando" class="cargando" role="status">Cargando…</p>

    <div v-else-if="denegado" class="mp-aviso mp-aviso--error" role="alert">
      <strong>Sin permiso.</strong>
      {{ error?.message ?? 'Su rol no permite consultar esta informacion.' }}
    </div>

    <div v-else-if="error" class="mp-aviso mp-aviso--error" role="alert">
      <p class="linea">{{ error.message }}</p>
      <button type="button" class="mp-boton mp-boton--secundario" @click="$emit('reintentar')">
        Volver a intentar
      </button>
    </div>

    <p v-else-if="vacio" class="vacio">{{ mensajeVacio }}</p>

    <slot v-else />
  </div>
</template>

<style scoped>
.cargando,
.vacio {
  margin: 0;
  padding: 2rem 0;
  text-align: center;
  color: var(--mp-texto-suave);
}

.linea {
  margin: 0 0 0.6rem;
}
</style>

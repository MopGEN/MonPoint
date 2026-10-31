<script setup>
/**
 * Pantalla de inicio de sesion.
 *
 * ALCANCE DE ESTE ARCHIVO EN LA TAREA 5.1
 * Esta version es minima y FUNCIONAL a proposito: el criterio de aceptacion del
 * andamiaje es poder recorrer el circuito completo (token -> guarda -> peticion
 * autenticada), y para eso hace falta poder iniciar sesion de verdad. Lo que
 * hay aqui es la conexion con el almacen y el manejo de error.
 *
 * LO QUE AGREGA LA TAREA 5.3 (H-078, H-079)
 *   - Validacion en el cliente antes de enviar (formato de correo, campos
 *     vacios) con mensajes por campo.
 *   - Presentacion final segun el prototipo.
 *   - Mensaje diferenciado para credenciales incorrectas frente a servicio
 *     caido: el cliente HTTP ya distingue ambos casos
 *     (ErrorDeGateway.esDeRed), aqui solo se muestran igual.
 *   - Bloqueo o espera tras intentos fallidos repetidos, si se decide incluirlo.
 */
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useSesion } from '@/stores/sesion'

const sesion = useSesion()
const router = useRouter()
const route = useRoute()

const correo = ref('')
const contrasena = ref('')
const error = ref('')

// El enrutador anade ?expirada=1 cuando el Gateway rechazo el token. Sin este
// aviso, al cajero se le vaciaria la pantalla sin explicacion.
const expirada = route.query.expirada === '1'

async function enviar () {
  error.value = ''
  try {
    await sesion.iniciar(correo.value.trim(), contrasena.value)
    const regresarA = route.query.regresarA
    await router.replace(typeof regresarA === 'string' ? regresarA : { name: 'caja' })
  } catch (e) {
    error.value = e?.message ?? 'No fue posible iniciar sesion.'
    contrasena.value = ''
  }
}
</script>

<template>
  <div class="envoltura">
    <form class="mp-tarjeta tarjeta" @submit.prevent="enviar">
      <h1>MonPoint</h1>
      <p class="subtitulo">Punto de venta</p>

      <p v-if="expirada && !error" class="mp-aviso mp-aviso--pendiente">
        Su sesion termino. Inicie sesion de nuevo.
      </p>

      <!-- role="alert" para que el lector de pantalla anuncie el fallo sin que
           el cajero tenga que buscarlo. -->
      <p v-if="error" class="mp-aviso mp-aviso--error" role="alert">
        {{ error }}
      </p>

      <div class="grupo">
        <label class="mp-etiqueta" for="correo">Correo</label>
        <input
          id="correo"
          v-model="correo"
          class="mp-campo"
          type="email"
          name="correo"
          autocomplete="username"
          required
          autofocus
        />
      </div>

      <div class="grupo">
        <label class="mp-etiqueta" for="contrasena">Contrasena</label>
        <input
          id="contrasena"
          v-model="contrasena"
          class="mp-campo"
          type="password"
          name="contrasena"
          autocomplete="current-password"
          required
        />
      </div>

      <button type="submit" class="mp-boton ancho" :disabled="sesion.entrando">
        {{ sesion.entrando ? 'Entrando...' : 'Entrar' }}
      </button>
    </form>
  </div>
</template>

<style scoped>
.envoltura {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 70vh;
}

.tarjeta {
  width: 100%;
  max-width: 380px;
  padding: 1.75rem;
}

.subtitulo {
  margin: 0 0 1.25rem;
  color: var(--mp-texto-suave);
}

.grupo {
  margin-bottom: 1rem;
}

.ancho {
  width: 100%;
  margin-top: 0.5rem;
}
</style>

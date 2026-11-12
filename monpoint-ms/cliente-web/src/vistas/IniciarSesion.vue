<script setup>
/** Inicio de sesion real, marca local y preferencia visual compartida.
 * La autenticacion conserva el token en memoria; no se precargan credenciales.
 */
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useSesion } from '@/stores/sesion'
import { usePreferencias } from '@/stores/preferencias'
import MarcaMonPoint from '@/componentes/MarcaMonPoint.vue'

const sesion = useSesion()
const preferencias = usePreferencias()
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
    <button type="button" class="icono-cabecera tema-login" :aria-label="preferencias.tema === 'claro' ? 'Cambiar a tema oscuro' : 'Cambiar a tema claro'" @click="preferencias.alternarTema"><span aria-hidden="true">{{ preferencias.tema === 'claro' ? '◐' : '◑' }}</span></button>
    <form class="mp-tarjeta tarjeta" @submit.prevent="enviar">
      <MarcaMonPoint completa />
      <h1>Iniciar sesión</h1>
      <p class="subtitulo">Punto de venta · Auto Tachi Refacciones y Accesorios</p>

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
        <label class="mp-etiqueta" for="contrasena">Contraseña</label>
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
      <p class="nota-sesion">Por seguridad, la sesión se cierra al recargar la página.</p>
    </form>
  </div>
</template>

<style scoped>
.envoltura {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100dvh;
  padding: 76px 16px 32px;
}

.tarjeta {
  width: 100%;
  max-width: 432px;
  padding: 32px;
}

.tarjeta :deep(.logo-completo) { margin: 0 auto 24px; }
h1 { margin-bottom: 8px; font-size: 24px; font-weight: 600; text-align: center; }
.tema-login { position: absolute; top: 20px; right: 20px; }

.subtitulo {
  margin: 0 0 1.25rem;
  color: var(--mp-texto-suave);
  font-size: 13px;
  text-align: center;
}

.grupo {
  margin-bottom: 1rem;
}

.ancho {
  width: 100%;
  margin-top: 0.5rem;
  min-height: 52px;
}
.nota-sesion { margin: 20px 0 0; color: var(--mp-texto-auxiliar); font-size: 12px; text-align: center; }
</style>

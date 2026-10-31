<script setup>
/**
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  PANTALLA DE CAJA — ESQUELETO. EL CONTENIDO ES LA TAREA 5.5.             ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * Este archivo NO implementa la caja. Deja el contrato escrito para que la 5.5
 * se construya sin volver a leer el codigo de los servicios, y una sonda de
 * conexion que valida el andamiaje (criterio de aceptacion de la 5.1).
 *
 * ── CONTRATO CON EL GATEWAY ────────────────────────────────────────────────
 * Todas las rutas pasan por el Gateway; los servicios no se exponen al
 * navegador. Prefijos enrutados: /auth, /inventario, /ventas, /clientes,
 * /reportes, /notificaciones.
 *
 * REGISTRAR UNA VENTA
 *   gateway.enviar('/ventas', { clienteId, lineas: [{ productoId, cantidad }] })
 *
 *   - `clienteId` es opcional: omitirlo registra una venta sin cliente. Si se
 *     envia un identificador inexistente en la replica, el servicio responde
 *     400 con el mensaje de cliente no disponible todavia, que el cliente HTTP
 *     ya entrega listo para mostrar en ErrorDeGateway.message.
 *   - NO se envia `total` ni `subtotal`: el servidor los calcula. Enviarlos no
 *     da error, simplemente se ignoran. El importe que se muestre al cajero
 *     debe ser el que devuelve el servidor, nunca el calculado en el navegador.
 *   - La respuesta es 202 con la venta en estado PENDIENTE y su `folio`. NO es
 *     una confirmacion: significa que la venta se acepto y la saga arranco.
 *
 * SABER COMO TERMINO LA VENTA
 *   La resolucion a CONFIRMADA o CANCELADA llega por mensajeria entre
 *   servicios, no por la respuesta HTTP. El navegador la conoce consultando:
 *
 *     gateway.obtener(`/ventas/${id}`)
 *
 *   ⚠ ESE ENDPOINT NO EXISTE AUN. Es la tarea 5.2 y es prerrequisito de esta
 *   pantalla. Sin el, la caja puede registrar ventas pero no puede decirle al
 *   cajero si se confirmaron. Orden obligado: 5.2 antes de 5.5.
 *
 *   Al consultar, `estado` vale PENDIENTE, CONFIRMADA o CANCELADA. En
 *   CANCELADA, `motivo` trae un codigo del catalogo:
 *     PRODUCTO_NO_ENCONTRADO · PRODUCTO_INACTIVO · EXISTENCIA_INSUFICIENTE
 *   Hay que traducirlo a lenguaje de caja; mostrar el codigo crudo no sirve a
 *   quien esta cobrando. Para EXISTENCIA_INSUFICIENTE el mensaje acordado usa
 *   el nombre congelado del producto y la cantidad solicitada: la existencia
 *   disponible todavia no viaja en el evento (pendiente de ms-inventario).
 *
 * ── DECISIONES QUE LA 5.5 TIENE QUE TOMAR ──────────────────────────────────
 *   1. Como se espera la resolucion: sondeo con intervalo y tope, o accion
 *      explicita del cajero. Un sondeo sin tope deja peticiones corriendo para
 *      siempre si la saga se atasca.
 *   2. Que se muestra mientras esta PENDIENTE. La caja no puede quedarse en
 *      blanco ni dar por buena una venta sin confirmar.
 *   3. Que pasa si el navegador se queda sin respuesta tras el 202: la venta ya
 *      existe en el servidor. Hay que poder recuperarla por folio, no
 *      registrarla otra vez.
 */
import { ref } from 'vue'
import { gateway, ErrorDeGateway } from '@/api/gateway'
import { useSesion } from '@/stores/sesion'

const sesion = useSesion()

const sonda = ref(null)
const sondeando = ref(false)

/**
 * Sonda del andamiaje. Es temporal: la 5.5 la borra.
 *
 * Diagnostico segun lo que devuelva:
 *   200/404 → el andamiaje funciona. El token se adjunto y el Gateway enruto;
 *             un 404 solo dice que esa ruta concreta no existe en el servicio.
 *   401     → el token NO esta llegando. Revisar el filtro del Gateway o la
 *             inyeccion de configurarGateway en main.js.
 *   0       → no hay contacto: infraestructura caida, VITE_GATEWAY_URL mal, o
 *             CORS rechazado por el Gateway (mirar la consola del navegador).
 */
async function probarConexion () {
  sondeando.value = true
  sonda.value = null
  try {
    const datos = await gateway.obtener('/inventario/productos')
    sonda.value = {
      clase: 'exito',
      texto: `Conexion correcta. El Gateway respondio y el token se adjunto. Elementos recibidos: ${
        Array.isArray(datos) ? datos.length : 'respuesta no es una lista'
      }.`,
    }
  } catch (e) {
    const estado = e instanceof ErrorDeGateway ? e.estado : -1
    if (estado === 404) {
      sonda.value = {
        clase: 'pendiente',
        texto:
          'El andamiaje funciona: el Gateway enruto y acepto el token. La ruta ' +
          '/inventario/productos no existe con ese nombre; ajustar al endpoint real.',
      }
    } else if (estado === 401 || estado === 403) {
      sonda.value = {
        clase: 'error',
        texto: `El Gateway rechazo la credencial (${estado}). El token no esta llegando o el rol no alcanza.`,
      }
    } else {
      sonda.value = { clase: 'error', texto: e?.message ?? 'Fallo desconocido.' }
    }
  } finally {
    sondeando.value = false
  }
}
</script>

<template>
  <section class="mp-tarjeta panel">
    <h1>Caja</h1>
    <p class="nota">
      Pantalla pendiente de construir (tarea 5.5). El andamiaje de la 5.1 esta
      en su lugar: sesion en memoria, guarda de rutas y cliente HTTP con token.
    </p>

    <dl class="datos">
      <dt>Usuario</dt>
      <dd>{{ sesion.usuario?.correo }}</dd>
      <dt>Rol</dt>
      <dd>{{ sesion.rol }}</dd>
      <dt>Gateway</dt>
      <dd class="mp-cifra">{{ gateway.base }}</dd>
    </dl>

    <button type="button" class="mp-boton" :disabled="sondeando" @click="probarConexion">
      {{ sondeando ? 'Probando...' : 'Probar conexion con el Gateway' }}
    </button>

    <p
      v-if="sonda"
      class="mp-aviso resultado"
      :class="`mp-aviso--${sonda.clase}`"
      role="status"
    >
      {{ sonda.texto }}
    </p>
  </section>
</template>

<style scoped>
.panel {
  max-width: 640px;
  padding: 1.5rem;
}

.nota {
  margin: 0 0 1.25rem;
  color: var(--mp-texto-suave);
}

.datos {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 0.35rem 1rem;
  margin: 0 0 1.25rem;
  font-size: 0.9375rem;
}

.datos dt {
  color: var(--mp-texto-suave);
  font-weight: 600;
}

.datos dd {
  margin: 0;
  overflow-wrap: anywhere;
}

.resultado {
  margin-top: 1rem;
}
</style>

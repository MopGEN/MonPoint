<script setup>
import { computed, reactive, ref, nextTick } from 'vue'
import { gateway } from '@/api/gateway'
import { useSesion } from '@/stores/sesion'
import { useRecurso } from '@/composables/useRecurso'
import { useAccion } from '@/composables/useAccion'
import EstadoRecurso from '@/componentes/EstadoRecurso.vue'
import TablaDatos from '@/componentes/TablaDatos.vue'
import DialogoModal from '@/componentes/DialogoModal.vue'
import CampoFormulario from '@/componentes/CampoFormulario.vue'
import {
  REGLAS_PASSWORD,
  ROLES,
  aPeticion,
  desdeUsuario,
  erroresDeCausa,
  esUnoMismo,
  filtrar,
  validar,
  vacio,
} from '@/usuarios/formulario'

/**
 * Usuarios (ms-auth, H-011 a H-013). Solo ADMIN.
 *
 * Tres reglas del servicio moldean la pantalla y conviene no descubrirlas a
 * base de errores:
 *
 * 1. El listado devuelve SOLO usuarios activos y no existe endpoint para
 *    reactivar. Desactivar es, desde la interfaz, irreversible: la cuenta
 *    desaparece y no hay forma de traerla de vuelta. El dialogo lo dice.
 * 2. Un ADMIN no puede desactivar su propia cuenta. El boton no aparece en la
 *    fila propia, con su motivo a la vista.
 * 3. Nadie puede cambiar su propio rol. El selector queda deshabilitado al
 *    editarse a uno mismo.
 *
 * AuthResponse no trae id, pero GET /auth/me si. Se usa ese id estable para
 * reconocer la fila propia aunque su correo cambie durante la sesion.
 */

const sesion = useSesion()
const idPropio = ref(null)

const COLUMNAS = [
  { clave: 'nombre', titulo: 'Usuario' },
  { clave: 'rol', titulo: 'Rol', ancho: '9rem' },
  { clave: 'acciones', titulo: '', ancho: '13rem' },
]

const recurso = useRecurso(async ({ senal }) => {
  const [lista, propio] = await Promise.all([
    gateway.obtener('/auth/usuarios', { senal }),
    gateway.obtener('/auth/me', { senal }),
  ])
  idPropio.value = propio.id
  return lista
}, {
  inicial: [],
})

const consulta = ref('')
const todos = computed(() => (Array.isArray(recurso.datos.value) ? recurso.datos.value : []))
const filas = computed(() => filtrar(todos.value, consulta.value))
const administradores = computed(() => todos.value.filter((u) => u.rol === 'ADMIN').length)

// --- alta y edicion -------------------------------------------------------

const dialogo = ref(null) // null | 'alta' | 'edicion'
const editando = ref(null)
const forma = reactive(vacio())

const esEdicion = computed(() => dialogo.value === 'edicion')
const editandoseASiMismo = computed(
  () => esEdicion.value && esUnoMismo(editando.value, idPropio.value),
)

const guardado = useAccion((id, cuerpo) =>
  id ? gateway.reemplazar(`/auth/usuarios/${id}`, cuerpo) : gateway.enviar('/auth/register', cuerpo),
)

function abrirAlta () {
  Object.assign(forma, vacio())
  guardado.limpiar()
  editando.value = null
  dialogo.value = 'alta'
}

function abrirEdicion (usuario) {
  Object.assign(forma, desdeUsuario(usuario))
  guardado.limpiar()
  editando.value = usuario
  dialogo.value = 'edicion'
}

function cerrarDialogo () {
  if (guardado.enCurso.value) return
  forma.password = ''
  dialogo.value = null
  editando.value = null
}

async function guardar () {
  if (guardado.enCurso.value) return

  const encontrados = validar(forma, { edicion: esEdicion.value })
  if (Object.keys(encontrados).length > 0) {
    guardado.fijarCampos(encontrados)
    await nextTick()
    document.getElementById(Object.keys(encontrados)[0])?.focus()
    return
  }

  const cuerpo = aPeticion(forma, {
    edicion: esEdicion.value,
    rolOriginal: editando.value?.rol ?? null,
  })
  const r = await guardado.correr(editando.value?.id ?? null, cuerpo)
  if (!r.ok) {
    // ms-auth reporta la politica de contrasena como lista y el correo
    // repetido como 409 sin campos: se traducen a campos aqui.
    const porCampo = erroresDeCausa(r.causa)
    if (porCampo) guardado.fijarCampos(porCampo)
    return
  }
  // Actualizar los datos visibles, sin modificar el rol del token ni simular
  // una renovacion de sesion. La fila propia se identifica por /auth/me.
  if (editandoseASiMismo.value && sesion.usuario) {
    sesion.usuario.nombre = r.valor.nombre
    sesion.usuario.correo = r.valor.correo
  }
  forma.password = ''
  dialogo.value = null
  editando.value = null
  await recurso.recargar()
}

// --- desactivacion --------------------------------------------------------

const desactivando = ref(null)
const baja = useAccion((id) => gateway.modificar(`/auth/usuarios/${id}/desactivar`))

function pedirBaja (usuario) {
  baja.limpiar()
  desactivando.value = usuario
}

async function confirmarBaja () {
  if (!desactivando.value) return
  const r = await baja.correr(desactivando.value.id)
  if (!r.ok) return
  desactivando.value = null
  await recurso.recargar()
}
</script>

<template>
  <section class="pantalla">
    <header class="encabezado">
      <div>
        <p class="seccion">ACCESO</p>
        <h1>Usuarios</h1>
        <p class="descripcion">
          Cuentas activas del sistema. Una cuenta desactivada no vuelve a aparecer aquí.
        </p>
      </div>

      <button type="button" class="mp-boton" @click="abrirAlta">Nuevo usuario</button>
    </header>

    <div class="controles">
      <label class="mp-solo-lectores" for="buscar">Buscar por nombre, correo o rol</label>
      <input
        id="buscar"
        v-model="consulta"
        class="mp-campo buscar"
        type="search"
        placeholder="Buscar por nombre, correo o rol"
      />
      <p class="cuenta" aria-live="polite">
        {{ filas.length }} de {{ todos.length }} · {{ administradores }}
        {{ administradores === 1 ? 'administrador' : 'administradores' }}
      </p>
    </div>

    <div class="mp-tarjeta tabla">
      <EstadoRecurso
        :cargando="recurso.cargando.value"
        :error="recurso.error.value"
        :denegado="recurso.denegado.value"
        :vacio="recurso.resuelto.value && filas.length === 0"
        :mensaje-vacio="
          consulta.trim() === ''
            ? 'No hay cuentas activas.'
            : 'Ninguna cuenta activa coincide con la búsqueda.'
        "
        @reintentar="recurso.recargar()"
      >
        <TablaDatos
          :columnas="COLUMNAS"
          :filas="filas"
          descripcion="Cuentas activas, con su correo y rol"
        >
          <template #celda-nombre="{ fila }">
            <span class="nombre">
              {{ fila.nombre }}
              <span v-if="esUnoMismo(fila, idPropio)" class="mp-insignia mp-insignia--apagada">
                usted
              </span>
            </span>
            <span class="detalle">{{ fila.correo }}</span>
          </template>

          <template #celda-rol="{ fila }">
            <span class="mp-insignia" :class="{ 'mp-insignia--apagada': fila.rol !== 'ADMIN' }">
              {{ fila.rol === 'ADMIN' ? 'Administrador' : 'Vendedor' }}
            </span>
          </template>

          <template #celda-acciones="{ fila }">
            <div class="acciones">
              <button type="button" class="mp-boton mp-boton--secundario" @click="abrirEdicion(fila)">
                Editar
              </button>
              <!-- El servicio rechaza que un ADMIN se desactive a si mismo, asi
                   que en lugar de ofrecer un boton que terminaria en error se
                   explica por que no esta. -->
              <button
                v-if="!esUnoMismo(fila, idPropio)"
                type="button"
                class="mp-boton mp-boton--secundario"
                @click="pedirBaja(fila)"
              >
                Desactivar
              </button>
              <span v-else class="nota">Su propia cuenta</span>
            </div>
          </template>
        </TablaDatos>
      </EstadoRecurso>
    </div>

    <DialogoModal
      :abierto="dialogo !== null"
      :ocupado="guardado.enCurso.value"
      :titulo="esEdicion ? 'Editar usuario' : 'Nuevo usuario'"
      @cerrar="cerrarDialogo"
    >
      <form id="forma-usuario" novalidate @submit.prevent="guardar">
        <div v-if="guardado.error.value" class="mp-aviso mp-aviso--error" role="alert">
          {{ guardado.error.value.message }}
        </div>

        <fieldset class="campos" :disabled="guardado.enCurso.value">
          <CampoFormulario id="nombre" etiqueta="Nombre" :error="guardado.campos.value.nombre">
            <template #default="{ atributos }">
              <input v-bind="atributos" v-model="forma.nombre" class="mp-campo" type="text" />
            </template>
          </CampoFormulario>

          <CampoFormulario id="correo" etiqueta="Correo" :error="guardado.campos.value.correo">
            <template #default="{ atributos }">
              <input
                v-bind="atributos"
                v-model="forma.correo"
                class="mp-campo"
                type="email"
                autocomplete="off"
              />
            </template>
          </CampoFormulario>

          <CampoFormulario
            id="password"
            :etiqueta="esEdicion ? 'Contraseña nueva (opcional)' : 'Contraseña'"
            :error="guardado.campos.value.password"
            :ayuda="
              esEdicion
                ? `Déjela vacía para conservar la actual. ${REGLAS_PASSWORD}`
                : REGLAS_PASSWORD
            "
          >
            <template #default="{ atributos }">
              <!-- new-password evita que el gestor del navegador rellene aqui
                   las credenciales de quien esta usando la pantalla. -->
              <input
                v-bind="atributos"
                v-model="forma.password"
                class="mp-campo"
                type="password"
                autocomplete="new-password"
              />
            </template>
          </CampoFormulario>

          <CampoFormulario
            id="rol"
            etiqueta="Rol"
            :error="guardado.campos.value.rol"
            :ayuda="editandoseASiMismo ? 'Nadie puede cambiar su propio rol.' : esEdicion ? 'El cambio de rol se refleja al volver a iniciar sesión.' : ''"
          >
            <template #default="{ atributos }">
              <select
                v-bind="atributos"
                v-model="forma.rol"
                class="mp-campo"
                :disabled="editandoseASiMismo"
              >
                <option v-for="r in ROLES" :key="r.valor" :value="r.valor">{{ r.etiqueta }}</option>
              </select>
            </template>
          </CampoFormulario>
        </fieldset>
      </form>

      <template #pie>
        <button
          type="button"
          class="mp-boton mp-boton--secundario"
          :disabled="guardado.enCurso.value"
          @click="cerrarDialogo"
        >
          Cancelar
        </button>
        <button
          type="submit"
          form="forma-usuario"
          class="mp-boton"
          :disabled="guardado.enCurso.value"
        >
          {{ guardado.enCurso.value ? 'Guardando…' : 'Guardar' }}
        </button>
      </template>
    </DialogoModal>

    <DialogoModal
      :abierto="desactivando !== null"
      :ocupado="baja.enCurso.value"
      titulo="Desactivar usuario"
      @cerrar="desactivando = null"
    >
      <div v-if="baja.error.value" class="mp-aviso mp-aviso--error" role="alert">
        {{ baja.error.value.message }}
      </div>

      <p class="confirmacion">
        <strong>{{ desactivando?.nombre }}</strong> no podrá iniciar sesión y desaparecerá de esta
        lista.
      </p>
      <p class="detalle-baja">
        <strong>No se puede deshacer desde la aplicación.</strong> El listado solo muestra cuentas
        activas y no hay una opción para reactivar esta cuenta.
      </p>
      <p class="detalle-baja">Una sesión ya abierta puede seguir activa hasta su vencimiento.</p>
      <p
        v-if="desactivando?.rol === 'ADMIN' && administradores <= 2"
        class="mp-aviso mp-aviso--pendiente"
        role="status"
      >
        Quedarían {{ administradores - 1 }}
        {{ administradores - 1 === 1 ? 'administrador' : 'administradores' }} en el sistema.
      </p>

      <template #pie>
        <button
          type="button"
          class="mp-boton mp-boton--secundario"
          :disabled="baja.enCurso.value"
          @click="desactivando = null"
        >
          Cancelar
        </button>
        <button type="button" class="mp-boton peligro" :disabled="baja.enCurso.value" @click="confirmarBaja">
          {{ baja.enCurso.value ? 'Desactivando…' : 'Desactivar' }}
        </button>
      </template>
    </DialogoModal>
  </section>
</template>

<style scoped>
.pantalla {
  max-width: 68rem;
  margin: 0 auto;
}

.encabezado {
  display: flex;
  align-items: flex-start;
  gap: 1rem;
  margin-bottom: 1rem;
}

.encabezado > div {
  flex: 1;
  min-width: 0;
}

.seccion {
  margin: 0 0 0.15rem;
  font-size: 0.75rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: var(--mp-verde);
}

.descripcion {
  margin: 0;
  color: var(--mp-texto-suave);
}

.controles {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 0.9rem;
}

.buscar {
  max-width: 24rem;
}

.cuenta {
  margin: 0 0 0 auto;
  font-size: 0.875rem;
  color: var(--mp-texto-suave);
}

.tabla {
  padding: 0.25rem 0.25rem 0;
  overflow: hidden;
}

.nombre {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-weight: 600;
}

.detalle {
  display: block;
  font-size: 0.8125rem;
  color: var(--mp-texto-suave);
}

.acciones {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  justify-content: flex-end;
}

.acciones .mp-boton {
  padding: 0.35rem 0.7rem;
  font-size: 0.875rem;
}

.nota {
  font-size: 0.8125rem;
  color: var(--mp-texto-suave);
}

.campos {
  margin: 0;
  padding: 0;
  border: 0;
}

.peligro {
  background: var(--mp-rojo);
}

.peligro:hover:not(:disabled) {
  background: #a03024;
}

.confirmacion {
  margin: 0 0 0.5rem;
}

.detalle-baja {
  margin: 0 0 0.75rem;
  font-size: 0.875rem;
  color: var(--mp-texto-suave);
}

@media (max-width: 720px) {
  .encabezado,
  .controles {
    flex-direction: column;
    align-items: stretch;
  }

  .cuenta {
    margin-left: 0;
  }

  .buscar {
    max-width: none;
  }
}
</style>

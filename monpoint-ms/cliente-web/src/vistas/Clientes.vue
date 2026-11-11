<script setup>
import { computed, ref, reactive, nextTick } from 'vue'
import { gateway } from '@/api/gateway'
import { useSesion } from '@/stores/sesion'
import { useRecurso } from '@/composables/useRecurso'
import { useAccion } from '@/composables/useAccion'
import EstadoRecurso from '@/componentes/EstadoRecurso.vue'
import TablaDatos from '@/componentes/TablaDatos.vue'
import DialogoModal from '@/componentes/DialogoModal.vue'
import CampoFormulario from '@/componentes/CampoFormulario.vue'
import { aPeticion, desdeCliente, filtrar, validar, vacio } from '@/clientes/formulario'

/**
 * Clientes (ms-clientes, tareas 3.4 y H-040).
 *
 * Los permisos NO son los de Productos y conviene no copiarlos por inercia:
 * aqui **ambos roles** pueden dar de alta y modificar —el servicio lo permite
 * y su propia documentacion marca ese punto como no definido— y solo ADMIN
 * puede eliminar.
 *
 * Y el borrado tampoco es el de Productos. Alli es logico: el documento
 * permanece con `activo=false`. Aqui es FISICO, porque el modelo de datos no
 * tiene campo `activo` en `clientes` y el esquema esta cerrado. Ademas no
 * existe evento de baja, asi que ninguna replica se entera. Por eso el dialogo
 * dice "Eliminar", avisa de que es permanente y nombra la consecuencia en
 * ventas: llamarlo "dar de baja" prometeria algo reversible que no lo es.
 */

const sesion = useSesion()
const esAdmin = computed(() => sesion.rol === 'ADMIN')

const COLUMNAS = [
  { clave: 'nombre', titulo: 'Cliente' },
  { clave: 'correo', titulo: 'Correo' },
  { clave: 'telefono', titulo: 'Teléfono', ancho: '10rem' },
]
const COLUMNAS_ADMIN = [...COLUMNAS, { clave: 'acciones', titulo: '', ancho: '13rem' }]
// Modificar lo pueden los dos roles, asi que la columna de acciones existe
// siempre; lo que cambia es que solo ADMIN ve el boton de eliminar.
const columnas = computed(() => COLUMNAS_ADMIN)

const recurso = useRecurso(({ senal }) => gateway.obtener('/clientes', { senal }), { inicial: [] })

const consulta = ref('')
const todos = computed(() => (Array.isArray(recurso.datos.value) ? recurso.datos.value : []))
const filas = computed(() => filtrar(todos.value, consulta.value))

// --- alta y edicion -------------------------------------------------------

const dialogo = ref(null) // null | 'alta' | 'edicion'
const editando = ref(null)
const forma = reactive(vacio())

const guardado = useAccion((id, cuerpo) =>
  id ? gateway.reemplazar(`/clientes/${id}`, cuerpo) : gateway.enviar('/clientes', cuerpo),
)

function abrirAlta () {
  Object.assign(forma, vacio())
  guardado.limpiar()
  editando.value = null
  dialogo.value = 'alta'
}

function abrirEdicion (cliente) {
  Object.assign(forma, desdeCliente(cliente))
  guardado.limpiar()
  editando.value = cliente
  dialogo.value = 'edicion'
}

function cerrarDialogo () {
  if (guardado.enCurso.value) return
  dialogo.value = null
  editando.value = null
}

async function guardar () {
  if (guardado.enCurso.value) return

  const encontrados = validar(forma)
  if (Object.keys(encontrados).length > 0) {
    guardado.fijarCampos(encontrados)
    // El foco va al primer campo con error: sin esto el mensaje puede quedar
    // fuera de la vista y parece que el boton no hizo nada.
    await nextTick()
    document.getElementById(Object.keys(encontrados)[0])?.focus()
    return
  }

  const r = await guardado.correr(editando.value?.id ?? null, aPeticion(forma))
  if (!r.ok) return
  dialogo.value = null
  editando.value = null
  await recurso.recargar()
}

// --- borrado fisico -------------------------------------------------------

const eliminando = ref(null)
const borrado = useAccion((id) => gateway.eliminar(`/clientes/${id}`))

async function confirmarEliminar () {
  if (!eliminando.value) return
  const r = await borrado.correr(eliminando.value.id)
  if (!r.ok) return
  eliminando.value = null
  await recurso.recargar()
}

function pedirEliminar (cliente) {
  borrado.limpiar()
  eliminando.value = cliente
}
</script>

<template>
  <section class="pantalla">
    <header class="encabezado">
      <div>
        <p class="seccion">DIRECTORIO</p>
        <h1>Clientes</h1>
        <p class="descripcion">
          Contactos disponibles en la caja. Altas y cambios para ambos roles.
        </p>
      </div>

      <button type="button" class="mp-boton" @click="abrirAlta">Nuevo cliente</button>
    </header>

    <div class="controles">
      <label class="mp-solo-lectores" for="buscar">Buscar por nombre, correo o teléfono</label>
      <input
        id="buscar"
        v-model="consulta"
        class="mp-campo buscar"
        type="search"
        placeholder="Buscar por nombre, correo o teléfono"
      />
      <p class="cuenta" aria-live="polite">{{ filas.length }} de {{ todos.length }}</p>
    </div>

    <div class="mp-tarjeta tabla">
      <EstadoRecurso
        :cargando="recurso.cargando.value"
        :error="recurso.error.value"
        :denegado="recurso.denegado.value"
        :vacio="recurso.resuelto.value && filas.length === 0"
        :mensaje-vacio="
          consulta.trim() === ''
            ? 'Todavía no hay clientes registrados.'
            : 'Ningún cliente coincide con la búsqueda.'
        "
        @reintentar="recurso.recargar()"
      >
        <TablaDatos
          :columnas="columnas"
          :filas="filas"
          descripcion="Clientes registrados, con su correo y teléfono"
        >
          <template #celda-nombre="{ fila }">
            <span class="nombre">{{ fila.nombre }}</span>
            <span v-if="fila.direccion" class="detalle">{{ fila.direccion }}</span>
          </template>

          <!-- Un campo opcional ausente se muestra con una raya y no en
               blanco: una celda vacia se confunde con un fallo de carga. -->
          <template #celda-correo="{ fila }">{{ fila.correo || '—' }}</template>
          <template #celda-telefono="{ fila }">{{ fila.telefono || '—' }}</template>

          <template #celda-acciones="{ fila }">
            <div class="acciones">
              <button type="button" class="mp-boton mp-boton--secundario" @click="abrirEdicion(fila)">
                Editar
              </button>
              <button
                v-if="esAdmin"
                type="button"
                class="mp-boton mp-boton--secundario"
                @click="pedirEliminar(fila)"
              >
                Eliminar
              </button>
            </div>
          </template>
        </TablaDatos>
      </EstadoRecurso>
    </div>

    <DialogoModal
      :abierto="dialogo !== null"
      :ocupado="guardado.enCurso.value"
      :titulo="dialogo === 'edicion' ? 'Editar cliente' : 'Nuevo cliente'"
      @cerrar="cerrarDialogo"
    >
      <form id="forma-cliente" novalidate @submit.prevent="guardar">
        <div v-if="guardado.error.value" class="mp-aviso mp-aviso--error" role="alert">
          {{ guardado.error.value.message }}
        </div>

        <fieldset class="campos" :disabled="guardado.enCurso.value">
          <CampoFormulario id="nombre" etiqueta="Nombre" :error="guardado.campos.value.nombre">
            <template #default="{ atributos }">
              <input v-bind="atributos" v-model="forma.nombre" class="mp-campo" type="text" />
            </template>
          </CampoFormulario>

          <CampoFormulario
            id="correo"
            etiqueta="Correo (opcional)"
            :error="guardado.campos.value.correo"
          >
            <template #default="{ atributos }">
              <input v-bind="atributos" v-model="forma.correo" class="mp-campo" type="email" />
            </template>
          </CampoFormulario>

          <CampoFormulario
            id="telefono"
            etiqueta="Teléfono (opcional)"
            :error="guardado.campos.value.telefono"
          >
            <template #default="{ atributos }">
              <input v-bind="atributos" v-model="forma.telefono" class="mp-campo" type="tel" />
            </template>
          </CampoFormulario>

          <CampoFormulario
            id="direccion"
            etiqueta="Dirección (opcional)"
            :error="guardado.campos.value.direccion"
          >
            <template #default="{ atributos }">
              <input v-bind="atributos" v-model="forma.direccion" class="mp-campo" type="text" />
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
          form="forma-cliente"
          class="mp-boton"
          :disabled="guardado.enCurso.value"
        >
          {{ guardado.enCurso.value ? 'Guardando…' : 'Guardar' }}
        </button>
      </template>
    </DialogoModal>

    <!-- Solo ADMIN elimina, asi que el dialogo tampoco existe para los demas:
         un <dialog> cerrado conserva su contenido en el DOM. -->
    <DialogoModal
      v-if="esAdmin"
      :abierto="eliminando !== null"
      :ocupado="borrado.enCurso.value"
      titulo="Eliminar cliente"
      @cerrar="eliminando = null"
    >
      <div v-if="borrado.error.value" class="mp-aviso mp-aviso--error" role="alert">
        {{ borrado.error.value.message }}
      </div>

      <p class="confirmacion">
        <strong>{{ eliminando?.nombre }}</strong> se borrará de forma permanente.
      </p>
      <p class="detalle-baja">
        No es una baja lógica: el registro desaparece y no se puede recuperar. Las ventas ya
        registradas conservan el nombre del cliente porque lo guardan consigo, pero ningún otro
        servicio recibe aviso de este borrado.
      </p>

      <template #pie>
        <button
          type="button"
          class="mp-boton mp-boton--secundario"
          :disabled="borrado.enCurso.value"
          @click="eliminando = null"
        >
          Cancelar
        </button>
        <button
          type="button"
          class="mp-boton peligro"
          :disabled="borrado.enCurso.value"
          @click="confirmarEliminar"
        >
          {{ borrado.enCurso.value ? 'Eliminando…' : 'Eliminar' }}
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
  display: block;
  font-weight: 600;
}

.detalle {
  display: block;
  font-size: 0.8125rem;
  color: var(--mp-texto-suave);
}

.acciones {
  display: flex;
  gap: 0.4rem;
  justify-content: flex-end;
}

.acciones .mp-boton {
  padding: 0.35rem 0.7rem;
  font-size: 0.875rem;
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
  margin: 0;
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

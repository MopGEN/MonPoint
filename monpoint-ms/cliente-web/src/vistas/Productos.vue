<script setup>
import { computed, ref, reactive, nextTick } from 'vue'
import { gateway } from '@/api/gateway'
import { useSesion } from '@/stores/sesion'
import { useRecurso } from '@/composables/useRecurso'
import EstadoRecurso from '@/componentes/EstadoRecurso.vue'
import TablaDatos from '@/componentes/TablaDatos.vue'
import DialogoModal from '@/componentes/DialogoModal.vue'
import CampoFormulario from '@/componentes/CampoFormulario.vue'
import { dinero } from '@/comun/importe'
import {
  UMBRAL_POR_OMISION,
  aPeticion,
  bajoUmbral,
  desdeProducto,
  filtrar,
  validar,
  vacio,
} from '@/productos/formulario'

/**
 * Catalogo de productos (ms-inventario, tareas 3.2 y 3.5).
 *
 * La pantalla la ven los dos roles, pero solo ADMIN puede dar de alta, editar
 * o dar de baja: asi lo exige ProductoServiceImpl. Las acciones se ocultan a
 * VENDEDOR, no porque eso sea la seguridad —la aplica el servicio— sino para
 * no ofrecer botones que terminarian en 403.
 *
 * La baja es logica: el documento permanece y el producto desaparece del
 * listado, que solo muestra activos. Por eso el dialogo dice "dar de baja" y
 * no "eliminar": llamarlo eliminar prometeria algo que no ocurre.
 */

const sesion = useSesion()
const esAdmin = computed(() => sesion.rol === 'ADMIN')

const COLUMNAS = [
  { clave: 'nombre', titulo: 'Producto' },
  { clave: 'precio', titulo: 'Precio', cifra: true, ancho: '9rem' },
  { clave: 'existencia', titulo: 'Existencia', cifra: true, ancho: '8rem' },
  { clave: 'umbralStock', titulo: 'Umbral', cifra: true, ancho: '6.5rem' },
]
const COLUMNAS_ADMIN = [...COLUMNAS, { clave: 'acciones', titulo: '', ancho: '13rem' }]

const columnas = computed(() => (esAdmin.value ? COLUMNAS_ADMIN : COLUMNAS))

const recurso = useRecurso(({ senal }) => gateway.obtener('/inventario/productos', { senal }), {
  inicial: [],
})

const consulta = ref('')
const todos = computed(() => (Array.isArray(recurso.datos.value) ? recurso.datos.value : []))
const filas = computed(() => filtrar(todos.value, consulta.value))
const enAlerta = computed(() => todos.value.filter(bajoUmbral).length)

// --- alta y edicion -------------------------------------------------------

const dialogo = ref(null) // null | 'alta' | 'edicion'
const editando = ref(null)
const forma = reactive(vacio())
const errores = ref({})
const errorGeneral = ref(null)
const guardando = ref(false)

function abrirAlta () {
  Object.assign(forma, vacio())
  errores.value = {}
  errorGeneral.value = null
  editando.value = null
  dialogo.value = 'alta'
}

function abrirEdicion (producto) {
  Object.assign(forma, desdeProducto(producto))
  errores.value = {}
  errorGeneral.value = null
  editando.value = producto
  dialogo.value = 'edicion'
}

function cerrarDialogo () {
  if (guardando.value) return
  dialogo.value = null
  editando.value = null
}

async function guardar () {
  // Vue actualiza disabled en el siguiente render; dos submit del mismo turno
  // deben quedar bloqueados tambien antes de que cambie el DOM.
  if (guardando.value) return
  const encontrados = validar(forma, { edicion: dialogo.value === 'edicion' })
  errores.value = encontrados
  errorGeneral.value = null
  if (Object.keys(encontrados).length > 0) {
    // El foco va al primer campo con error: sin esto, en un formulario largo
    // el mensaje aparece fuera de la vista y parece que no paso nada.
    await nextTick()
    document.getElementById(Object.keys(encontrados)[0])?.focus()
    return
  }

  guardando.value = true
  try {
    const cuerpo = aPeticion(forma)
    if (editando.value) await gateway.reemplazar(`/inventario/productos/${editando.value.id}`, cuerpo)
    else await gateway.enviar('/inventario/productos', cuerpo)
    dialogo.value = null
    editando.value = null
    await recurso.recargar()
  } catch (causa) {
    // El servidor devuelve los errores de validacion en `campos`, con la misma
    // clave que usa el formulario, asi que se pintan sin traducir nada. En ese
    // caso no se muestra ademas el aviso general: su texto es el generico
    // "uno o mas campos no cumplen las validaciones", que junto al mensaje
    // concreto del campo solo anade ruido.
    const campos = causa?.campos
    const hayCampos = campos && typeof campos === 'object' && Object.keys(campos).length > 0
    errores.value = hayCampos ? campos : {}
    errorGeneral.value = hayCampos ? null : causa
  } finally {
    guardando.value = false
  }
}

// --- baja logica ----------------------------------------------------------

const dandoBaja = ref(null)
const errorBaja = ref(null)
const bajaEnCurso = ref(false)

function pedirBaja (producto) {
  dandoBaja.value = producto
  errorBaja.value = null
}

async function confirmarBaja () {
  if (bajaEnCurso.value || !dandoBaja.value) return
  bajaEnCurso.value = true
  errorBaja.value = null
  try {
    await gateway.eliminar(`/inventario/productos/${dandoBaja.value.id}`)
    dandoBaja.value = null
    await recurso.recargar()
  } catch (causa) {
    errorBaja.value = causa
  } finally {
    bajaEnCurso.value = false
  }
}
</script>

<template>
  <section class="pantalla">
    <header class="encabezado">
      <div>
        <p class="descripcion">
          Catálogo activo. {{ esAdmin ? 'Altas, cambios y bajas lógicas.' : 'Solo consulta.' }}
        </p>
      </div>

      <button v-if="esAdmin" type="button" class="mp-boton" @click="abrirAlta">
        Nuevo producto
      </button>
    </header>

    <div class="controles">
      <label class="mp-solo-lectores" for="buscar">Buscar por nombre o descripción</label>
      <input
        id="buscar"
        v-model="consulta"
        class="mp-campo buscar"
        type="search"
        placeholder="Buscar por nombre o descripción"
      />

      <p class="cuenta" aria-live="polite">
        {{ filas.length }} de {{ todos.length }}<template v-if="enAlerta > 0">
          · {{ enAlerta }} en umbral o por debajo</template>
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
            ? 'No hay productos activos en el catálogo.'
            : 'Ningún producto activo coincide con la búsqueda.'
        "
        @reintentar="recurso.recargar()"
      >
        <TablaDatos
          :columnas="columnas"
          :filas="filas"
          descripcion="Productos activos, con precio, existencia y umbral"
        >
          <template #celda-nombre="{ fila }">
            <span class="nombre">{{ fila.nombre }}</span>
            <span v-if="fila.descripcion" class="detalle">{{ fila.descripcion }}</span>
          </template>

          <template #celda-precio="{ fila }">{{ dinero(fila.precio) }}</template>

          <template #celda-existencia="{ fila }">
            <span v-if="bajoUmbral(fila)" class="mp-insignia mp-insignia--aviso">
              {{ fila.existencia }} · bajo
            </span>
            <template v-else>{{ fila.existencia }}</template>
          </template>

          <template #celda-acciones="{ fila }">
            <div class="acciones">
              <button type="button" class="mp-boton mp-boton--secundario" @click="abrirEdicion(fila)">
                Editar
              </button>
              <button type="button" class="mp-boton mp-boton--secundario" @click="pedirBaja(fila)">
                Dar de baja
              </button>
            </div>
          </template>
        </TablaDatos>
      </EstadoRecurso>
    </div>

    <!-- Alta y edición comparten formulario: las reglas son las mismas salvo
         el umbral, obligatorio solo al editar. -->
    <DialogoModal
      v-if="esAdmin"
      :abierto="dialogo !== null"
      :ocupado="guardando"
      :titulo="dialogo === 'edicion' ? 'Editar producto' : 'Nuevo producto'"
      @cerrar="cerrarDialogo"
    >
      <form id="forma-producto" novalidate @submit.prevent="guardar">
        <fieldset class="campos" :disabled="guardando">
          <div v-if="errorGeneral" class="mp-aviso mp-aviso--error" role="alert">
            {{ errorGeneral.message }}
          </div>

          <CampoFormulario id="nombre" etiqueta="Nombre" :error="errores.nombre">
            <template #default="{ atributos }">
              <input v-bind="atributos" v-model="forma.nombre" class="mp-campo" type="text" />
            </template>
          </CampoFormulario>

          <CampoFormulario id="descripcion" etiqueta="Descripción (opcional)" :error="errores.descripcion">
            <template #default="{ atributos }">
              <input v-bind="atributos" v-model="forma.descripcion" class="mp-campo" type="text" />
            </template>
          </CampoFormulario>

          <CampoFormulario
            id="precio"
            etiqueta="Precio"
            :error="errores.precio"
            ayuda="Hasta dos decimales, con punto. Sin signo de moneda."
          >
            <template #default="{ atributos }">
              <input
                v-bind="atributos"
                v-model="forma.precio"
                class="mp-campo mp-cifra"
                type="text"
                inputmode="decimal"
              />
            </template>
          </CampoFormulario>

          <CampoFormulario id="existencia" etiqueta="Existencia" :error="errores.existencia">
            <template #default="{ atributos }">
              <input
                v-bind="atributos"
                v-model="forma.existencia"
                class="mp-campo mp-cifra"
                type="text"
                inputmode="numeric"
              />
            </template>
          </CampoFormulario>

          <CampoFormulario
            id="umbralStock"
            etiqueta="Umbral de existencia baja"
            :error="errores.umbralStock"
            :ayuda="
              dialogo === 'edicion'
                ? 'Obligatorio al editar: dejarlo vacío conservaría el umbral anterior.'
                : `Si se deja vacío se usa ${UMBRAL_POR_OMISION}.`
            "
          >
            <template #default="{ atributos }">
              <input
                v-bind="atributos"
                v-model="forma.umbralStock"
                class="mp-campo mp-cifra"
                type="text"
                inputmode="numeric"
              />
            </template>
          </CampoFormulario>
        </fieldset>
      </form>

      <template #pie>
        <button
          type="button"
          class="mp-boton mp-boton--secundario"
          :disabled="guardando"
          @click="cerrarDialogo"
        >
          Cancelar
        </button>
        <button type="submit" form="forma-producto" class="mp-boton" :disabled="guardando">
          {{ guardando ? 'Guardando…' : 'Guardar' }}
        </button>
      </template>
    </DialogoModal>

    <!-- Ambos dialogos solo existen para ADMIN. Un <dialog> cerrado conserva
         su contenido en el DOM, asi que no basta con no poder abrirlo: para
         quien no puede usarlos, no se renderizan. -->
    <DialogoModal
      v-if="esAdmin"
      :abierto="dandoBaja !== null"
      :ocupado="bajaEnCurso"
      titulo="Dar de baja"
      @cerrar="dandoBaja = null"
    >
      <div v-if="errorBaja" class="mp-aviso mp-aviso--error" role="alert">
        {{ errorBaja.message }}
      </div>

      <p class="confirmacion">
        <strong>{{ dandoBaja?.nombre }}</strong> dejará de aparecer en el catálogo y en la caja.
      </p>
      <p class="detalle-baja">
        Es una baja lógica: el registro se conserva y las ventas anteriores no cambian. Para
        volver a venderlo habría que darlo de alta de nuevo.
      </p>

      <template #pie>
        <button
          type="button"
          class="mp-boton mp-boton--secundario"
          :disabled="bajaEnCurso"
          @click="dandoBaja = null"
        >
          Cancelar
        </button>
        <button type="button" class="mp-boton peligro" :disabled="bajaEnCurso" @click="confirmarBaja">
          {{ bajaEnCurso ? 'Dando de baja…' : 'Dar de baja' }}
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

.campos {
  border: 0;
  margin: 0;
  padding: 0;
  min-width: 0;
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
  max-width: 22rem;
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

.peligro {
  background: var(--mp-error-fondo);
  color: var(--mp-error);
  border-color: var(--mp-error-borde);
}

.peligro:hover:not(:disabled) {
  background: var(--mp-error-fondo);
  border-color: var(--mp-error);
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

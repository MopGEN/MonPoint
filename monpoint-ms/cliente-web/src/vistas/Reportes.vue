<script setup>
import { computed, reactive, ref, nextTick } from 'vue'
import { gateway } from '@/api/gateway'
import { useRecurso } from '@/composables/useRecurso'
import { useAccion } from '@/composables/useAccion'
import EstadoRecurso from '@/componentes/EstadoRecurso.vue'
import DialogoModal from '@/componentes/DialogoModal.vue'
import CampoFormulario from '@/componentes/CampoFormulario.vue'
import GraficaBarras from '@/componentes/GraficaBarras.vue'
import { dinero } from '@/comun/importe'
import {
  PERIODOS,
  etiquetaPunto,
  nombreDocumento,
  rangoPorOmision,
  rutaAgregado,
  validar,
  validarHistorial,
} from '@/reportes/consulta'

/**
 * Reportes (ms-reportes, tareas 4.2, 4.6 y H-054).
 *
 * Pantalla solo de ADMIN: la agregacion y las tres exportaciones PDF lo
 * exigen en el servicio. La configuracion de empresa la puede LEER tambien un
 * vendedor, pero no hay motivo para darle una pantalla cuyo resto no puede
 * usar, asi que vive aqui.
 *
 * La agregacion cuenta solo ventas CONFIRMADAS: una venta cancelada no suma.
 * Conviene decirlo en pantalla, porque un total que no cuadra con el numero de
 * folios emitidos parece un error y no lo es.
 */

const consulta = reactive(rangoPorOmision())
const errores = ref({})

const agregado = useRecurso(({ senal }) => gateway.obtener(rutaAgregado(consulta), { senal }), {
  inicial: null,
})

const puntos = computed(() =>
  (agregado.datos.value?.puntos ?? []).map((p) => ({
    inicio: p.inicio,
    total: p.total,
    etiqueta: etiquetaPunto(agregado.datos.value?.periodo ?? consulta.periodo, p.inicio),
  })),
)

const total = computed(() => agregado.datos.value?.total ?? null)
const zona = computed(() => agregado.datos.value?.zonaHoraria ?? null)
const descripcionAgregado = computed(() => {
  const datos = agregado.datos.value
  return datos ? `Ventas confirmadas entre ${datos.desde} y ${datos.hasta}` : 'Ventas confirmadas'
})

async function consultar () {
  const encontrados = validar(consulta)
  errores.value = encontrados
  if (Object.keys(encontrados).length > 0) {
    await nextTick()
    document.getElementById(Object.keys(encontrados)[0])?.focus()
    return
  }
  await agregado.recargar()
}

// --- documentos -----------------------------------------------------------

/**
 * Abre un PDF del servicio.
 *
 * No se usa un enlace: el token vive en memoria y una navegacion normal no lo
 * lleva. Se pide con fetch, se envuelve en una URL de objeto y se revoca
 * despues, porque cada URL que no se revoca retiene su contenido en memoria
 * mientras viva el documento.
 */
const documento = useAccion(async (ruta, nombre) => {
  const blob = await gateway.descargar(ruta)
  const url = URL.createObjectURL(blob)
  try {
    const enlace = document.createElement('a')
    enlace.href = url
    enlace.download = nombre
    enlace.rel = 'noopener'
    document.body.appendChild(enlace)
    enlace.click()
    enlace.remove()
  } finally {
    // Se conservan diez segundos: revocar en el mismo instante puede cancelar
    // la descarga que se acaba de iniciar.
    setTimeout(() => URL.revokeObjectURL(url), 10_000)
  }
})

const DOCUMENTOS = computed(() => [
  {
    clave: 'historial',
    titulo: 'Historial de ventas',
    detalle: `Ventas confirmadas entre ${consulta.desde} y ${consulta.hasta}.`,
    ruta: `/reportes/ventas/historial?desde=${consulta.desde}&hasta=${consulta.hasta}`,
    nombre: nombreDocumento('historial-ventas', `${consulta.desde}_${consulta.hasta}`),
    bloqueo: Object.values(validarHistorial(consulta)).join(' '),
  },
  {
    clave: 'productos',
    titulo: 'Listado de productos',
    detalle: 'Catálogo activo con precios y existencias.',
    ruta: '/reportes/productos/listado',
    nombre: nombreDocumento('listado-productos'),
  },
  {
    clave: 'clientes',
    titulo: 'Listado de clientes',
    detalle: 'Directorio completo de clientes registrados.',
    ruta: '/reportes/clientes/listado',
    nombre: nombreDocumento('listado-clientes'),
  },
])

const descargando = ref('')

async function abrirDocumento (doc) {
  if (documento.enCurso.value || doc.bloqueo) return
  descargando.value = doc.clave
  await documento.correr(doc.ruta, doc.nombre)
  descargando.value = ''
}

// --- configuracion de empresa --------------------------------------------

const configuracion = useRecurso(async ({ senal }) => {
  try {
    return await gateway.obtener('/reportes/configuracion', { senal })
  } catch (causa) {
    // Sin documento aun: no es un bloqueo para registrar la primera empresa.
    if (causa.estado === 404) return null
    throw causa
  }
})

const dialogo = ref(false)
const forma = reactive({ nombreEmpresa: '', rfc: '', direccion: '', telefono: '' })
const guardado = useAccion((cuerpo) => gateway.reemplazar('/reportes/configuracion', cuerpo))

function abrirConfiguracion () {
  const c = configuracion.datos.value ?? {}
  Object.assign(forma, {
    nombreEmpresa: c.nombreEmpresa ?? '',
    rfc: c.rfc ?? '',
    direccion: c.direccion ?? '',
    telefono: c.telefono ?? '',
  })
  guardado.limpiar()
  dialogo.value = true
}

async function guardarConfiguracion () {
  if (guardado.enCurso.value) return

  // Comprobacion local minima. El servidor aplica ademas @Pattern al RFC y
  // @Size a nombre/direccion/telefono; useAccion muestra esos errores por campo.
  const encontrados = {}
  if (forma.nombreEmpresa.trim() === '') {
    encontrados.nombreEmpresa = 'El nombre de la empresa es obligatorio'
  }
  if (forma.rfc.trim() === '') encontrados.rfc = 'El RFC es obligatorio'
  if (Object.keys(encontrados).length > 0) {
    guardado.fijarCampos(encontrados)
    await nextTick()
    document.getElementById(Object.keys(encontrados)[0])?.focus()
    return
  }

  const limpio = (v) => {
    const t = v.trim()
    return t === '' ? null : t
  }
  const r = await guardado.correr({
    nombreEmpresa: forma.nombreEmpresa.trim(),
    rfc: forma.rfc.trim(),
    direccion: limpio(forma.direccion),
    telefono: limpio(forma.telefono),
  })
  if (!r.ok) return
  dialogo.value = false
  await configuracion.recargar()
}
</script>

<template>
  <section class="pantalla">
    <header class="encabezado">
      <div>
        <p class="seccion">ANÁLISIS</p>
        <h1>Reportes</h1>
        <p class="descripcion">
          Ventas confirmadas por periodo. Una venta cancelada no suma a ningún total.
        </p>
      </div>
    </header>

    <form class="mp-tarjeta filtros" @submit.prevent="consultar">
      <CampoFormulario id="periodo" etiqueta="Granularidad" :error="errores.periodo">
        <template #default="{ atributos }">
          <select v-bind="atributos" v-model="consulta.periodo" class="mp-campo">
            <option v-for="p in PERIODOS" :key="p.valor" :value="p.valor">{{ p.etiqueta }}</option>
          </select>
        </template>
      </CampoFormulario>

      <CampoFormulario id="desde" etiqueta="Desde" :error="errores.desde">
        <template #default="{ atributos }">
          <input v-bind="atributos" v-model="consulta.desde" class="mp-campo" type="date" />
        </template>
      </CampoFormulario>

      <CampoFormulario id="hasta" etiqueta="Hasta" :error="errores.hasta">
        <template #default="{ atributos }">
          <input v-bind="atributos" v-model="consulta.hasta" class="mp-campo" type="date" />
        </template>
      </CampoFormulario>

      <button type="submit" class="mp-boton" :disabled="agregado.cargando.value">
        {{ agregado.cargando.value ? 'Consultando…' : 'Consultar' }}
      </button>
    </form>

    <div class="mp-tarjeta panel">
      <EstadoRecurso
        :cargando="agregado.cargando.value"
        :error="agregado.error.value"
        :denegado="agregado.denegado.value"
        :vacio="agregado.resuelto.value && puntos.length === 0"
        mensaje-vacio="No hay periodos en el rango consultado."
        @reintentar="consultar()"
      >
        <div class="resumen">
          <div>
            <p class="mp-etiqueta">Total del rango</p>
            <p class="total mp-cifra">{{ dinero(total) }}</p>
            <p class="detalle rango-aplicado">{{ descripcionAgregado }}</p>
          </div>
          <p v-if="zona" class="zona">
            Los periodos se cortan en la zona horaria <strong>{{ zona }}</strong>.
          </p>
        </div>

        <GraficaBarras
          :puntos="puntos"
          :descripcion="descripcionAgregado"
        />
      </EstadoRecurso>
    </div>

    <h2 class="titulo-seccion">Documentos</h2>
    <div v-if="documento.error.value" class="mp-aviso mp-aviso--error" role="alert">
      {{ documento.error.value.message }}
    </div>

    <ul class="documentos">
      <li v-for="doc in DOCUMENTOS" :key="doc.clave" class="mp-tarjeta documento">
        <div class="texto">
          <p class="nombre">{{ doc.titulo }}</p>
          <p class="detalle">{{ doc.detalle }}</p>
          <p v-if="doc.bloqueo" :id="`${doc.clave}-bloqueo`" class="detalle">{{ doc.bloqueo }}</p>
        </div>
        <button
          type="button"
          class="mp-boton mp-boton--secundario"
          :disabled="documento.enCurso.value || Boolean(doc.bloqueo)"
          :aria-describedby="doc.bloqueo ? `${doc.clave}-bloqueo` : undefined"
          @click="abrirDocumento(doc)"
        >
          {{ descargando === doc.clave ? 'Generando…' : 'Descargar PDF' }}
        </button>
      </li>
    </ul>

    <h2 class="titulo-seccion">Datos de la empresa</h2>
    <div class="mp-tarjeta panel">
      <EstadoRecurso
        :cargando="configuracion.cargando.value"
        :error="configuracion.error.value"
        :denegado="configuracion.denegado.value"
        @reintentar="configuracion.recargar()"
      >
        <dl class="datos">
          <div><dt>Empresa</dt><dd>{{ configuracion.datos.value?.nombreEmpresa || '—' }}</dd></div>
          <div><dt>RFC</dt><dd>{{ configuracion.datos.value?.rfc || '—' }}</dd></div>
          <div><dt>Dirección</dt><dd>{{ configuracion.datos.value?.direccion || '—' }}</dd></div>
          <div><dt>Teléfono</dt><dd>{{ configuracion.datos.value?.telefono || '—' }}</dd></div>
        </dl>
        <p class="pie-datos">
          {{ configuracion.datos.value ? 'Estos datos encabezan los tickets y los listados en PDF.' : 'Todavía no se han registrado los datos de la empresa.' }}
          <button type="button" class="mp-boton mp-boton--secundario" @click="abrirConfiguracion">
            {{ configuracion.datos.value ? 'Editar' : 'Registrar datos de empresa' }}
          </button>
        </p>
      </EstadoRecurso>
    </div>

    <DialogoModal
      :abierto="dialogo"
      :ocupado="guardado.enCurso.value"
      titulo="Datos de la empresa"
      @cerrar="guardado.enCurso.value || (dialogo = false)"
    >
      <form id="forma-config" novalidate @submit.prevent="guardarConfiguracion">
        <div v-if="guardado.error.value" class="mp-aviso mp-aviso--error" role="alert">
          {{ guardado.error.value.message }}
        </div>

        <fieldset class="campos" :disabled="guardado.enCurso.value">
          <CampoFormulario
            id="nombreEmpresa"
            etiqueta="Nombre de la empresa"
            :error="guardado.campos.value.nombreEmpresa"
          >
            <template #default="{ atributos }">
              <input v-bind="atributos" v-model="forma.nombreEmpresa" class="mp-campo" type="text" maxlength="120" />
            </template>
          </CampoFormulario>

          <CampoFormulario id="rfc" etiqueta="RFC" :error="guardado.campos.value.rfc" ayuda="El servicio valida el formato del RFC (12 o 13 caracteres).">
            <template #default="{ atributos }">
              <input v-bind="atributos" v-model="forma.rfc" class="mp-campo" type="text" />
            </template>
          </CampoFormulario>

          <CampoFormulario
            id="direccion"
            etiqueta="Dirección (opcional)"
            :error="guardado.campos.value.direccion"
          >
            <template #default="{ atributos }">
              <input v-bind="atributos" v-model="forma.direccion" class="mp-campo" type="text" maxlength="200" />
            </template>
          </CampoFormulario>

          <CampoFormulario
            id="telefono"
            etiqueta="Teléfono (opcional)"
            :error="guardado.campos.value.telefono"
          >
            <template #default="{ atributos }">
              <input v-bind="atributos" v-model="forma.telefono" class="mp-campo" type="tel" maxlength="30" />
            </template>
          </CampoFormulario>
        </fieldset>
      </form>

      <template #pie>
        <button
          type="button"
          class="mp-boton mp-boton--secundario"
          :disabled="guardado.enCurso.value"
          @click="dialogo = false"
        >
          Cancelar
        </button>
        <button type="submit" form="forma-config" class="mp-boton" :disabled="guardado.enCurso.value">
          {{ guardado.enCurso.value ? 'Guardando…' : 'Guardar' }}
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
  margin-bottom: 1rem;
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

.filtros {
  display: flex;
  align-items: flex-end;
  gap: 0.75rem;
  flex-wrap: wrap;
  padding: 0.9rem 1rem;
  margin-bottom: 0.9rem;
}

.filtros .campo,
.filtros :deep(.campo) {
  margin-top: 0;
}

.panel {
  padding: 1rem;
  margin-bottom: 1.25rem;
}

.resumen {
  display: flex;
  align-items: flex-start;
  gap: 1rem;
  margin-bottom: 0.75rem;
}

.resumen .mp-etiqueta {
  margin-bottom: 0.1rem;
}

.total {
  margin: 0;
  font-size: 1.75rem;
  font-weight: 700;
}

.zona {
  margin: 0 0 0 auto;
  max-width: 22rem;
  text-align: right;
  font-size: 0.8125rem;
  color: var(--mp-texto-suave);
}

.titulo-seccion {
  margin: 0 0 0.6rem;
  font-size: 1.0625rem;
}

.documentos {
  display: grid;
  gap: 0.6rem;
  margin: 0 0 1.25rem;
  padding: 0;
  list-style: none;
}

.documento {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 0.75rem 1rem;
}

.documento .texto {
  flex: 1;
  min-width: 0;
}

.nombre {
  margin: 0;
  font-weight: 600;
}

.detalle {
  margin: 0;
  font-size: 0.8125rem;
  color: var(--mp-texto-suave);
}

.datos {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(12rem, 1fr));
  gap: 0.75rem 1.5rem;
  margin: 0 0 0.9rem;
}

.datos dt {
  font-size: 0.8125rem;
  font-weight: 600;
  color: var(--mp-texto-suave);
}

.datos dd {
  margin: 0.1rem 0 0;
}

.pie-datos {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin: 0;
  padding-top: 0.75rem;
  border-top: 1px solid var(--mp-borde);
  font-size: 0.875rem;
  color: var(--mp-texto-suave);
}

.pie-datos .mp-boton {
  margin-left: auto;
}

.campos {
  margin: 0;
  padding: 0;
  border: 0;
}

@media (max-width: 720px) {
  .resumen {
    flex-direction: column;
  }

  .zona {
    margin-left: 0;
    text-align: left;
    max-width: none;
  }

  .documento {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>

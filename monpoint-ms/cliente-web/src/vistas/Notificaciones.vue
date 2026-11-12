<script setup>
import { computed, ref } from 'vue'
import { gateway } from '@/api/gateway'
import { avisosActualizados } from '@/composables/actualizacionesInterfaz'
import { useRecurso } from '@/composables/useRecurso'
import EstadoRecurso from '@/componentes/EstadoRecurso.vue'
import TablaDatos from '@/componentes/TablaDatos.vue'

/**
 * Alertas de existencia baja (ms-notificaciones, tarea 5.4).
 *
 * Hasta ahora el sistema generaba estas alertas y nadie podia verlas: el unico
 * modo de comprobarlas era consultar la API. Esta pantalla las hace visibles,
 * como una ampliacion del cliente sobre el servicio ya terminado.
 *
 * Solo ADMIN. El enrutador ya lo impide, pero la pantalla tambien trata el 403
 * por si alguien llega por URL con un token de otro rol: la guarda es
 * comodidad; el Gateway autentica y el servicio exige ADMIN.
 *
 * El servicio devuelve la entidad, no un DTO. Se consume tal cual y se anota
 * como deuda en la 6.2: si manana se envuelve en un DTO, esta vista cambia.
 */

const COLUMNAS = [
  { clave: 'estado', titulo: 'Estado', ancho: '7.5rem' },
  { clave: 'productoNombre', titulo: 'Producto' },
  { clave: 'existenciaResultante', titulo: 'Existencia', cifra: true, ancho: '7rem' },
  { clave: 'fechaCreacion', titulo: 'Generada', ancho: '12rem' },
  // Ancho suficiente para que "Marcar leída" quepa en una linea: un boton
  // partido en dos renglones dentro de una tabla se lee como un fallo.
  { clave: 'acciones', titulo: '', ancho: '11.5rem' },
]

/** null = todas; true/false filtran por leida. */
const filtro = ref(false)

const recurso = useRecurso(
  ({ senal }) => {
    const consulta = filtro.value === null ? '' : `?leida=${filtro.value}`
    return gateway.obtener(`/notificaciones${consulta}`, { senal })
  },
  { inicial: [] },
)

function cambiarFiltro (valor) {
  filtro.value = valor
  recurso.recargar()
}

const filas = computed(() => (Array.isArray(recurso.datos.value) ? recurso.datos.value : []))

const sinLeer = computed(() => filas.value.filter((n) => !n.leida).length)

/** Identificadores en curso de marcado, para deshabilitar solo su boton. */
const marcando = ref(new Set())
const errorAccion = ref(null)

async function marcarLeida (notificacion) {
  if (marcando.value.has(notificacion.id)) return
  // Reemplazar el conjunto expresa cada cambio como una asignacion.
  marcando.value = new Set(marcando.value).add(notificacion.id)
  errorAccion.value = null
  try {
    await gateway.reemplazar(`/notificaciones/${notificacion.id}/leida`)
    // Se recarga en vez de parchear la fila en memoria. La operacion es
    // idempotente en el servidor y la lista depende del filtro: si el filtro
    // es "sin leer", la fila debe desaparecer, y eso lo decide el servidor,
    // no esta vista.
    await recurso.recargar()
    avisosActualizados()
  } catch (causa) {
    errorAccion.value = causa
  } finally {
    const copia = new Set(marcando.value)
    copia.delete(notificacion.id)
    marcando.value = copia
  }
}

const formatoFecha = new Intl.DateTimeFormat('es-MX', {
  dateStyle: 'short',
  timeStyle: 'short',
})

function fecha (valor) {
  if (!valor) return '—'
  const d = new Date(valor)
  return Number.isNaN(d.getTime()) ? String(valor) : formatoFecha.format(d)
}
</script>

<template>
  <section class="pantalla">
    <header class="encabezado">
      <div>
        <p class="descripcion">
          Alertas generadas cuando un producto queda en su umbral o por debajo.
        </p>
      </div>

      <button
        type="button"
        class="mp-boton mp-boton--secundario"
        :disabled="recurso.cargando.value"
        @click="recurso.recargar()"
      >
        Actualizar
      </button>
    </header>

    <div class="filtros" role="group" aria-label="Filtrar notificaciones">
      <button
        v-for="opcion in [
          { valor: false, texto: 'Sin leer' },
          { valor: true, texto: 'Leídas' },
          { valor: null, texto: 'Todas' },
        ]"
        :key="String(opcion.valor)"
        type="button"
        class="mp-boton mp-boton--secundario filtro"
        :class="{ elegido: filtro === opcion.valor }"
        :aria-pressed="filtro === opcion.valor"
        @click="cambiarFiltro(opcion.valor)"
      >
        {{ opcion.texto }}
      </button>

      <!-- aria-live para que un lector de pantalla anuncie el cambio del
           contador sin que el usuario tenga que ir a buscarlo. -->
      <p class="cuenta" aria-live="polite">
        {{ sinLeer }} sin leer de {{ filas.length }} mostradas
      </p>
    </div>

    <div v-if="errorAccion" class="mp-aviso mp-aviso--error" role="alert">
      {{ errorAccion.message }}
    </div>

    <div class="mp-tarjeta tabla">
      <EstadoRecurso
        :cargando="recurso.cargando.value"
        :error="recurso.error.value"
        :denegado="recurso.denegado.value"
        :vacio="recurso.resuelto.value && filas.length === 0"
        mensaje-vacio="No hay alertas con este filtro."
        @reintentar="recurso.recargar()"
      >
        <TablaDatos
          :columnas="COLUMNAS"
          :filas="filas"
          descripcion="Alertas de existencia baja, con su producto, existencia y fecha"
        >
          <template #celda-estado="{ fila }">
            <span
              class="mp-insignia"
              :class="fila.leida ? 'mp-insignia--apagada' : 'mp-insignia--aviso'"
            >
              {{ fila.leida ? 'Leída' : 'Sin leer' }}
            </span>
          </template>

          <template #celda-productoNombre="{ fila }">
            <span class="producto">{{ fila.productoNombre }}</span>
            <span class="mensaje">{{ fila.mensaje }}</span>
          </template>

          <template #celda-fechaCreacion="{ fila }">
            {{ fecha(fila.fechaCreacion) }}
          </template>

          <template #celda-acciones="{ fila }">
            <button
              v-if="!fila.leida"
              type="button"
              class="mp-boton mp-boton--secundario"
              :disabled="marcando.has(fila.id)"
              @click="marcarLeida(fila)"
            >
              {{ marcando.has(fila.id) ? 'Marcando…' : 'Marcar leída' }}
            </button>
            <span v-else class="leida-en">{{ fecha(fila.fechaLectura) }}</span>
          </template>
        </TablaDatos>
      </EstadoRecurso>
    </div>
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

.filtros {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  margin-bottom: 0.9rem;
}

.filtro {
  padding: 0.35rem 0.8rem;
  font-size: 0.875rem;
}

.filtro.elegido {
  border-color: var(--mp-verde);
  color: var(--mp-verde);
  font-weight: 700;
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

.producto {
  display: block;
  font-weight: 600;
}

.mensaje {
  display: block;
  font-size: 0.8125rem;
  color: var(--mp-texto-suave);
}

.leida-en {
  font-size: 0.8125rem;
  color: var(--mp-texto-suave);
}

@media (max-width: 720px) {
  .encabezado {
    flex-direction: column;
  }

  .cuenta {
    width: 100%;
    margin-left: 0;
  }

  .filtros {
    flex-wrap: wrap;
  }
}
</style>

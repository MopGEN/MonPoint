<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { gateway } from '@/api/gateway'
import { useCaja } from '@/stores/caja'
import { crearCobro, editable, cantidadValida, precioValido, dinero, estimado } from '@/caja/cobro'
const s = useCaja().cobro
const cobro = crearCobro(s, gateway)
const productos = ref([]),
  clientes = ref([]),
  busqueda = ref('')
const cargando = ref(false),
  errorCatalogo = ref(''),
  errorClientes = ref('')
let carga,
  desmontado = false
const puedeEditar = computed(() => editable(s))
const validas = computed(() =>
  s.lineas.every((l) => cantidadValida(l.cantidad) && precioValido(l.precio)),
)
const total = computed(() => (validas.value ? dinero(estimado(s.lineas)) : '—'))
const visibles = computed(() =>
  productos.value.filter(
    (p) =>
      p.activo !== false &&
      p.nombre
        .toLocaleLowerCase('es-MX')
        .includes(busqueda.value.trim().toLocaleLowerCase('es-MX')),
  ),
)
const rechazado = computed(() => (s.estado === 'cancelada' ? s.venta?.productoIdRechazado : null))
async function cargar() {
  if (cargando.value) return
  cargando.value = true
  errorCatalogo.value = ''
  errorClientes.value = ''
  carga = new AbortController()
  const [p, c] = await Promise.allSettled([
    gateway.obtener('/inventario/productos', { senal: carga.signal }),
    gateway.obtener('/clientes', { senal: carga.signal }),
  ])
  if (desmontado) return
  if (p.status === 'fulfilled' && Array.isArray(p.value)) productos.value = p.value
  else errorCatalogo.value = p.reason?.message ?? 'No se pudo cargar el catálogo.'
  if (c.status === 'fulfilled' && Array.isArray(c.value)) clientes.value = c.value
  else errorClientes.value = c.reason?.message ?? 'No se pudieron cargar los clientes.'
  cargando.value = false
}
function agregar(p) {
  if (!puedeEditar.value || !precioValido(p.precio)) return
  const l = s.lineas.find((l) => l.productoId === p.id)
  if (l) {
    if (cantidadValida(l.cantidad + 1)) l.cantidad++
  } else s.lineas.push({ productoId: p.id, nombre: p.nombre, precio: p.precio, cantidad: 1 })
}
function quitar(id) {
  if (puedeEditar.value) s.lineas = s.lineas.filter((l) => l.productoId !== id)
}
function prevenirSalida(e) {
  if (
    s.lineas.length ||
    s.pendientes.some((p) => !['CONFIRMADA', 'CANCELADA'].includes(p.detalle?.estado))
  ) {
    e.preventDefault()
    e.returnValue = ''
  }
}
onMounted(() => {
  void cargar()
  window.addEventListener('beforeunload', prevenirSalida)
})
onUnmounted(() => {
  desmontado = true
  carga?.abort()
  cobro.detener()
  window.removeEventListener('beforeunload', prevenirSalida)
})
</script>

<template>
  <section class="caja">
    <header class="cabecera">
      <div>
        <p class="sobre">PUNTO DE VENTA</p>
        <h1>Caja</h1>
        <p class="suave">Arma el carrito y consulta el resultado del cobro.</p>
      </div>
      <button class="mp-boton mp-boton--secundario" :disabled="cargando" @click="cargar">
        {{ cargando ? 'Actualizando…' : 'Actualizar catálogo' }}
      </button>
    </header>
    <div class="columnas">
      <section class="mp-tarjeta panel" aria-labelledby="titulo-catalogo">
        <h2 id="titulo-catalogo">Productos</h2>
        <label class="mp-etiqueta" for="buscar">Buscar por nombre</label
        ><input
          id="buscar"
          v-model="busqueda"
          class="mp-campo"
          type="search"
          placeholder="Escribe el nombre del producto"
        />
        <p v-if="errorCatalogo" role="alert" class="mp-aviso mp-aviso--error">
          {{ errorCatalogo }}
        </p>
        <p v-else-if="cargando" role="status">Cargando productos…</p>
        <p v-else-if="!visibles.length" class="vacio">No hay productos para esta búsqueda.</p>
        <ul class="productos">
          <li v-for="p in visibles" :key="p.id" class="producto">
            <div>
              <strong>{{ p.nombre }}</strong>
              <p class="suave">Existencia: {{ p.existencia }}</p>
              <span class="mp-cifra precio">{{ dinero(p.precio) }}</span>
            </div>
            <button
              class="mp-boton mp-boton--secundario"
              :aria-label="`Agregar ${p.nombre}`"
              :disabled="!puedeEditar || !precioValido(p.precio)"
              @click="agregar(p)"
            >
              Agregar
            </button>
          </li>
        </ul>
        <p class="suave">La existencia puede cambiar. Se confirma al procesar el cobro.</p>
      </section>
      <section class="mp-tarjeta panel" aria-labelledby="titulo-carrito">
        <h2 id="titulo-carrito">
          Carrito <span class="contador">{{ s.lineas.length }}</span>
        </h2>
        <label class="mp-etiqueta" for="cliente">Cliente (opcional)</label
        ><select id="cliente" v-model="s.clienteId" class="mp-campo" :disabled="!puedeEditar">
          <option value="">Venta sin cliente</option>
          <option v-for="c in clientes" :key="c.id" :value="c.id">{{ c.nombre }}</option>
        </select>
        <p v-if="errorClientes" role="alert" class="mp-aviso mp-aviso--error">
          {{ errorClientes }} Puede registrar una venta sin cliente.
        </p>
        <p v-if="!s.lineas.length" class="vacio">Agrega productos para comenzar una venta.</p>
        <ul class="lineas">
          <li
            v-for="l in s.lineas"
            :key="l.productoId"
            :class="['linea', { rechazada: rechazado === l.productoId }]"
          >
            <div class="linea-titulo">
              <strong>{{ l.nombre }}</strong
              ><button
                class="quitar"
                :aria-label="`Quitar ${l.nombre}`"
                :disabled="!puedeEditar"
                @click="quitar(l.productoId)"
              >
                Quitar
              </button>
            </div>
            <p v-if="rechazado === l.productoId" class="marca-rechazo">
              ⚠ Producto señalado en el rechazo
            </p>
            <div class="linea-cantidad">
              <label :for="`cantidad-${l.productoId}`">Cantidad</label
              ><input
                :id="`cantidad-${l.productoId}`"
                v-model.number="l.cantidad"
                class="mp-campo cantidad"
                type="number"
                min="1"
                max="2147483647"
                step="1"
                :disabled="!puedeEditar"
                :aria-invalid="!cantidadValida(l.cantidad)"
              /><span class="mp-cifra">{{
                cantidadValida(l.cantidad) ? dinero(estimado([l])) : '—'
              }}</span>
            </div>
          </li>
        </ul>
        <p v-if="!validas" role="alert" class="mp-aviso mp-aviso--error">
          Revise las cantidades y los precios del carrito.
        </p>
        <p v-if="s.error" role="alert" class="mp-aviso mp-aviso--error">{{ s.error }}</p>
        <div v-if="s.estado === 'enviando'" class="resultado proceso" role="status">
          <span class="icono">◷</span>
          <h3>Enviando el cobro…</h3>
          <p>Espera la respuesta antes de iniciar otra operación.</p>
        </div>
        <div v-if="s.estado === 'enProceso'" class="resultado proceso" role="status">
          <span class="icono">◷</span>
          <h3>Venta en proceso</h3>
          <p>Folio {{ s.venta.folio }} · Estamos confirmando la operación.</p>
        </div>
        <div v-if="s.estado === 'confirmada'" class="resultado confirmada" role="status">
          <span class="icono">✓</span>
          <h3>Venta confirmada</h3>
          <p>Folio {{ s.venta.folio }}</p>
          <p>
            Total cobrado <strong class="mp-cifra definitivo">{{ dinero(s.venta.total) }}</strong>
          </p>
          <ul class="detalle">
            <li v-for="(l, i) in s.venta.lineas" :key="i">
              {{ l.cantidad }} × {{ l.nombreProducto }} <span>{{ dinero(l.subtotal) }}</span>
            </li>
          </ul>
          <button class="mp-boton mp-boton--secundario" @click="cobro.nueva">Nueva venta</button>
        </div>
        <div v-if="s.estado === 'cancelada'" class="resultado cancelada" role="alert">
          <span class="icono">✕</span>
          <h3>Venta cancelada</h3>
          <p>Folio {{ s.venta.folio }}</p>
          <p>{{ s.venta.motivo }}</p>
          <p>
            Conservamos el carrito. Corrige las cantidades o quita productos antes de cobrar de
            nuevo.
          </p>
        </div>
        <div v-if="s.estado !== 'confirmada'" class="pie-carrito">
          <div>
            <span>Total estimado</span><strong class="mp-cifra total">{{ total }}</strong>
          </div>
          <button
            class="mp-boton cobrar"
            :disabled="!puedeEditar || !s.lineas.length || !validas"
            @click="cobro.cobrar"
          >
            {{
              s.estado === 'enviando'
                ? 'Enviando…'
                : s.estado === 'enProceso'
                  ? 'Esperando resultado…'
                  : 'Cobrar'
            }}
          </button>
        </div>
      </section>
    </div>
    <section
      v-if="s.pendientes.length"
      class="mp-tarjeta panel bandeja"
      aria-labelledby="titulo-pendientes"
    >
      <h2 id="titulo-pendientes">Seguimiento de operaciones</h2>
      <p class="suave">
        Puedes atender otra venta. Consulta estas operaciones antes de volver a cobrarlas.
      </p>
      <article v-for="(p, i) in s.pendientes" :key="i" class="operacion" aria-live="polite">
        <strong>{{ p.folio != null ? `Folio ${p.folio}` : 'Operación sin folio recibido' }}</strong
        ><template v-if="p.detalle?.estado === 'CONFIRMADA'"
          ><p>✓ Venta confirmada · Total cobrado: {{ dinero(p.detalle.total) }}</p></template
        ><template v-else-if="p.detalle?.estado === 'CANCELADA'"
          ><p>✕ Venta cancelada</p>
          <p>{{ p.detalle.motivo }}</p></template
        ><template v-else
          ><p>◷ Resultado sin confirmar</p>
          <p>{{ p.mensaje }}</p>
          <button
            v-if="p.id"
            class="mp-boton mp-boton--secundario"
            :disabled="p.consultando"
            @click="cobro.consultarPendiente(p)"
          >
            {{ p.consultando ? 'Consultando…' : 'Consultar de nuevo' }}
          </button></template
        >
      </article>
    </section>
  </section>
</template>

<style scoped>
.caja {
  max-width: 1280px;
  margin: auto;
}
.cabecera {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 1.5rem;
}
.sobre {
  font-size: 0.75rem;
  letter-spacing: 0.12em;
  font-weight: 700;
  color: var(--mp-verde-oscuro);
  margin: 0 0 0.5rem;
}
.suave {
  color: var(--mp-texto-suave);
  margin: 0.4rem 0;
}
.columnas {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(0, 1fr);
  gap: 1.5rem;
  align-items: start;
}
.panel {
  padding: 1.5rem;
}
.productos,
.lineas,
.detalle {
  list-style: none;
  padding: 0;
  margin: 1rem 0;
}
.productos {
  max-height: 60vh;
  overflow: auto;
}
.producto {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
  padding: 1rem 0;
  border-bottom: 1px solid var(--mp-borde);
}
.precio {
  font-weight: 700;
}
.vacio {
  text-align: center;
  padding: 2rem 0.5rem;
  color: var(--mp-texto-suave);
}
.contador {
  font-size: 0.875rem;
  color: var(--mp-texto-suave);
}
.linea {
  border-bottom: 1px solid var(--mp-borde);
  padding: 1rem 0.5rem;
}
.linea-titulo,
.linea-cantidad,
.detalle li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}
.linea-cantidad {
  margin-top: 0.75rem;
  font-size: 0.875rem;
}
.cantidad {
  width: 6rem;
}
.quitar {
  border: 0;
  background: none;
  text-decoration: underline;
  color: var(--mp-texto-suave);
  cursor: pointer;
}
.quitar:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.rechazada {
  border-left: 4px solid var(--mp-rojo);
  background: rgb(192 57 43 / 8%);
}
.marca-rechazo {
  font-size: 0.875rem;
  font-weight: 600;
}
.pie-carrito {
  border-top: 1px solid var(--mp-borde);
  padding-top: 1rem;
}
.pie-carrito > div {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 1rem;
}
.total,
.definitivo {
  display: block;
  font-size: 2rem;
}
.cobrar {
  width: 100%;
  margin-top: 1rem;
  padding: 1rem;
}
.resultado {
  margin-top: 1rem;
  padding: 1.25rem;
  border-radius: var(--mp-radio);
}
.icono {
  font-size: 2rem;
  display: block;
}
.proceso {
  border: 2px dashed var(--mp-ambar);
  background: rgb(183 121 31 / 8%);
}
.confirmada {
  color: white;
  background: #17795a;
}
.confirmada button {
  color: white;
  border-color: white;
}
.confirmada button:hover {
  color: var(--mp-texto);
}
.cancelada {
  border-left: 5px solid var(--mp-rojo);
  background: rgb(192 57 43 / 8%);
}
.bandeja {
  margin-top: 1.5rem;
}
.operacion {
  padding: 1rem 0;
  border-top: 1px solid var(--mp-borde);
}
@media (max-width: 760px) {
  .columnas {
    grid-template-columns: 1fr;
  }
  .cabecera {
    align-items: start;
    flex-direction: column;
  }
  .panel {
    padding: 1rem;
  }
}
</style>

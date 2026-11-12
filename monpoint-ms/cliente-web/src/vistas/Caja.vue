<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { gateway } from '@/api/gateway'
import TicketVenta from '@/componentes/TicketVenta.vue'
import { useCaja } from '@/stores/caja'
import { crearCobro, editable, cantidadValida, precioValido, dinero, estimado } from '@/caja/cobro'
import { filtrarPor } from '@/comun/filtro'
import { useRoute } from 'vue-router'
const ruta = useRoute()
const s = useCaja().cobro
const cobro = crearCobro(s, gateway)
const productos = ref([]),
  clientes = ref([]),
  busqueda = ref('')
const cargando = ref(false),
  errorCatalogo = ref(''),
  errorClientes = ref('')
const campoBusqueda = ref(null)
let carga,
  desmontado = false
const puedeEditar = computed(() => editable(s))
const validas = computed(() =>
  s.lineas.every((l) => cantidadValida(l.cantidad) && precioValido(l.precio)),
)
const total = computed(() => (validas.value ? dinero(estimado(s.lineas)) : '—'))
// Se filtra sin acentos y tambien por descripcion. El prototipo ofrece buscar
// por "codigo", pero `ProductoResponse` no tiene codigo ni SKU: los campos de
// texto del catalogo son `nombre` y `descripcion`, asi que la caja busca en
// esos dos y el marcador los nombra. Inventar un codigo en el cliente seria
// prometer un dato que el servidor no guarda.
const visibles = computed(() =>
  filtrarPor(
    productos.value.filter((p) => p.activo !== false),
    busqueda.value,
    ['nombre', 'descripcion'],
  ),
)
const rechazado = computed(() => (s.estado === 'cancelada' ? s.venta?.productoIdRechazado : null))

/**
 * Estado de existencia de un producto, para la insignia de la tarjeta.
 *
 * `umbralStock` es el mismo campo con el que ms-inventario decide emitir
 * StockBajo, asi que la caja y las notificaciones avisan por el mismo
 * criterio y no por dos umbrales distintos.
 *
 * Ninguno de los tres estados deshabilita la tarjeta: la existencia que se ve
 * aqui es una lectura anterior al cobro, y quien decide si alcanza es el
 * servidor al reservar. Bloquear en el cliente daria una negativa que el
 * servidor no ha dado.
 */
function existencia (p) {
  if (!(p.existencia > 0)) return { clase: 'mp-insignia--error', glifo: '✕', texto: 'Sin existencia' }
  if (p.existencia <= p.umbralStock)
    return { clase: 'mp-insignia--aviso', glifo: '▲', texto: `Existencia ${p.existencia}` }
  return { clase: 'mp-insignia--apagada', glifo: '●', texto: `Existencia ${p.existencia}` }
}

// Los tres pasos del prototipo sobre los seis estados de la maquina de cobro.
// `cancelada` cae en el paso 3 y no en el 1 aunque el carrito vuelva a ser
// editable: para quien mira la pantalla, una venta cancelada es un resultado,
// y el paso 1 ya quedo recorrido.
const PASOS = ['Carrito', 'En proceso', 'Resultado']
const pasoActual = computed(() => {
  if (['enviando', 'enProceso'].includes(s.estado)) return 2
  if (['confirmada', 'cancelada'].includes(s.estado)) return 3
  return 1
})

const puedeCobrar = computed(() => puedeEditar.value && s.lineas.length > 0 && validas.value)

function agregarPrimero () {
  if (cargando.value || errorCatalogo.value) return
  const primero = visibles.value[0]
  if (primero) agregar(primero)
}

function agregarConEnter (e) {
  if (e.key !== 'Enter' || e.ctrlKey || e.metaKey || e.altKey || e.shiftKey || e.isComposing || e.repeat || e.defaultPrevented) return
  e.preventDefault()
  agregarPrimero()
}

// F12 puede estar reservado por el navegador o sistema. Se ofrece tambien
// Ctrl+Enter/Meta+Enter, sin prometer control de las herramientas del navegador.
function atajos (e) {
  if (ruta.name !== 'caja' || e.defaultPrevented || e.isComposing || e.repeat || document.querySelector('dialog[open], .desplegable')) return
  const sinModificadores = !e.ctrlKey && !e.metaKey && !e.altKey && !e.shiftKey
  if (e.key === 'F2' && sinModificadores) {
    e.preventDefault()
    campoBusqueda.value?.focus()
    campoBusqueda.value?.select()
    return
  }
  const cobrarConTeclado = (e.key === 'F12' && sinModificadores) ||
    (e.key === 'Enter' && (e.ctrlKey || e.metaKey) && !e.altKey && !e.shiftKey)
  if (cobrarConTeclado && puedeCobrar.value) {
    e.preventDefault()
    void cobro.cobrar()
  }
}
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
  window.addEventListener('keydown', atajos)
})
onUnmounted(() => {
  desmontado = true
  carga?.abort()
  cobro.detener()
  window.removeEventListener('beforeunload', prevenirSalida)
  window.removeEventListener('keydown', atajos)
})
</script>

<template>
  <section class="caja">
    <div class="columnas">
      <section class="panel catalogo" aria-labelledby="titulo-catalogo">
        <h2 id="titulo-catalogo" class="mp-solo-lectores">Catálogo de productos</h2>
        <div class="cabecera-catalogo">
          <label class="mp-etiqueta" for="buscar">Buscar producto</label>
          <button class="actualizar" :disabled="cargando" @click="cargar">{{ cargando ? 'Actualizando…' : 'Actualizar catálogo' }}</button>
        </div>
        <div class="busqueda-fila">
          <div class="busqueda">
            <input
              id="buscar"
              ref="campoBusqueda"
              v-model="busqueda"
              class="mp-campo campo-busqueda"
              type="search"
              placeholder="Nombre o descripción"
              aria-describedby="pista-busqueda"
              aria-keyshortcuts="F2"
              @keydown="agregarConEnter"
            />
            <!-- El chip no es interactivo: rotula el atajo que escucha la vista.
                 Se marca `aria-hidden` porque la pista de abajo ya lo nombra en
                 una frase, y leerlo dos veces solo estorba. -->
            <span class="tecla" aria-hidden="true">F2</span>
          </div>
          <p id="pista-busqueda" class="pista">
            F2 enfoca la búsqueda. Enter agrega el primer resultado al carrito.
          </p>
        </div>
        <p v-if="errorCatalogo" role="alert" class="mp-aviso mp-aviso--error">
          {{ errorCatalogo }}
        </p>
        <p v-else-if="cargando" role="status">Cargando productos…</p>
        <p v-else-if="!visibles.length" class="vacio">No hay productos para esta búsqueda.</p>
        <ul v-else class="rejilla">
          <li v-for="p in visibles" :key="p.id">
            <button
              class="tarjeta"
              :aria-label="`Agregar ${p.nombre}, ${dinero(p.precio)}, ${existencia(p).texto}`"
              :disabled="!puedeEditar || !precioValido(p.precio)"
              @click="agregar(p)"
            >
              <span class="nombre">{{ p.nombre }}</span>
              <span class="precio">{{ dinero(p.precio) }}</span>
              <span class="mp-insignia existencia" :class="existencia(p).clase">
                <span class="glifo" aria-hidden="true">{{ existencia(p).glifo }}</span>
                {{ existencia(p).texto }}
              </span>
            </button>
          </li>
        </ul>
        <p class="pista">La existencia puede cambiar. Se confirma al procesar el cobro.</p>
      </section>
      <section class="panel panel-cobro" aria-labelledby="titulo-carrito">
        <!-- Lista ordenada y no una fila de `div`: son etapas numeradas de un
             mismo proceso, y asi un lector de pantalla las anuncia con su
             posicion y su total sin que haya que repetirlo en el texto. -->
        <ol class="pasos" role="list" aria-label="Estado del cobro">
          <li
            v-for="(nombre, i) in PASOS"
            :key="nombre"
            :class="{ alcanzado: i + 1 <= pasoActual }"
            :aria-current="i + 1 === pasoActual ? 'step' : undefined"
          >
            <span class="numero" aria-hidden="true">{{ i + 1 }}</span>{{ nombre }}
            <span v-if="i < PASOS.length - 1" class="separador" aria-hidden="true">→</span>
          </li>
        </ol>
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
        <div v-if="!s.lineas.length && s.estado !== 'confirmada'" class="carrito-vacio">
          <span aria-hidden="true">▤</span><h3>Carrito vacío</h3>
          <p>Busca un producto o toca una tarjeta del catálogo para agregar la primera línea.</p>
        </div>
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
          <TicketVenta :venta="s.venta" />
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
          <button class="mp-boton cobrar" :disabled="!puedeCobrar" aria-keyshortcuts="Control+Enter Meta+Enter F12" @click="cobro.cobrar">
            {{
              s.estado === 'enviando'
                ? 'Enviando…'
                : s.estado === 'enProceso'
                  ? 'Esperando resultado…'
                  : 'Cobrar'
            }}
            <span v-if="puedeCobrar" class="tecla tecla--clara" aria-hidden="true">F12</span>
          </button>
          <p class="pista">Ctrl+Enter o ⌘+Enter también cobran.</p>
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
          ><p>✓ Venta confirmada · Total cobrado: {{ dinero(p.detalle.total) }}</p>
          <TicketVenta :venta="p.detalle" /></template
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
/* Caja ocupa el ancho interior de la concha; las demas rutas conservan su
   relleno. A 1440px: 232 lateral + 816 catalogo + 392 cobro. */
.columnas {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 392px;
  align-items: start;
}
.panel { min-width: 0; padding: 20px; }
.catalogo { background: var(--mp-fondo); container-type: inline-size; container-name: catalogo; }
.panel-cobro {
  position: sticky;
  top: 61px;
  min-height: calc(100dvh - 61px);
  display: flex;
  flex-direction: column;
  border-left: 1px solid var(--mp-borde-suave);
  background: var(--mp-superficie);
}
.cabecera-catalogo { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; }
.cabecera-catalogo .mp-etiqueta { font-weight: 500; line-height: 1.3; }
.busqueda-fila { display: grid; grid-template-columns: minmax(0, 530px) minmax(140px, 1fr); gap: 16px; align-items: center; }
.busqueda-fila .pista { margin: 0; }
.actualizar { padding: 0; border: 0; background: transparent; color: var(--mp-texto-suave); font-size: 12px; text-decoration: underline; cursor: pointer; }
.actualizar:disabled { opacity: .55; cursor: wait; }
.rejilla, .lineas, .detalle { list-style: none; padding: 0; margin: 1rem 0; }
.rejilla {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(185px, 100%), 1fr));
  gap: 12px;
}
.carrito-vacio { margin: 16px 0; padding: 32px 20px; text-align: center; border: 1px dashed var(--mp-borde); border-radius: var(--mp-radio-tarjeta); }
.carrito-vacio > span { display: block; margin-bottom: 8px; color: var(--mp-texto-auxiliar); }
.carrito-vacio h3 { font-size: 16px; }
.carrito-vacio p { margin: 0; color: var(--mp-texto-suave); }
.tarjeta {
  display: flex;
  flex-direction: column;
  align-items: start;
  gap: 6px;
  width: 100%;
  min-width: 0;
  overflow-wrap: anywhere;
  height: 100%;
  padding: 14px;
  border: 1px solid var(--mp-borde-suave);
  border-radius: var(--mp-radio-tarjeta);
  background: var(--mp-superficie);
  color: var(--mp-texto);
  text-align: left;
  cursor: pointer;
}
.tarjeta:hover:not(:disabled) {
  border-color: var(--mp-verde);
  background: var(--mp-verde-suave);
}
.tarjeta:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.nombre {
  font-size: 0.875rem;
  font-weight: 500;
  line-height: 1.35;
}

/* `margin-top: auto` empuja la insignia al borde inferior: en una fila de la
   rejilla las tarjetas se estiran a la mas alta, y sin esto las insignias
   quedarian a distintas alturas segun lo largo del nombre. */
.existencia {
  margin-top: auto;
}
.glifo {
  font-family: var(--mp-tipografia-cifras);
}
/* El prototipo pone el dinero en la familia del texto y reserva la
   monoespaciada para los identificadores y los rotulos de tecla. Se conserva
   `tabular-nums`, que es lo que de verdad aporta la mono en una columna de
   importes: cifras del mismo ancho para que los puntos decimales se alineen.
   Asi se obtiene la alineacion sin cambiar de familia. */
.precio,
.mp-cifra {
  font-family: var(--mp-tipografia);
  font-variant-numeric: tabular-nums;
}
.precio {
  font-size: 1.25rem;
  font-weight: 600;
}

/* Chip que rotula una tecla. Monoespaciado para que se lea como una tecla y no
   como una palabra del texto. */
.tecla {
  padding: 2px 6px;
  border: 1px solid var(--mp-borde);
  border-radius: 5px;
  background: var(--mp-superficie-2);
  color: var(--mp-texto-auxiliar);
  font-family: var(--mp-tipografia-cifras);
  font-size: 0.6875rem;
  font-weight: 500;
  line-height: 1.5;
}
.tecla--clara {
  border-color: rgb(255 255 255 / 45%);
  background: rgb(255 255 255 / 18%);
  color: #fff;
}

.busqueda {
  position: relative;
}
/* El relleno derecho reserva el sitio del chip para que el texto escrito no
   pase por debajo de el. */
.campo-busqueda {
  padding-right: 3.5rem;
}
.busqueda .tecla {
  position: absolute;
  top: 50%;
  right: 0.6rem;
  transform: translateY(-50%);
  pointer-events: none;
}
.pista {
  margin: 0.4rem 0 0;
  color: var(--mp-texto-auxiliar);
  font-size: 0.75rem;
  line-height: 1.5;
}

.pasos {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  margin: 0 0 1rem;
  padding: 0;
  list-style: none;
  font-size: 0.875rem;
  color: var(--mp-texto);
}
.pasos li {
  display: flex;
  gap: 6px;
  align-items: center;
}
.separador {
  margin-left: 6px;
  color: var(--mp-texto-auxiliar);
}
.numero {
  display: grid;
  place-items: center;
  width: 20px;
  height: 20px;
  border: 1px solid var(--mp-borde);
  border-radius: 50%;
  background: var(--mp-superficie-2);
  color: var(--mp-texto-auxiliar);
  font-size: 0.6875rem;
  font-weight: 700;
}
.pasos .alcanzado .numero {
  border-color: var(--mp-verde);
  background: var(--mp-verde);
  color: #fff;
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
  background: var(--mp-error-fondo);
}
.marca-rechazo {
  font-size: 0.875rem;
  font-weight: 600;
}
.pie-carrito {
  margin-top: auto;
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
  border-radius: var(--mp-radio-tarjeta);
}
.icono {
  font-size: 2rem;
  display: block;
}
.proceso {
  border: 1px solid var(--mp-proceso-borde);
  color: var(--mp-proceso);
  background: var(--mp-proceso-fondo);
}
.confirmada {
  background: var(--mp-exito-fondo);
  color: var(--mp-exito);
  border: 1px solid var(--mp-exito-borde);
}
.confirmada button {
  color: var(--mp-exito);
  border-color: var(--mp-exito-borde);
}
.confirmada button:hover {
  color: var(--mp-texto);
}
.cancelada {
  color: var(--mp-error);
  border: 1px solid var(--mp-error-borde);
  border-left-width: 5px;
  background: var(--mp-error-fondo);
}
.bandeja {
  margin-top: 1.5rem;
}
.operacion {
  padding: 1rem 0;
  border-top: 1px solid var(--mp-borde);
}
/* Se decide por ancho del contenido, tambien con la lateral colapsada.
   Dos tarjetas de 185 + hueco 12 + padding 40 + cobro 392 requieren 814px. */
@container area-caja (max-width: 813px) {
  .columnas { grid-template-columns: 1fr; }
  .panel-cobro { position: static; min-height: auto; border-left: 0; border-top: 1px solid var(--mp-borde-suave); }
}
@container catalogo (max-width: 680px) {
  .busqueda-fila { grid-template-columns: 1fr; gap: 4px; }
}
@media (max-width: 720px) {
  .panel { padding: 16px; }
  .rejilla { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>

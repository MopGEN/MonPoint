import { createRouter, createWebHistory } from 'vue-router'
import { useSesion } from '@/stores/sesion'

/**
 * Enrutador con guarda de sesion y de rol.
 *
 * La guarda es comodidad de interfaz, NO seguridad. Quien edite el estado de
 * Pinia desde las herramientas del navegador puede ver cualquier pantalla. Lo
 * que impide leer o escribir datos es que el Gateway valide el token y el rol
 * en cada peticion; la guarda solo evita mostrar pantallas que fallarian.
 *
 * Toda ruta es privada salvo que declare `meta.publica`. Asi, una vista nueva
 * nace protegida: olvidarse de la meta deja la pantalla cerrada, no abierta.
 */
/**
 * Ruta a la que se manda a quien entra autenticado o pide algo que su rol no
 * alcanza. Debe ser una ruta accesible por TODOS los roles del sistema.
 */
const RUTA_REFUGIO = 'caja'

const rutas = [
  {
    path: '/',
    redirect: '/caja',
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('@/vistas/IniciarSesion.vue'),
    meta: { publica: true, titulo: 'Iniciar sesion' },
  },
  {
    path: '/caja',
    name: 'caja',
    component: () => import('@/vistas/Caja.vue'),
    meta: { titulo: 'Caja', roles: ['ADMIN', 'VENDEDOR'] },
  },
  // Pantalla de reportes: se agrega cuando ms-reportes exponga la proyeccion
  // (tareas 4.2 y 4.6). Quedara con meta.roles = ['ADMIN'].
  {
    path: '/:ruta(.*)*',
    name: 'no-encontrada',
    component: () => import('@/vistas/NoEncontrada.vue'),
    meta: { publica: true, titulo: 'Pagina no encontrada' },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes: rutas,
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach((destino) => {
  const sesion = useSesion()

  // Un token vencido segun el reloj local se descarta antes de navegar. Esto
  // ahorra una peticion condenada al 401, no sustituye la validacion del
  // Gateway.
  if (sesion.autenticado && sesion.caducado) sesion.cerrar()

  const publica = destino.meta.publica === true

  if (!publica && !sesion.autenticado) {
    return {
      name: 'login',
      // Se guarda el destino para volver ahi tras iniciar sesion. Se usa
      // `query` y no el estado del almacen porque el almacen se vacia al
      // cerrar sesion, que es justo el momento en que hace falta recordarlo.
      query: destino.fullPath === '/' ? {} : { regresarA: destino.fullPath },
    }
  }

  // Ya autenticado: la pantalla de inicio de sesion no tiene sentido.
  if (publica && destino.name === 'login' && sesion.autenticado) {
    return { name: RUTA_REFUGIO }
  }

  const rolesPermitidos = destino.meta.roles
  if (Array.isArray(rolesPermitidos) && !rolesPermitidos.includes(sesion.rol)) {
    // Redirigir siempre al refugio provocaria un bucle infinito si el refugio
    // fuera justo la ruta denegada (rol desconocido o fuera del catalogo). En
    // ese caso la sesion no es utilizable: se cierra y se manda a iniciar
    // sesion, que es publica y por tanto siempre alcanzable.
    if (destino.name === RUTA_REFUGIO) {
      sesion.cerrar()
      return { name: 'login' }
    }
    return { name: RUTA_REFUGIO }
  }

  return true
})

router.afterEach((destino) => {
  const t = destino.meta.titulo
  document.title = t ? `MonPoint · ${t}` : 'MonPoint'
})

export default router

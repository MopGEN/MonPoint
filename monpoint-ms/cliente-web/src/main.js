import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from '@/App.vue'
import router from '@/router'
import { useSesion } from '@/stores/sesion'
import { configurarGateway } from '@/api/gateway'
import '@/assets/base.css'
import licenciaInter from '@/assets/fuentes/OFL.txt?url'
import licenciaMono from '@/assets/fuentes/IBM-Plex-Mono-OFL.txt?url'

// Las licencias acompanan a los WOFF2 tambien en dist/nginx, no solo en Git.
for (const [nombre, href] of [['Inter', licenciaInter], ['IBM Plex Mono', licenciaMono]]) {
  const enlace = document.createElement('link')
  enlace.rel = 'license'
  enlace.href = href
  enlace.title = `SIL Open Font License: ${nombre}`
  document.head.append(enlace)
}

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)

// El almacen se obtiene pasandole la instancia de Pinia de forma explicita: en
// este punto todavia no hay componente activo, asi que `useSesion()` sin
// argumento no tendria de donde resolverla.
const sesion = useSesion(pinia)

// Aqui se cierra el triangulo sin crear un ciclo de importaciones: el cliente
// HTTP no conoce ni al almacen ni al enrutador, solo estas dos funciones.
configurarGateway({
  obtenerToken: () => sesion.token,
  alExpirarSesion: () => {
    sesion.cerrar()
    // `replace` y no `push`: la pantalla desde la que caduco la sesion no debe
    // quedar en el historial, porque el boton Atras llevaria a una vista sin
    // datos que volveria a disparar un 401.
    router.replace({ name: 'login', query: { expirada: '1' } })
  },
})

app.use(router)
app.mount('#app')

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// Decision deliberada: NO se configura un proxy de desarrollo (server.proxy).
//
// Un proxy haria que en desarrollo el navegador pidiera a localhost:5173 y Vite
// reenviara al Gateway, de modo que el navegador nunca veria una peticion de
// origen cruzado. Eso enmascara CORS: la aplicacion funcionaria en `npm run dev`
// y fallaria al desplegarla, porque en produccion el navegador si pide
// directamente al Gateway. Al no haber proxy, el CORS del Gateway
// (monpoint.cors.origenes) se ejercita desde el primer dia.
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    // strictPort evita que Vite se mueva en silencio a 5174 cuando 5173 esta
    // ocupado: ese otro puerto no esta en monpoint.cors.origenes del Gateway y
    // el sintoma seria un fallo de CORS sin causa aparente.
    strictPort: true,
  },
})
